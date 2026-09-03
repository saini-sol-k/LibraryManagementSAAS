package com.librarysaas.attendance.service.impl;

import com.librarysaas.attendance.dto.AttendanceResponse;
import com.librarysaas.attendance.dto.CheckInRequest;
import com.librarysaas.attendance.entity.Attendance;
import com.librarysaas.attendance.repository.AttendanceRepository;
import com.librarysaas.attendance.service.AttendanceService;
import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.seat.entity.Seat;
import com.librarysaas.seat.repository.SeatAssignmentRepository;
import com.librarysaas.seat.repository.SeatRepository;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Attendance rules.
 *
 * Two properties are load bearing:
 *
 * <ul>
 *   <li>A visit never crosses a tenant. The library comes from the path or from
 *       the attendance row itself, never from a header, and the student and seat
 *       are matched against that library by plain field comparison. Those checks
 *       do not consult the caller's authorities, so a super admin cannot record
 *       a visit the rules would otherwise forbid.</li>
 *   <li>A student has at most one open visit. Nothing in the schema enforces
 *       this - there is no unique constraint - so the check here is the only
 *       thing standing between the data and a double check-in.</li>
 * </ul>
 *
 * The status vocabulary is exactly what V1__initial_schema evidences: PRESENT
 * as the column default for an open visit, COMPLETED on the seeded closed row.
 */
@Service
public class AttendanceServiceImpl implements AttendanceService {

