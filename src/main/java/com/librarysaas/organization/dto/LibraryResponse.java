package com.librarysaas.organization.dto;

import com.librarysaas.library.entity.Library;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class LibraryResponse {
    
    private Long libraryId;
    private Long organizationId;
    private String libraryCode;
    private String name;
    private String description;
    private String email;
    private String mobile;
    private String status;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private String timezone;
    private String currency;
    /** How many seats the library is configured to have. Seat rows follow from it. */
    private Integer seatCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public LibraryResponse() {}

    public LibraryResponse(Library lib) {
        this.libraryId = lib.getLibraryId();
        this.organizationId = lib.getOrganization() != null ? lib.getOrganization().getOrganizationId() : null;
        this.libraryCode = lib.getLibraryCode();
        this.name = lib.getName();
        this.description = lib.getDescription();
        this.email = lib.getEmail();
        this.mobile = lib.getMobile();
        this.status = lib.getStatus();
        this.openingTime = lib.getOpeningTime();
        this.closingTime = lib.getClosingTime();
        this.timezone = lib.getTimezone();
        this.currency = lib.getCurrency();
        this.seatCount = lib.getSeatCount();
        this.createdAt = lib.getCreatedAt();
        this.updatedAt = lib.getUpdatedAt();
    }

    public static LibraryResponse from(Library lib) {
        return new LibraryResponse(lib);
    }

    // Getters and setters

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public String getLibraryCode() {
        return libraryCode;
    }

    public void setLibraryCode(String libraryCode) {
        this.libraryCode = libraryCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public void setOpeningTime(LocalTime openingTime) {
        this.openingTime = openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }
}
