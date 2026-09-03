package com.librarysaas.attendance.service;

import com.librarysaas.attendance.dto.AttendanceResponse;
import com.librarysaas.attendance.dto.CheckInRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance: students checking into and out of a library.
 *
 * Every operation is library scoped and re-checks the caller's membership of
 * that library. Rows are never deleted; a visit ends by being checked out.
 */
public interface AttendanceService {

    /** One day of a library's attendance, newest check-in first. Defaults to today. */
    List<AttendanceResponse> getLibraryAttendance(Long libraryId, LocalDate date, String status);

    /** One student's visit history, newest first. */
    List<AttendanceResponse> getStudentAttendance(Long studentId);

    AttendanceResponse getAttendance(Long attendanceId);

    /** Opens a visit. The student must belong to the library and not already be in. */
    AttendanceResponse checkIn(Long libraryId, CheckInRequest request);

    /** Closes an open visit and records its duration. */
    AttendanceResponse checkOut(Long attendanceId);
}
