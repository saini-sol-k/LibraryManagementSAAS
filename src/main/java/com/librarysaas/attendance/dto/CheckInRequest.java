package com.librarysaas.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Check a student into a library.
 *
 * libraryId is deliberately absent: it comes from the path, so a caller cannot
 * record a visit in another tenant by editing the body. The check-in time is
 * the server clock rather than a field, because nothing in the schema defines
 * rules for back-dating a visit.
 */
public class CheckInRequest {

    @NotNull(message = "Student id is required")
    @Positive(message = "Student id must be a positive number")
    private Long studentId;

    /**
     * Optional. Must belong to the same library when supplied. When omitted the
     * student's current seat allocation is recorded, if they hold one.
     */
    @Positive(message = "Seat id must be a positive number")
    private Long seatId;

    public CheckInRequest() {}

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }
}
