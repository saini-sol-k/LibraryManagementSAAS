package com.librarysaas.organization.dto;

import com.librarysaas.organization.entity.Organization;
import java.time.LocalDateTime;

public class OrganizationResponse {
    
    private Long organizationId;
    private String organizationCode;
    private String name;
    private String legalName;
    private String email;
    private String mobile;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OrganizationResponse() {}

    public OrganizationResponse(Organization org) {
        this.organizationId = org.getOrganizationId();
        this.organizationCode = org.getOrganizationCode();
        this.name = org.getName();
        this.legalName = org.getLegalName();
        this.email = org.getEmail();
        this.mobile = org.getMobile();
        this.status = org.getStatus();
        this.createdAt = org.getCreatedAt();
        this.updatedAt = org.getUpdatedAt();
    }

    public static OrganizationResponse from(Organization org) {
        return new OrganizationResponse(org);
    }

    // Getters and setters

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
