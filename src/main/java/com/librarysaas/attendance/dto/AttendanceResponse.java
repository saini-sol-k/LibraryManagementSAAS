package com.librarysaas.attendance.dto;

import com.librarysaas.attendance.entity.Attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One visit.
 *
 * Carries just enough of the student and seat to label a row, following
 * SeatAllocationResponse, so an attendance list never becomes a second student
 * endpoint. {@code open} is derived from the absence of a check-out time, which
 * is what actually decides whether the visit is still running.
 */
public class AttendanceResponse {

    private Long attendanceId;
    private Long libraryId;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private Long seatId;
    private String seatNumber;
    private LocalDate attendanceDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Integer durationMinutes;
    private String status;
    private Boolean open;

    public static AttendanceResponse from(Attendance attendance) {
        AttendanceResponse response = new AttendanceResponse();
        response.attendanceId = attendance.getAttendanceId();

        if (attendance.getLibrary() != null) {
            response.libraryId = attendance.getLibrary().getLibraryId();
        }
        if (attendance.getStudent() != null) {
            response.studentId = attendance.getStudent().getStudentId();
            response.studentCode = attendance.getStudent().getStudentCode();

            String first = attendance.getStudent().getFirstName();
            String last = attendance.getStudent().getLastName();
            response.studentName = last == null || last.isBlank() ? first : first + " " + last;
        }
        if (attendance.getSeat() != null) {
            response.seatId = attendance.getSeat().getSeatId();
            response.seatNumber = attendance.getSeat().getSeatNumber();
        }

        response.attendanceDate = attendance.getAttendanceDate();
        response.checkInTime = attendance.getCheckInTime();
        response.checkOutTime = attendance.getCheckOutTime();
        response.durationMinutes = attendance.getDurationMinutes();
        response.status = attendance.getStatus();
        response.open = attendance.getCheckOutTime() == null;
        return response;
    }

    public Long getAttendanceId() {
        return attendanceId;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public String getStudentName() {
        return studentName;
    }

    public Long getSeatId() {
        return seatId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getOpen() {
        return open;
    }
}
