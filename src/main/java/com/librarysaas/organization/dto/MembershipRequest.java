package com.librarysaas.organization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for adding a user to an organization or a library.
 *
 * The tenant (organization or library) is taken from the URL, so only the target user
 * and the primary flag travel in the body. Whether the tenant is active, whether the
 * user already holds the membership and the cross-tenant rules are enforced by
 * UserManagementService.
 */
public class MembershipRequest {

    @NotNull(message = "User id is required")
    @Positive(message = "User id must be a positive number")
    private Long userId;

    /** Optional; treated as false when omitted. */
    private Boolean isPrimary;

    public MembershipRequest() {}

    public MembershipRequest(Long userId, Boolean isPrimary) {
        this.userId = userId;
        this.isPrimary = isPrimary;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
