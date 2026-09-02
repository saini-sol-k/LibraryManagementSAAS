package com.librarysaas.organization.dto;

public class OrganizationCreateRequest {
    
    private String organizationCode;
    private String name;
    private String legalName;
    private String email;
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
