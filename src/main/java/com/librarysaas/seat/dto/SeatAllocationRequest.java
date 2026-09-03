package com.librarysaas.seat.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Allocate a seat to a student.
 *
 * The seat and library come from the path; only the student and an optional
 * start date are supplied.
 */
public class SeatAllocationRequest {

    @NotNull(message = "Student is required")
    private Long studentId;

    /** Optional. Defaults to today when omitted. */
    private LocalDate startDate;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
}
