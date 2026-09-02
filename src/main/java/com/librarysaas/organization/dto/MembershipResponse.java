package com.librarysaas.organization.dto;

import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.security.model.User;

import java.time.LocalDateTime;

/**
 * A user's membership of one tenant, for organization and library membership APIs.
 *
 * Exactly one of {@code organizationId} / {@code libraryId} is populated, depending on
 * which tenant the membership belongs to. Only non-sensitive user fields are carried:
 * the password hash and verification flags are never mapped.
 */
public class MembershipResponse {

    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Long organizationId;
    private Long libraryId;
    private Boolean isPrimary;
    private String status;
    private LocalDateTime joinedAt;

    public MembershipResponse() {}

    public static MembershipResponse from(UserOrganization membership) {
        MembershipResponse r = new MembershipResponse();
        r.userId = membership.getId() != null ? membership.getId().getUserId() : null;
        r.organizationId = membership.getId() != null ? membership.getId().getOrganizationId() : null;
        r.isPrimary = membership.getIsPrimary();
        r.status = membership.getStatus();
        r.joinedAt = membership.getJoinedAt();
        r.applyUser(membership.getUser());
        return r;
    }

    public static MembershipResponse from(UserLibrary membership) {
        MembershipResponse r = new MembershipResponse();
        r.userId = membership.getId() != null ? membership.getId().getUserId() : null;
        r.libraryId = membership.getId() != null ? membership.getId().getLibraryId() : null;
        r.isPrimary = membership.getIsPrimary();
        r.status = membership.getStatus();
        r.joinedAt = membership.getJoinedAt();
        r.applyUser(membership.getUser());
        return r;
    }

    /** Copies only the user fields that are safe to publish. */
    private void applyUser(User user) {
        if (user == null) {
            return;
        }
        if (this.userId == null) {
            this.userId = user.getUserId();
        }
        this.username = user.getUsername();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
