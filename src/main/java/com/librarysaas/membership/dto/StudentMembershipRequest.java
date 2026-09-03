package com.librarysaas.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create payload for a student membership.
 *
 * libraryId is deliberately absent: it comes from the path, so a caller cannot
 * place a membership in another tenant by editing the body. That the student
 * belongs to that library, that the number is free, and that the period does
 * not overlap an existing active membership are all decided by
 * StudentMembershipService, not here.
 */
public class StudentMembershipRequest {

    @NotNull(message = "Student id is required")
    @Positive(message = "Student id must be a positive number")
    private Long studentId;

    @NotBlank(message = "Membership number is required")
    @Size(max = 50, message = "Membership number must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 _/-]{0,49}$",
            message = "Membership number may only contain letters, digits, spaces and - _ /")
    private String membershipNumber;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    /** Optional; treated as false when omitted. Stored only, never acted on. */
    private Boolean autoRenew;

    public StudentMembershipRequest() {}

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getMembershipNumber() {
        return membershipNumber;
    }

    public void setMembershipNumber(String membershipNumber) {
        this.membershipNumber = membershipNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
    }
}
