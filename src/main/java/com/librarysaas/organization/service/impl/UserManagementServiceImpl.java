package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.entity.Organization;
import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.repository.UserLibraryRepository;
import com.librarysaas.organization.repository.UserOrganizationRepository;
import com.librarysaas.organization.service.UserManagementService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class UserManagementServiceImpl implements UserManagementService {

    private static final String STATUS_ACTIVE = OrganizationServiceImpl.STATUS_ACTIVE;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final LibraryRepository libraryRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public UserManagementServiceImpl(UserRepository userRepository,
                                   OrganizationRepository organizationRepository,
                                   LibraryRepository libraryRepository,
                                   UserOrganizationRepository userOrganizationRepository,
                                   UserLibraryRepository userLibraryRepository,
                                   TenantAuthorizationService tenantAuthorizationService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.libraryRepository = libraryRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public void addUserToOrganization(Long organizationId, Long userId, Boolean isPrimary) {
        Long currentUserId = requireCurrentUserId();

        // Verify current user has access to organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        // Verify organization exists and is operating
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found", "ORGANIZATION_NOT_FOUND"));
        if (!STATUS_ACTIVE.equals(org.getStatus())) {
            throw new BusinessException("Cannot add a user to an inactive organization", "ORGANIZATION_INACTIVE");
        }

        requireUserExists(userId);

        boolean primary = Boolean.TRUE.equals(isPrimary);

        // An existing membership is either a genuine conflict or a rejoin.
        UserOrganization membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(userId, organizationId)
                .orElse(null);
        if (membership != null && STATUS_ACTIVE.equals(membership.getStatus())) {
            throw new ConflictException("User is already a member of this organization",
                    "USER_ALREADY_IN_ORGANIZATION");
        }

        if (primary) {
            clearPrimaryOrganization(userId, organizationId);
        }

        if (membership == null) {
            membership = new UserOrganization(userId, organizationId);
            membership.setJoinedAt(LocalDateTime.now());
            membership.setCreatedAt(LocalDateTime.now());
        }
        membership.setIsPrimary(primary);
        membership.setStatus(STATUS_ACTIVE);

        userOrganizationRepository.save(membership);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void removeUserFromOrganization(Long organizationId, Long userId) {
        Long currentUserId = requireCurrentUserId();

        // Verify current user has access to organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        UserOrganization uo = userOrganizationRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this organization",
                        "USER_NOT_IN_ORGANIZATION"));

        // An organization must never be left without an active member to administer it.
        if (STATUS_ACTIVE.equals(uo.getStatus())
                && userOrganizationRepository.findActiveByOrganizationId(organizationId).size() <= 1) {
            throw new BusinessException("Cannot remove the last active member of an organization",
                    "ORGANIZATION_LAST_MEMBER");
        }

        // Also remove from all libraries in this organization
        libraryRepository.findByOrganizationId(organizationId).forEach(lib ->
                userLibraryRepository.findByUserIdAndLibraryId(userId, lib.getLibraryId())
                        .ifPresent(ul -> userLibraryRepository.delete(ul))
        );

        userOrganizationRepository.delete(uo);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public void addUserToLibrary(Long libraryId, Long userId, Boolean isPrimary) {
        Long currentUserId = requireCurrentUserId();

        // Verify library exists
        Library lib = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        // Verify current user has access to library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, libraryId);

        if (!STATUS_ACTIVE.equals(lib.getStatus())) {
            throw new BusinessException("Cannot add a user to an inactive library", "LIBRARY_INACTIVE");
        }

        requireUserExists(userId);

        // CRITICAL: a library membership may never widen the tenant boundary - the target
        // user must already belong to the library's owning organization.
        Organization org = lib.getOrganization();
        if (org == null) {
            throw new BusinessException("Library is not linked to an organization", "LIBRARY_ORGANIZATION_MISSING");
        }
        if (!tenantAuthorizationService.hasOrganizationAccess(userId, org.getOrganizationId())) {
            throw new BusinessException("User is not a member of the library's organization",
                    "USER_NOT_IN_ORGANIZATION");
        }

        boolean primary = Boolean.TRUE.equals(isPrimary);

        UserLibrary membership = userLibraryRepository.findByUserIdAndLibraryId(userId, libraryId).orElse(null);
        if (membership != null && STATUS_ACTIVE.equals(membership.getStatus())) {
            throw new ConflictException("User is already a member of this library", "USER_ALREADY_IN_LIBRARY");
        }

        if (primary) {
            clearPrimaryLibrary(userId, libraryId);
        }

        if (membership == null) {
            membership = new UserLibrary(userId, libraryId);
            membership.setJoinedAt(LocalDateTime.now());
            membership.setCreatedAt(LocalDateTime.now());
        }
        membership.setIsPrimary(primary);
        membership.setStatus(STATUS_ACTIVE);

        userLibraryRepository.save(membership);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void removeUserFromLibrary(Long libraryId, Long userId) {
        Long currentUserId = requireCurrentUserId();

        // Verify current user has access to library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, libraryId);

        UserLibrary ul = userLibraryRepository.findByUserIdAndLibraryId(userId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this library",
                        "USER_NOT_IN_LIBRARY"));

        userLibraryRepository.delete(ul);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void setUserPrimaryOrganization(Long userId, Long organizationId) {
        Long currentUserId = requireCurrentUserId();

        // User can only set their own primary organization
        if (!currentUserId.equals(userId)) {
            throw new ForbiddenException("Can only set your own primary organization");
        }

        // Verify user is member of this organization
        UserOrganization uo = userOrganizationRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this organization",
                        "USER_NOT_IN_ORGANIZATION"));

        // Only an ACTIVE membership can be the primary tenant for a user.
        if (!STATUS_ACTIVE.equals(uo.getStatus())) {
            throw new BusinessException("Membership in this organization is not active",
                    "ORGANIZATION_MEMBERSHIP_INACTIVE");
        }

        clearPrimaryOrganization(userId, organizationId);

        uo.setIsPrimary(true);
        userOrganizationRepository.save(uo);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void setUserPrimaryLibrary(Long userId, Long libraryId) {
        Long currentUserId = requireCurrentUserId();

        // User can only set their own primary library
        if (!currentUserId.equals(userId)) {
            throw new ForbiddenException("Can only set your own primary library");
        }

        // Verify user is member of this library
        UserLibrary ul = userLibraryRepository.findByUserIdAndLibraryId(userId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this library",
                        "USER_NOT_IN_LIBRARY"));

        // Only an ACTIVE membership can be the primary tenant for a user.
        if (!STATUS_ACTIVE.equals(ul.getStatus())) {
            throw new BusinessException("Membership in this library is not active",
                    "LIBRARY_MEMBERSHIP_INACTIVE");
        }

        clearPrimaryLibrary(userId, libraryId);

        ul.setIsPrimary(true);
        userLibraryRepository.save(ul);
    }

    private Long requireCurrentUserId() {
        return tenantAuthorizationService.getCurrentUserId()
                .orElseThrow(() -> new ForbiddenException("You do not have permission to perform this operation"));
    }

    private void requireUserExists(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }
    }

    /** A user has at most one primary organization; demote any other before promoting. */
    private void clearPrimaryOrganization(Long userId, Long keepOrganizationId) {
        userOrganizationRepository.findPrimaryByUserId(userId)
                .filter(existing -> !existing.getId().getOrganizationId().equals(keepOrganizationId))
                .ifPresent(existing -> {
                    existing.setIsPrimary(false);
                    userOrganizationRepository.save(existing);
                });
    }

    /** A user has at most one primary library; demote any other before promoting. */
    private void clearPrimaryLibrary(Long userId, Long keepLibraryId) {
        userLibraryRepository.findPrimaryByUserId(userId)
                .filter(existing -> !existing.getId().getLibraryId().equals(keepLibraryId))
                .ifPresent(existing -> {
                    existing.setIsPrimary(false);
                    userLibraryRepository.save(existing);
                });
    }
}
