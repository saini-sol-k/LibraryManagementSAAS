package com.librarysaas.membership.dto;

import com.librarysaas.membership.entity.StudentMembership;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One student membership.
 *
 * Carries just enough of the student to label a row in the UI, following
 * SeatAllocationResponse, so the member list never turns into a second student
 * endpoint. {@code expired} is derived at read time from the end date: nothing
 * in the system sweeps memberships into EXPIRED, so a stored ACTIVE status can
 * legitimately sit on a period that has already passed.
 */
public class StudentMembershipResponse {

    private Long membershipId;
    private Long libraryId;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private String membershipNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean autoRenew;
    private Boolean expired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static StudentMembershipResponse from(StudentMembership membership) {
        StudentMembershipResponse response = new StudentMembershipResponse();
        response.membershipId = membership.getMembershipId();

        if (membership.getLibrary() != null) {
            response.libraryId = membership.getLibrary().getLibraryId();
        }
        if (membership.getStudent() != null) {
            response.studentId = membership.getStudent().getStudentId();
            response.studentCode = membership.getStudent().getStudentCode();

            String first = membership.getStudent().getFirstName();
            String last = membership.getStudent().getLastName();
            response.studentName = last == null || last.isBlank() ? first : first + " " + last;
        }

        response.membershipNumber = membership.getMembershipNumber();
        response.startDate = membership.getStartDate();
        response.endDate = membership.getEndDate();
        response.status = membership.getStatus();
        response.autoRenew = membership.getAutoRenew();
        response.expired = membership.getEndDate() != null
                && membership.getEndDate().isBefore(LocalDate.now());
        response.createdAt = membership.getCreatedAt();
        response.updatedAt = membership.getUpdatedAt();
        response.version = membership.getVersion();
        return response;
    }

    public Long getMembershipId() {
        return membershipId;
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

    public String getMembershipNumber() {
        return membershipNumber;
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

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public Boolean getExpired() {
        return expired;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
