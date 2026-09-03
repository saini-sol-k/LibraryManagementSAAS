package com.librarysaas.attendance.controller;

import com.librarysaas.attendance.dto.AttendanceResponse;
import com.librarysaas.attendance.dto.CheckInRequest;
import com.librarysaas.attendance.service.AttendanceService;
import com.librarysaas.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance: students checking into and out of a library.
 *
 * The day's list is nested under the library that owns it, so a read is
 * tenant-checkable from the path alone. A single visit sits at the top level
 * because its id is globally unique, and the service authorises against the
 * library on the visit's own row. There is no delete: the schema keeps every
 * visit, and no permission exists for removing one.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Attendance", description = "Student check-in, check-out and attendance history")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Autowired
    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/libraries/{libraryId}/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    @Operation(summary = "List a library's attendance for one day",
            description = "Newest check-in first. Defaults to today when no date is given. "
                    + "Optional status filter: PRESENT for visits still in progress, COMPLETED "
                    + "for those checked out. Requires ATTENDANCE_VIEW and membership of the library.")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> listLibraryAttendance(
            @PathVariable Long libraryId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {
        List<AttendanceResponse> rows = attendanceService.getLibraryAttendance(libraryId, date, status);
        return ResponseEntity.ok(new ApiResponse<>(true, "Attendance retrieved", rows));
    }

    @PostMapping("/libraries/{libraryId}/attendance/check-in")
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE')")
    @Operation(summary = "Check a student in",
            description = "Opens a visit at the current server time. The student must belong to "
                    + "this library and must not already be checked in. A seat may be named, and "
                    + "must belong to this library; when omitted the student's current seat "
                    + "allocation is recorded if they hold one.")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @PathVariable Long libraryId,
            @Valid @RequestBody CheckInRequest request) {
        AttendanceResponse attendance = attendanceService.checkIn(libraryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Student checked in", attendance));
    }

    @PostMapping("/attendance/{attendanceId}/check-out")
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE')")
    @Operation(summary = "Check a student out",
            description = "Closes an open visit at the current server time and records its "
                    + "duration in minutes. A visit that is already closed is a conflict. Guarded "
                    + "by ATTENDANCE_CREATE because the schema defines no ATTENDANCE_UPDATE "
                    + "permission.")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut(
            @PathVariable Long attendanceId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Student checked out",
                attendanceService.checkOut(attendanceId)));
    }

    @GetMapping("/attendance/{attendanceId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    @Operation(summary = "Get one attendance record",
            description = "Requires membership of the library the visit belongs to.")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getAttendance(
            @PathVariable Long attendanceId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Attendance retrieved",
                attendanceService.getAttendance(attendanceId)));
    }

    @GetMapping("/students/{studentId}/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    @Operation(summary = "List one student's visit history",
            description = "Newest first. Requires membership of the student's library.")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> listStudentAttendance(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Attendance retrieved",
                attendanceService.getStudentAttendance(studentId)));
    }
}
