package com.librarysaas.seat.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.seat.dto.*;
import com.librarysaas.seat.entity.Seat;
import com.librarysaas.seat.entity.SeatAssignment;
import com.librarysaas.seat.entity.SeatType;
import com.librarysaas.seat.entity.SeatZone;
import com.librarysaas.seat.repository.SeatAssignmentRepository;
import com.librarysaas.seat.repository.SeatRepository;
import com.librarysaas.seat.repository.SeatTypeRepository;
import com.librarysaas.seat.repository.SeatZoneRepository;
import com.librarysaas.seat.service.SeatService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seat inventory and allocation.
 *
 * Allocation exclusivity is enforced here because V1__initial_schema.sql
 * deliberately carries no unique constraint on active assignments and states
 * that the service layer must allow only one active assignment per student and
 * one active student per seat. Both checks run inside the same transaction as
 * the write.
 *
 * Authorisation reuses the seat permissions the schema already defines -
 * SEAT_VIEW, SEAT_CREATE, SEAT_UPDATE, SEAT_ASSIGN. There is no SEAT_DELETE, so
 * taking a seat out of service is an update, not a delete.
 */
@Service
public class SeatServiceImpl implements SeatService {

    /** Seat statuses. AVAILABLE, OCCUPIED and MAINTENANCE are all seeded by V1. */
    static final String STATUS_AVAILABLE = "AVAILABLE";
    static final String STATUS_OCCUPIED = "OCCUPIED";
    static final String STATUS_MAINTENANCE = "MAINTENANCE";
    static final String STATUS_INACTIVE = "INACTIVE";

    private static final Set<String> SEAT_STATUSES =
            Set.of(STATUS_AVAILABLE, STATUS_OCCUPIED, STATUS_MAINTENANCE, STATUS_INACTIVE);

    /**
     * Statuses a caller may set directly. OCCUPIED is excluded because it is a
     * consequence of an allocation, not something to be typed in - allowing it
     * would let the seat status drift away from seat_assignment.
     */
    private static final Set<String> SETTABLE_STATUSES =
            Set.of(STATUS_AVAILABLE, STATUS_MAINTENANCE, STATUS_INACTIVE);

