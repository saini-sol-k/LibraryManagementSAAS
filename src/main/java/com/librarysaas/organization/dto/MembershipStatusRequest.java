package com.librarysaas.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for changing a membership's status.
 *
 * The tenant and the target user come from the URL, so only the new status
 * travels in the body. Which statuses are allowed, and the rules around
 * deactivating a primary or a last remaining member, are enforced by
 * UserManagementService rather than here.
 */
public class MembershipStatusRequest {

    @NotBlank(message = "Status is required")
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    public MembershipStatusRequest() {}

    public MembershipStatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
