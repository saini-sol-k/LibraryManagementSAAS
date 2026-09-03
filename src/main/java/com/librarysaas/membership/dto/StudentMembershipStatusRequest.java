package com.librarysaas.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for changing a membership's status.
 *
 * The membership comes from the URL, so only the new status travels in the
 * body. Which statuses exist and which transitions are legal are enforced by
 * StudentMembershipService rather than here, matching how Phase 2C handles
 * staff membership status.
 */
public class StudentMembershipStatusRequest {

    @NotBlank(message = "Status is required")
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    public StudentMembershipStatusRequest() {}

    public StudentMembershipStatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
