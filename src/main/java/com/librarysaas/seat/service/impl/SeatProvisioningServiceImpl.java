package com.librarysaas.seat.service.impl;

import com.librarysaas.attendance.repository.AttendanceRepository;
import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.seat.dto.SeatProvisioningResult;
import com.librarysaas.seat.entity.Seat;
import com.librarysaas.seat.repository.SeatAssignmentRepository;
import com.librarysaas.seat.repository.SeatRepository;
import com.librarysaas.seat.service.SeatProvisioningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a configured seat count into seat rows.
 *
 * <p><b>Why the stored count is the source of truth.</b> seat_number is VARCHAR(50), so
 * the largest existing number cannot be read off the table: MAX() would order
 * "9" above "100" and the seat after 100 would come out numbered 10. The stored
 * count is therefore what numbering continues from, and the seat rows follow
 * it rather than defining it.
 *
 * <p><b>Uniqueness</b> is guaranteed by uk_seat_library_number (library_id,
 * seat_number) from V1. This class never leans on that constraint to catch a
 * mistake: it reads the block of numbers it is about to touch and skips what
 * already exists, so the constraint stays a backstop rather than control flow.
 *
 * <p><b>Tenancy.</b> Every query is scoped by the library id taken from the
 * Library the caller hands over. There is no branch that widens a query for a
 * super admin - a platform administrator changing a seat count still operates on
 * that one library id.
 */
@Service
public class SeatProvisioningServiceImpl implements SeatProvisioningService {

    /** Statuses shared with SeatServiceImpl, which owns the seat lifecycle. */
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_OCCUPIED = "OCCUPIED";
    private static final String STATUS_INACTIVE = "INACTIVE";

    /** Naming every blocking seat would stop the message being readable. */
    private static final int MAX_REPORTED_SEATS = 10;

    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final AttendanceRepository attendanceRepository;

