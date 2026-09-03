package com.librarysaas.organization.service;

import com.librarysaas.organization.dto.MembershipResponse;

import java.util.List;

public interface UserManagementService {

    void addUserToOrganization(Long organizationId, Long userId, Boolean isPrimary);

    void removeUserFromOrganization(Long organizationId, Long userId);

    void addUserToLibrary(Long libraryId, Long userId, Boolean isPrimary);

    void removeUserFromLibrary(Long libraryId, Long userId);

    void setUserPrimaryOrganization(Long userId, Long organizationId);

    void setUserPrimaryLibrary(Long userId, Long libraryId);

    /** Members of an organization, primary first. Includes non-active memberships. */
    List<MembershipResponse> getOrganizationMembers(Long organizationId);

    /** Members of a library, primary first. Includes non-active memberships. */
    List<MembershipResponse> getLibraryMembers(Long libraryId);

    /**
     * Activates or deactivates a membership without deleting it, so the
     * joined_at history survives. Removal remains a separate operation.
     */
    MembershipResponse updateOrganizationMembershipStatus(Long organizationId, Long userId, String status);

    MembershipResponse updateLibraryMembershipStatus(Long libraryId, Long userId, String status);
}