    static final String STATUS_PRESENT = "PRESENT";
    static final String STATUS_COMPLETED = "COMPLETED";

    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_PRESENT, STATUS_COMPLETED);

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final LibraryRepository libraryRepository;
    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                                 StudentRepository studentRepository,
                                 LibraryRepository libraryRepository,
                                 SeatRepository seatRepository,
                                 SeatAssignmentRepository seatAssignmentRepository,
                                 TenantAuthorizationService tenantAuthorizationService) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.libraryRepository = libraryRepository;
        this.seatRepository = seatRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /* ---------------------------------------------------------------- reads */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public List<AttendanceResponse> getLibraryAttendance(Long libraryId, LocalDate date, String status) {
        requireLibrary(libraryId);

        // The table is indexed by (library_id, attendance_date), so a day is the
        // natural unit to read. Today is the useful default for a desk screen.
        LocalDate day = date == null ? LocalDate.now() : date;

        List<Attendance> rows = status == null || status.isBlank()
                ? attendanceRepository.findByLibraryAndDate(libraryId, day)
                : attendanceRepository.findByLibraryAndDateAndStatus(
                        libraryId, day, normaliseStatus(status));

        return rows.stream().map(AttendanceResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public List<AttendanceResponse> getStudentAttendance(Long studentId) {
        // The student resolves first so an unknown id is 404 rather than 403.
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        requireLibraryAccess(libraryIdOf(student));

        return attendanceRepository.findByStudent(studentId).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public AttendanceResponse getAttendance(Long attendanceId) {
        return AttendanceResponse.from(requireAttendance(attendanceId));
    }

    /**
     * The current time at the precision the database actually stores.
     *
     * check_in_time and check_out_time are TIMESTAMP columns with no fractional
     * seconds, and MySQL ROUNDS to the nearest second rather than truncating. An
     * unrounded value therefore comes back up to half a second later than it was
     * written, which made a fast check-in followed by check-out look like a visit
     * that ended before it began. Working in whole seconds keeps what is stored
     * identical to what was set, and keeps the derived duration stable.
     */
    private static LocalDateTime nowToStoredPrecision() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /* --------------------------------------------------------------- writes */

    /**
     * Check-out mutates an existing row, which would normally call for an
     * ATTENDANCE_UPDATE permission. No such permission exists in the schema, so
     * both halves of a visit are guarded by ATTENDANCE_CREATE rather than
     * inventing a permission that would need a migration to seed.
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE')")
    public AttendanceResponse checkIn(Long libraryId, CheckInRequest request) {
        Library library = requireLibrary(libraryId);
        Student student = requireStudentInLibrary(request.getStudentId(), libraryId);

        // One visit at a time. Checked against the absence of a check-out time,
        // so this holds even for a row whose status was somehow left wrong.
        if (!attendanceRepository.findOpenByStudent(student.getStudentId()).isEmpty()) {
            throw new ConflictException("This student is already checked in",
                    "STUDENT_ALREADY_CHECKED_IN");
        }

        Seat seat = resolveSeat(libraryId, student, request.getSeatId());

        LocalDateTime now = nowToStoredPrecision();

        Attendance attendance = new Attendance();
        attendance.setLibrary(library);
        attendance.setStudent(student);
        attendance.setSeat(seat);
        attendance.setAttendanceDate(now.toLocalDate());
        attendance.setCheckInTime(now);
        attendance.setStatus(STATUS_PRESENT);
        attendance.setCreatedAt(now);
        attendance.setUpdatedAt(now);

        return AttendanceResponse.from(attendanceRepository.saveAndFlush(attendance));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE')")
    public AttendanceResponse checkOut(Long attendanceId) {
        Attendance attendance = requireAttendance(attendanceId);

        if (attendance.getCheckOutTime() != null) {
            throw new ConflictException("This visit has already been checked out",
                    "ATTENDANCE_ALREADY_CLOSED");
        }

        LocalDateTime now = nowToStoredPrecision();
        LocalDateTime checkIn = attendance.getCheckInTime();

        // A clock adjustment must not produce a negative stay.
        if (checkIn != null && now.isBefore(checkIn)) {
            throw new BusinessException("Check-out time cannot be before check-in time",
                    "INVALID_ATTENDANCE_PERIOD");
        }

        attendance.setCheckOutTime(now);
        attendance.setDurationMinutes(checkIn == null
                ? null
                : (int) Duration.between(checkIn, now).toMinutes());
        attendance.setStatus(STATUS_COMPLETED);
        attendance.setUpdatedAt(now);

        return AttendanceResponse.from(attendanceRepository.saveAndFlush(attendance));
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves the library and confirms the caller belongs to it. The library is
     * looked up first so an unknown id is 404 rather than 403.
     */
    private Library requireLibrary(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        requireLibraryAccess(libraryId);
        return library;
    }

    /**
     * Resolves a visit and authorises against the library on its own row, so an
     * attendance id from another tenant is refused rather than served.
     */
    private Attendance requireAttendance(Long attendanceId) {
        Attendance attendance = attendanceRepository.findByIdWithDetail(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attendance record not found", "ATTENDANCE_NOT_FOUND"));

        Long libraryId = attendance.getLibrary() == null
                ? null
                : attendance.getLibrary().getLibraryId();
        if (libraryId == null) {
            throw new ResourceNotFoundException("Attendance record not found", "ATTENDANCE_NOT_FOUND");
        }
        requireLibraryAccess(libraryId);
        return attendance;
    }

    /**
     * The tenant boundary for this module.
     *
     * A visit may only ever record a student in the library that student belongs
     * to. The comparison is a plain field check on data the caller has no
     * influence over, so it holds for every role, a super admin included.
     */
    private Student requireStudentInLibrary(Long studentId, Long libraryId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        Long studentLibraryId = student.getLibrary() == null
                ? null
                : student.getLibrary().getLibraryId();

        if (studentLibraryId == null || !studentLibraryId.equals(libraryId)) {
            throw new BusinessException("Student does not belong to this library",
                    "STUDENT_NOT_IN_LIBRARY");
        }
        return student;
    }

    /**
     * An explicitly named seat must belong to this library; scoping the lookup
     * is what stops seat-id probing across tenants. When none is named the
     * student's current allocation is recorded, which is how the seeded rows
     * pair a visit with a seat.
     */
    private Seat resolveSeat(Long libraryId, Student student, Long requestedSeatId) {
        if (requestedSeatId != null) {
            return seatRepository.findBySeatIdAndLibraryLibraryId(requestedSeatId, libraryId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Seat not found in this library", "SEAT_NOT_FOUND"));
        }
        return seatAssignmentRepository.findActiveByStudentId(student.getStudentId())
                .map(assignment -> assignment.getSeat())
                .orElse(null);
    }

    /** Attendance statuses are a closed set; anything else is a business error. */
    private String normaliseStatus(String requested) {
        String status = requested == null ? "" : requested.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    "Invalid attendance status: " + requested + ". Allowed: "
                            + String.join(", ", ALLOWED_STATUSES),
                    "INVALID_ATTENDANCE_STATUS");
        }
        return status;
    }

    private Long libraryIdOf(Student student) {
        Long libraryId = student.getLibrary() == null ? null : student.getLibrary().getLibraryId();
        if (libraryId == null) {
            // A student with no library cannot be tenant-checked, so it must not
            // be readable rather than defaulting open.
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }
        return libraryId;
    }

    private Long currentUserId() {
        return tenantAuthorizationService.getCurrentUserId().orElse(null);
    }

    /**
     * Converts only an authorisation denial into ForbiddenException. Anything
     * else propagates so infrastructure failures still surface as INTERNAL_ERROR
     * rather than being masked as a permission problem.
     */
    private void requireLibraryAccess(Long libraryId) {
        try {
            tenantAuthorizationService.requireLibraryAccess(currentUserId(), libraryId);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
    }
}