    @Autowired
    public SeatProvisioningServiceImpl(SeatRepository seatRepository,
                                       SeatAssignmentRepository seatAssignmentRepository,
                                       AttendanceRepository attendanceRepository) {
        this.seatRepository = seatRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public SeatProvisioningResult applySeatCount(Library library, int newSeatCount, Long actorUserId) {
        requireValidSeatCount(newSeatCount);

        int currentSeatCount = library.getSeatCount() == null ? 0 : library.getSeatCount();

        if (newSeatCount > currentSeatCount) {
            return increase(library, currentSeatCount, newSeatCount, actorUserId);
        }
        if (newSeatCount < currentSeatCount) {
            return decrease(library, currentSeatCount, newSeatCount, actorUserId);
        }

        // Setting the seat count a library already has is not an error, but it must
        // not produce a second set of seats.
        return SeatProvisioningResult.unchanged(currentSeatCount);
    }

    /* ---------------------------------------------------------------- increase */

    /**
     * Creates only the numbers between the old and the new seat count.
     *
     * Seats at or below the current seat count are never read, written or
     * validated here, which is what keeps an increase from disturbing a seat
     * that is occupied or out of service.
     */
    private SeatProvisioningResult increase(Library library, int currentSeatCount,
                                            int newSeatCount, Long actorUserId) {
        int from = currentSeatCount + 1;
        Map<String, Seat> existing = seatsByNumber(library, from, newSeatCount);

        LocalDateTime now = LocalDateTime.now();
        List<Seat> toSave = new ArrayList<>(newSeatCount - currentSeatCount);
        int created = 0;
        int reactivated = 0;

        for (int number = from; number <= newSeatCount; number++) {
            Seat seat = existing.get(String.valueOf(number));

            if (seat == null) {
                toSave.add(newSeat(library, number, now, actorUserId));
                created++;
                continue;
            }

            // The seat is already there, which happens when a previous reduction
            // took it out of service. It is brought back rather than duplicated,
            // so it keeps its id and its assignment and attendance history stay
            // attached. A seat in any other status is left as the library set it.
            if (STATUS_INACTIVE.equals(seat.getStatus())) {
                seat.setStatus(STATUS_AVAILABLE);
                seat.setUpdatedAt(now);
                seat.setUpdatedBy(actorUserId);
                toSave.add(seat);
                reactivated++;
            }
        }

        seatRepository.saveAll(toSave);
        library.setSeatCount(newSeatCount);

        return SeatProvisioningResult.increased(newSeatCount, created, reactivated, from, newSeatCount);
    }

    /* ---------------------------------------------------------------- decrease */

    /**
     * Withdraws the seats above the new seat count, choosing per seat between
     * deleting the row and retiring it.
     *
     * <p>Three outcomes, decided by what actually references the seat:
     *
     * <ol>
     *   <li><b>In use</b> - allocated to a student right now. The whole reduction
     *       is refused, naming the seat. This is the case the requirement is
     *       written around: seat 85 is occupied, so the count cannot go to 80.</li>
     *   <li><b>Has history</b> - a released allocation or an attendance record
     *       points at it. The row is kept and marked INACTIVE, because
     *       seat_assignment and attendance both hold a foreign key to seat and
     *       deleting it would either fail outright or strand that history.</li>
     *   <li><b>Never referenced</b> - nothing has ever pointed at it, so the row
     *       is deleted. Removing it loses nothing, and it keeps a library that
     *       was simply over-provisioned from accumulating dead INACTIVE rows.</li>
     * </ol>
     *
     * <p>The in-use check runs across the whole block before anything is touched,
     * so a refused reduction leaves every seat exactly as it was rather than
     * withdrawing the ones it got to first.
     */
    private SeatProvisioningResult decrease(Library library, int currentSeatCount,
                                            int newSeatCount, Long actorUserId) {
        int from = newSeatCount + 1;
        Map<String, Seat> existing = seatsByNumber(library, from, currentSeatCount);

        List<Seat> surplus = new ArrayList<>();
        for (int number = from; number <= currentSeatCount; number++) {
            Seat seat = existing.get(String.valueOf(number));
            if (seat != null) {
                surplus.add(seat);
            }
        }

        requireNoneInUse(surplus, newSeatCount);

        // One query each, for the whole block, rather than two per seat.
        Set<Long> referenced = referencedSeatIds(surplus);

        LocalDateTime now = LocalDateTime.now();
        List<Seat> toRetire = new ArrayList<>();
        List<Seat> toRemove = new ArrayList<>();

        for (Seat seat : surplus) {
            if (referenced.contains(seat.getSeatId())) {
                // Already retired by an earlier reduction: leave it alone rather
                // than counting it again.
                if (STATUS_INACTIVE.equals(seat.getStatus())) {
                    continue;
                }
                seat.setStatus(STATUS_INACTIVE);
                seat.setUpdatedAt(now);
                seat.setUpdatedBy(actorUserId);
                toRetire.add(seat);
            } else {
                toRemove.add(seat);
            }
        }

        seatRepository.saveAll(toRetire);
        seatRepository.deleteAll(toRemove);
        library.setSeatCount(newSeatCount);

        return SeatProvisioningResult.decreased(newSeatCount, toRemove.size(), toRetire.size(),
                from, currentSeatCount);
    }

    /**
     * Refuses the reduction outright if any seat that would go is allocated to a
     * student right now. The message names the seat so the owner can act on it
     * rather than guess which one is in the way.
     */
    private void requireNoneInUse(List<Seat> surplus, int newSeatCount) {
        if (surplus.isEmpty()) {
            return;
        }

        List<Long> seatIds = surplus.stream().map(Seat::getSeatId).toList();
        Set<Long> allocated = new HashSet<>(seatAssignmentRepository.findActiveSeatIdsIn(seatIds));

        // A seat marked OCCUPIED without a live assignment row would be a stale
        // status, but it still says the library considers it in use, so it counts.
        for (Seat seat : surplus) {
            if (STATUS_OCCUPIED.equals(seat.getStatus())) {
                allocated.add(seat.getSeatId());
            }
        }

        if (!allocated.isEmpty()) {
            throw new ConflictException(
                    "Seat count cannot be reduced to " + newSeatCount + " because "
                            + describe(surplus, allocated, "seat", "seats")
                            + (allocated.size() == 1 ? " is" : " are") + " currently in use.",
                    "SEAT_COUNT_REDUCTION_BLOCKED");
        }
    }

    /**
     * Seats that anything still points at - a past allocation or an attendance
     * record. These are retired rather than deleted, so the history they carry
     * keeps resolving to a real seat.
     */
    private Set<Long> referencedSeatIds(List<Seat> surplus) {
        if (surplus.isEmpty()) {
            return Set.of();
        }
        List<Long> seatIds = surplus.stream().map(Seat::getSeatId).toList();

        Set<Long> referenced = new HashSet<>(seatAssignmentRepository.findSeatIdsWithAssignments(seatIds));
        referenced.addAll(attendanceRepository.findSeatIdsWithAttendance(seatIds));
        return referenced;
    }

    /* --------------------------------------------------------------- internals */

    /**
     * Reads the block of seat numbers a seat-count change is about to touch.
     *
     * Scoped to the library, so a seat number that exists in another tenant is
     * invisible here and cannot influence what is created.
     */
    private Map<String, Seat> seatsByNumber(Library library, int from, int to) {
        List<String> numbers = new ArrayList<>(to - from + 1);
        for (int number = from; number <= to; number++) {
            numbers.add(String.valueOf(number));
        }

        Map<String, Seat> byNumber = new HashMap<>();
        for (Seat seat : seatRepository.findByLibraryLibraryIdAndSeatNumberIn(
                library.getLibraryId(), numbers)) {
            byNumber.put(seat.getSeatNumber(), seat);
        }
        return byNumber;
    }

    /**
     * Generated seats are AVAILABLE, the status V1 already makes the column
     * default. No new status was introduced: a seat created by a seat-count change
     * is an ordinary seat in every respect.
     */
    private Seat newSeat(Library library, int number, LocalDateTime now, Long actorUserId) {
        Seat seat = new Seat();
        seat.setLibrary(library);
        seat.setSeatNumber(String.valueOf(number));
        seat.setStatus(STATUS_AVAILABLE);
        seat.setCreatedAt(now);
        seat.setUpdatedAt(now);
        seat.setCreatedBy(actorUserId);
        seat.setUpdatedBy(actorUserId);
        seat.setVersion(0L);
        return seat;
    }

    private void requireValidSeatCount(int seatCount) {
        if (seatCount < MIN_SEAT_COUNT) {
            throw new BusinessException(
                    "Seat count must be at least " + MIN_SEAT_COUNT,
                    "INVALID_SEAT_COUNT");
        }
        if (seatCount > MAX_SEAT_COUNT) {
            throw new BusinessException(
                    "Seat count must not exceed " + MAX_SEAT_COUNT,
                    "INVALID_SEAT_COUNT");
        }
    }

    /**
     * "seat 85" / "seats 85, 86", so the refusal reads as a sentence. Long lists
     * are truncated - naming ninety blocking seats stops the message being read
     * at all.
     */
    private String describe(List<Seat> candidates, Set<Long> blockedIds,
                            String singular, String plural) {
        List<String> numbers = candidates.stream()
                .filter(seat -> blockedIds.contains(seat.getSeatId()))
                .map(Seat::getSeatNumber)
                .toList();

        if (numbers.size() == 1) {
            return singular + " " + numbers.get(0);
        }
        if (numbers.size() <= MAX_REPORTED_SEATS) {
            return plural + " " + String.join(", ", numbers);
        }
        return plural + " " + String.join(", ", numbers.subList(0, MAX_REPORTED_SEATS))
                + " and " + (numbers.size() - MAX_REPORTED_SEATS) + " more";
    }
}
