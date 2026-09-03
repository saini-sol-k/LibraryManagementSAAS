package com.librarysaas.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for retiring or reinstating a fee plan.
 *
 * The plan comes from the URL, so only the new status travels in the body.
 * Which statuses exist is enforced by FeePlanService.
 */
public class FeePlanStatusRequest {

    @NotBlank(message = "Status is required")
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    public FeePlanStatusRequest() {}

    public FeePlanStatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
