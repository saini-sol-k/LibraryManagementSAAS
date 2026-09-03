package com.librarysaas.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The editable part of a membership: its number, its period and the auto-renew
 * flag.
 *
 * Used both to update a membership in place and to describe the successor
 * period when renewing. Neither the student nor the library appears, because a
 * membership never moves between students or tenants; renewing inherits the
 * student from the membership being renewed. Status is changed through its own
 * endpoint, so it is absent here too.
 */
public class StudentMembershipUpdateRequest {

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

    public StudentMembershipUpdateRequest() {}

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
