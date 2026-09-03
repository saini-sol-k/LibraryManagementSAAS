package com.librarysaas.reporting.dto;

import com.librarysaas.membership.entity.StudentMembership;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One membership approaching its end date.
 *
 * Purpose-built for the report rather than reusing the membership module's own
 * response, so it carries only what a "who lapses soon" list needs and no
 * version or audit fields. Reporting never changes a membership's status; this
 * is a view of rows that already exist.
 */
public class ExpiringMembershipResponse {

    private Long membershipId;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private String membershipNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean autoRenew;
    /** Whole days from the library's local today to the end date; never negative. */
    private long daysRemaining;

    public static ExpiringMembershipResponse from(StudentMembership membership, LocalDate asOf) {
        ExpiringMembershipResponse r = new ExpiringMembershipResponse();
        r.membershipId = membership.getMembershipId();

        if (membership.getStudent() != null) {
            r.studentId = membership.getStudent().getStudentId();
            r.studentCode = membership.getStudent().getStudentCode();
            String first = membership.getStudent().getFirstName();
            String last = membership.getStudent().getLastName();
            r.studentName = last == null || last.isBlank() ? first : first + " " + last;
        }

        r.membershipNumber = membership.getMembershipNumber();
        r.startDate = membership.getStartDate();
        r.endDate = membership.getEndDate();
        r.status = membership.getStatus();
        r.autoRenew = membership.getAutoRenew();
        r.daysRemaining = membership.getEndDate() == null
                ? 0
                : Math.max(0, ChronoUnit.DAYS.between(asOf, membership.getEndDate()));
        return r;
    }

    public Long getMembershipId() { return membershipId; }
    public Long getStudentId() { return studentId; }
    public String getStudentCode() { return studentCode; }
    public String getStudentName() { return studentName; }
    public String getMembershipNumber() { return membershipNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public Boolean getAutoRenew() { return autoRenew; }
    public long getDaysRemaining() { return daysRemaining; }
}
