package com.librarysaas.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OrganizationUpdateRequest {
    
    // All fields are optional on update; only supplied values are applied.
    // Allowed status values are enforced by OrganizationService, not duplicated here.

    @Size(max = 200, message = "Organization name must not exceed 200 characters")
    private String name;

    @Size(max = 250, message = "Legal name must not exceed 250 characters")
    private String legalName;

    @Email(message = "Email must be a well-formed email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 30, message = "Mobile must not exceed 30 characters")
    @Pattern(regexp = "^[0-9+][0-9 ()-]{5,19}$", message = "Mobile must be a valid contact number")
    private String mobile;

    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    public OrganizationUpdateRequest() {}

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