    /** Assignment statuses. ACTIVE is the schema default. */
    static final String ASSIGNMENT_ACTIVE = "ACTIVE";
    static final String ASSIGNMENT_RELEASED = "RELEASED";

    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final SeatZoneRepository seatZoneRepository;
    private final LibraryRepository libraryRepository;
    private final StudentRepository studentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public SeatServiceImpl(SeatRepository seatRepository,
                           SeatAssignmentRepository seatAssignmentRepository,
                           SeatTypeRepository seatTypeRepository,
                           SeatZoneRepository seatZoneRepository,
                           LibraryRepository libraryRepository,
                           StudentRepository studentRepository,
                           TenantAuthorizationService tenantAuthorizationService) {
        this.seatRepository = seatRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.seatZoneRepository = seatZoneRepository;
        this.libraryRepository = libraryRepository;
        this.studentRepository = studentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /* ------------------------------------------------------------- inventory */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SEAT_VIEW')")
    public List<SeatResponse> getSeats(Long libraryId, String status, Long zoneId,
                                       Long seatTypeId, String search) {
        requireLibrary(libraryId);

        String normalisedStatus = status == null || status.isBlank()
                ? null
                : normaliseStatus(status, SEAT_STATUSES);
        String normalisedSearch = search == null || search.isBlank() ? null : search.trim();

        List<Seat> seats =
                seatRepository.search(libraryId, normalisedStatus, zoneId, seatTypeId, normalisedSearch);

        // One query for every active allocation in the library, so rendering a
        // seat grid does not fan out into a query per seat.
        Map<Long, SeatAssignment> activeBySeatId = new HashMap<>();
        for (SeatAssignment assignment : seatAssignmentRepository.findActiveByLibraryId(libraryId)) {
            activeBySeatId.put(assignment.getSeat().getSeatId(), assignment);
        }

        List<SeatResponse> responses = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            responses.add(SeatResponse.from(seat, activeBySeatId.get(seat.getSeatId())));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SEAT_VIEW')")
    public SeatResponse getSeat(Long libraryId, Long seatId) {
        requireLibrary(libraryId);
        Seat seat = requireSeat(libraryId, seatId);
        return SeatResponse.from(seat, activeAssignmentOrNull(seatId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SEAT_CREATE')")
    public SeatResponse createSeat(Long libraryId, SeatRequest request) {
        Library library = requireLibrary(libraryId);

        String seatNumber = request.getSeatNumber().trim();
        if (seatRepository.existsByLibraryLibraryIdAndSeatNumber(libraryId, seatNumber)) {
            throw new ConflictException(
                    "Seat " + seatNumber + " already exists in this library",
                    "SEAT_NUMBER_ALREADY_EXISTS");
        }

        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? STATUS_AVAILABLE
                : normaliseSettableStatus(request.getStatus());

        Seat seat = new Seat();
        seat.setLibrary(library);
        seat.setSeatNumber(seatNumber);
        seat.setStatus(status);
        seat.setZone(resolveZone(libraryId, request.getZoneId()));
        seat.setSeatType(resolveSeatType(libraryId, request.getSeatTypeId()));
        seat.setCreatedAt(LocalDateTime.now());
        seat.setUpdatedAt(LocalDateTime.now());
        seat.setCreatedBy(currentUserId());
        seat.setUpdatedBy(currentUserId());
        seat.setVersion(0L);

        return SeatResponse.from(seatRepository.save(seat), null);
    }

    /**
     * A seat number is assigned when the seat is generated and is fixed for the
     * life of the seat. It is the physical sheet the student is sent to, and it
     * is what attendance and allocation history are read against, so renumbering
     * seat 25 to seat 50 would silently rewrite what those records mean.
     *
     * The number is still required in the payload - the client sends back the one
     * it was given - and a request that carries a different one is refused here.
     * The form renders the field read-only, but that is a convenience; this check
     * is the boundary, because a read-only input is not a security control.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SEAT_UPDATE')")
    public SeatResponse updateSeat(Long libraryId, Long seatId, SeatRequest request) {
        requireLibrary(libraryId);
        Seat seat = requireSeat(libraryId, seatId);

        String seatNumber = request.getSeatNumber().trim();
        if (!seatNumber.equals(seat.getSeatNumber())) {
            throw new BusinessException(
                    "Seat number cannot be changed. Seat " + seat.getSeatNumber()
                            + " keeps the number it was created with.",
                    "SEAT_NUMBER_NOT_EDITABLE");
        }

        SeatAssignment active = activeAssignmentOrNull(seatId);

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = normaliseSettableStatus(request.getStatus());
            // An allocated seat cannot be moved to a status that contradicts the
            // allocation; the seat must be released first.
            if (active != null && !STATUS_OCCUPIED.equals(status)) {
                throw new ConflictException(
                        "Seat " + seat.getSeatNumber()
                                + " is allocated. Release it before changing its status.",
                        "SEAT_HAS_ACTIVE_ALLOCATION");
            }
            seat.setStatus(status);
        }

        seat.setZone(resolveZone(libraryId, request.getZoneId()));
        seat.setSeatType(resolveSeatType(libraryId, request.getSeatTypeId()));
        seat.setUpdatedAt(LocalDateTime.now());
        seat.setUpdatedBy(currentUserId());

        return SeatResponse.from(seatRepository.save(seat), active);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SEAT_UPDATE')")
    public SeatResponse deactivateSeat(Long libraryId, Long seatId) {
        requireLibrary(libraryId);
        Seat seat = requireSeat(libraryId, seatId);

        if (activeAssignmentOrNull(seatId) != null) {
            throw new ConflictException(
                    "Seat " + seat.getSeatNumber()
                            + " is allocated. Release it before taking it out of service.",
                    "SEAT_HAS_ACTIVE_ALLOCATION");
        }
        if (STATUS_INACTIVE.equals(seat.getStatus())) {
            throw new ConflictException(
                    "Seat " + seat.getSeatNumber() + " is already out of service",
                    "SEAT_ALREADY_INACTIVE");
        }

        seat.setStatus(STATUS_INACTIVE);
        seat.setUpdatedAt(LocalDateTime.now());
        seat.setUpdatedBy(currentUserId());
        return SeatResponse.from(seatRepository.save(seat), null);
    }

    /* ------------------------------------------------------------ allocation */

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SEAT_ASSIGN')")
    public SeatAllocationResponse allocateSeat(Long libraryId, Long seatId,
                                               SeatAllocationRequest request) {
        Library library = requireLibrary(libraryId);
        Seat seat = requireSeat(libraryId, seatId);

        // The student is looked up inside the same library, so a student id from
        // another tenant reads as not found rather than leaking its existence.
        Student student = studentRepository
                .findByStudentIdAndLibraryLibraryId(request.getStudentId(), libraryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found in this library", "STUDENT_NOT_FOUND"));

        if (!STATUS_AVAILABLE.equals(seat.getStatus())) {
            // Distinguish "someone is sitting here" from "this seat is not usable".
            if (activeAssignmentOrNull(seatId) != null) {
                throw new ConflictException(
                        "Seat " + seat.getSeatNumber() + " is already allocated",
                        "SEAT_ALREADY_ALLOCATED");
            }
            throw new ConflictException(
                    "Seat " + seat.getSeatNumber() + " is not available (" + seat.getStatus() + ")",
                    "SEAT_NOT_AVAILABLE");
        }

        // Guard the seat even when its status says AVAILABLE, so a stale status
        // cannot produce two active assignments on one seat.
        if (seatAssignmentRepository.findActiveBySeatId(seatId).isPresent()) {
            throw new ConflictException(
                    "Seat " + seat.getSeatNumber() + " is already allocated",
                    "SEAT_ALREADY_ALLOCATED");
        }

        seatAssignmentRepository.findActiveByStudentId(student.getStudentId()).ifPresent(existing -> {
            throw new ConflictException(
                    "This student already holds seat " + existing.getSeat().getSeatNumber(),
                    "STUDENT_ALREADY_HAS_SEAT");
        });

        LocalDate startDate = request.getStartDate() == null ? LocalDate.now() : request.getStartDate();

        SeatAssignment assignment = new SeatAssignment();
        assignment.setLibrary(library);
        assignment.setSeat(seat);
        assignment.setStudent(student);
        assignment.setStartDate(startDate);
        assignment.setStatus(ASSIGNMENT_ACTIVE);
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setCreatedBy(currentUserId());
        seatAssignmentRepository.save(assignment);

        seat.setStatus(STATUS_OCCUPIED);
        seat.setUpdatedAt(LocalDateTime.now());
        seat.setUpdatedBy(currentUserId());
        seatRepository.save(seat);

        return SeatAllocationResponse.from(assignment);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SEAT_ASSIGN')")
    public SeatAllocationResponse releaseSeat(Long libraryId, Long seatId) {
        requireLibrary(libraryId);
        Seat seat = requireSeat(libraryId, seatId);

        SeatAssignment assignment = seatAssignmentRepository.findActiveBySeatId(seatId)
                .orElseThrow(() -> new ConflictException(
                        "Seat " + seat.getSeatNumber() + " is not currently allocated",
                        "SEAT_NOT_ALLOCATED"));

        // The row is closed rather than deleted, preserving allocation history.
        assignment.setStatus(ASSIGNMENT_RELEASED);
        assignment.setEndDate(LocalDate.now());
        seatAssignmentRepository.save(assignment);

        seat.setStatus(STATUS_AVAILABLE);
        seat.setUpdatedAt(LocalDateTime.now());
        seat.setUpdatedBy(currentUserId());
        seatRepository.save(seat);

        return SeatAllocationResponse.from(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SEAT_VIEW')")
    public SeatAllocationResponse getCurrentAllocation(Long libraryId, Long seatId) {
        requireLibrary(libraryId);
        requireSeat(libraryId, seatId);

        SeatAssignment active = activeAssignmentOrNull(seatId);
        return active == null ? null : SeatAllocationResponse.from(active);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SEAT_VIEW')")
    public SeatAllocationResponse getStudentAllocation(Long studentId) {
        // Look the student up first so a missing student is 404, not 403.
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        Long libraryId = student.getLibrary() != null ? student.getLibrary().getLibraryId() : null;
        if (libraryId == null) {
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }
        requireAccess(() -> tenantAuthorizationService.requireLibraryAccess(currentUserId(), libraryId));

        return seatAssignmentRepository.findActiveByStudentId(studentId)
                .map(SeatAllocationResponse::from)
                .orElse(null);
    }

    /* -------------------------------------------------------- reference data */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SEAT_VIEW')")
    public List<SeatTypeResponse> getSeatTypes(Long libraryId) {
        requireLibrary(libraryId);
        return seatTypeRepository.findByLibraryLibraryIdOrderByName(libraryId).stream()
                .map(SeatTypeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SEAT_VIEW')")
    public List<SeatZoneResponse> getSeatZones(Long libraryId) {
        requireLibrary(libraryId);
        return seatZoneRepository.findByLibraryLibraryIdOrderByName(libraryId).stream()
                .map(SeatZoneResponse::from)
                .toList();
    }

    /* -------------------------------------------------------------- internals */

    /**
     * Resolves the library and confirms the caller is a member of it. The
     * library is looked up first so an unknown id is 404 rather than 403.
     */
    private Library requireLibrary(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        requireAccess(() -> tenantAuthorizationService.requireLibraryAccess(currentUserId(), libraryId));
        return library;
    }

    /** Scoping the lookup to the library is what stops seat-id probing across tenants. */
    private Seat requireSeat(Long libraryId, Long seatId) {
        return seatRepository.findBySeatIdAndLibraryLibraryId(seatId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat not found in this library", "SEAT_NOT_FOUND"));
    }

    private SeatAssignment activeAssignmentOrNull(Long seatId) {
        return seatAssignmentRepository.findActiveBySeatId(seatId).orElse(null);
    }

    private SeatZone resolveZone(Long libraryId, Long zoneId) {
        if (zoneId == null) return null;
        return seatZoneRepository.findByZoneIdAndLibraryLibraryId(zoneId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Zone not found in this library", "SEAT_ZONE_NOT_FOUND"));
    }

    private SeatType resolveSeatType(Long libraryId, Long seatTypeId) {
        if (seatTypeId == null) return null;
        return seatTypeRepository.findBySeatTypeIdAndLibraryLibraryId(seatTypeId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat type not found in this library", "SEAT_TYPE_NOT_FOUND"));
    }

    private String normaliseStatus(String requested, Set<String> allowed) {
        String status = requested.trim().toUpperCase();
        if (!allowed.contains(status)) {
            throw new BusinessException(
                    "Invalid seat status: " + requested + ". Allowed: " + String.join(", ", allowed),
                    "INVALID_SEAT_STATUS");
        }
        return status;
    }

    private String normaliseSettableStatus(String requested) {
        String status = requested.trim().toUpperCase();
        if (STATUS_OCCUPIED.equals(status)) {
            throw new BusinessException(
                    "A seat becomes OCCUPIED by allocating it, not by setting its status",
                    "INVALID_SEAT_STATUS");
        }
        return normaliseStatus(status, SETTABLE_STATUSES);
    }

    private Long currentUserId() {
        return tenantAuthorizationService.getCurrentUserId().orElse(null);
    }

    /**
     * Converts only an authorisation denial into ForbiddenException. Anything
     * else propagates so infrastructure failures still surface as INTERNAL_ERROR
     * rather than being masked as a permission problem.
     */
    private void requireAccess(Runnable check) {
        try {
            check.run();
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
    }
}
