package com.librarysaas.seat.dto;

import com.librarysaas.seat.entity.SeatAssignment;

import java.time.LocalDate;

/**
 * One seat allocation. Carries just enough of the student to label a seat in
 * the UI without exposing the student record.
 */
public class SeatAllocationResponse {

    private Long assignmentId;
    private Long seatId;
    private String seatNumber;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;

    public static SeatAllocationResponse from(SeatAssignment assignment) {
        SeatAllocationResponse response = new SeatAllocationResponse();
        response.assignmentId = assignment.getAssignmentId();

        if (assignment.getSeat() != null) {
            response.seatId = assignment.getSeat().getSeatId();
            response.seatNumber = assignment.getSeat().getSeatNumber();
        }
        if (assignment.getStudent() != null) {
            response.studentId = assignment.getStudent().getStudentId();
            response.studentCode = assignment.getStudent().getStudentCode();

            String first = assignment.getStudent().getFirstName();
            String last = assignment.getStudent().getLastName();
            response.studentName = last == null || last.isBlank() ? first : first + " " + last;
        }

        response.startDate = assignment.getStartDate();
        response.endDate = assignment.getEndDate();
        response.status = assignment.getStatus();
        return response;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public String getSeatNumber() {
        return seatNumber;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStatus() {
        return status;
    }
}
