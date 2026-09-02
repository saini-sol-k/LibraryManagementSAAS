package com.librarysaas.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OrganizationCreateRequest {
    
    @NotBlank(message = "Organization code is required")
    @Size(max = 50, message = "Organization code must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
             message = "Organization code may only contain letters, digits, hyphen and underscore")
    private String organizationCode;

    @NotBlank(message = "Organization name is required")
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

    public OrganizationCreateRequest() {}

    public OrganizationCreateRequest(String organizationCode, String name, String legalName, 
                                   String email, String mobile) {
        this.organizationCode = organizationCode;
        this.name = name;
        this.legalName = legalName;
        this.email = email;
        this.mobile = mobile;
    }

    // Getters and setters

    public String getOrganizationCode() {
        return organizationCode;
    }

    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
    }

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
}
