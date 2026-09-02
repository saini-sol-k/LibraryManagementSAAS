package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.entity.Organization;
import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.entity.UserLibraryKey;
import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.organization.entity.UserOrganizationKey;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.repository.UserLibraryRepository;
import com.librarysaas.organization.repository.UserOrganizationRepository;
import com.librarysaas.organization.service.UserManagementService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.security.model.User;
import com.librarysaas.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class UserManagementServiceImpl implements UserManagementService {

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
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new AccessDeniedException("Access denied");
        }

        // Verify current user has access to organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        // Verify organization exists
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        // Verify target user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if already a member
        if (userOrganizationRepository.findByUserIdAndOrganizationId(userId, organizationId).isPresent()) {
            throw new ConflictException("User is already a member of this organization");
        }

        // If setting as primary, unset any other primary
        if (isPrimary) {
            userOrganizationRepository.findPrimaryByUserId(userId)
                    .ifPresent(uo -> {
                        uo.setIsPrimary(false);
                        userOrganizationRepository.save(uo);
                    });
        }

        UserOrganization uo = new UserOrganization(userId, organizationId);
        uo.setIsPrimary(isPrimary != null ? isPrimary : false);
        uo.setStatus("ACTIVE");
        uo.setJoinedAt(LocalDateTime.now());
        uo.setCreatedAt(LocalDateTime.now());

        userOrganizationRepository.save(uo);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void removeUserFromOrganization(Long organizationId, Long userId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new AccessDeniedException("Access denied");
        }

        // Verify current user has access to organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        UserOrganization uo = userOrganizationRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this organization"));

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
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new AccessDeniedException("Access denied");
        }

        // Verify library exists
        Library lib = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found"));

        // Verify current user has access to library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, libraryId);

        // Verify target user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // CRITICAL: Verify target user is a member of the library's organization
        if (!tenantAuthorizationService.hasOrganizationAccess(userId, lib.getOrganization().getOrganizationId())) {
            throw new AccessDeniedException("User is not a member of the library's organization");
        }

        // Check if already a member
        if (userLibraryRepository.findByUserIdAndLibraryId(userId, libraryId).isPresent()) {
            throw new ConflictException("User is already a member of this library");
        }

        // If setting as primary, unset any other primary
        if (isPrimary) {
            userLibraryRepository.findPrimaryByUserId(userId)
                    .ifPresent(ul -> {
                        ul.setIsPrimary(false);
                        userLibraryRepository.save(ul);
                    });
        }

        UserLibrary ul = new UserLibrary(userId, libraryId);
        ul.setIsPrimary(isPrimary != null ? isPrimary : false);
        ul.setStatus("ACTIVE");
        ul.setJoinedAt(LocalDateTime.now());
        ul.setCreatedAt(LocalDateTime.now());

        userLibraryRepository.save(ul);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void removeUserFromLibrary(Long libraryId, Long userId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new AccessDeniedException("Access denied");
        }

        // Verify current user has access to library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, libraryId);

        UserLibrary ul = userLibraryRepository.findByUserIdAndLibraryId(userId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this library"));

        userLibraryRepository.delete(ul);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void setUserPrimaryOrganization(Long userId, Long organizationId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new AccessDeniedException("Access denied");
        }

        // User can only set their own primary organization
        if (!currentUserId.equals(userId)) {
            throw new AccessDeniedException("Can only set your own primary organization");
        }

        // Verify user is member of this organization
        UserOrganization uo = userOrganizationRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this organization"));

        // Unset any other primary
        userOrganizationRepository.findPrimaryByUserId(userId)
                .ifPresent(existing -> {
                    if (!existing.getId().getOrganizationId().equals(organizationId)) {
                        existing.setIsPrimary(false);
                        userOrganizationRepository.save(existing);
                    }
                });

        // Set this as primary
        uo.setIsPrimary(true);
        userOrganizationRepository.save(uo);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void setUserPrimaryLibrary(Long userId, Long libraryId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new AccessDeniedException("Access denied");
        }

        // User can only set their own primary library
        if (!currentUserId.equals(userId)) {
            throw new AccessDeniedException("Can only set your own primary library");
        }

        // Verify user is member of this library
        UserLibrary ul = userLibraryRepository.findByUserIdAndLibraryId(userId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this library"));

        // Unset any other primary
        userLibraryRepository.findPrimaryByUserId(userId)
                .ifPresent(existing -> {
                    if (!existing.getId().getLibraryId().equals(libraryId)) {
                        existing.setIsPrimary(false);
                        userLibraryRepository.save(existing);
                    }
                });

        // Set this as primary
        ul.setIsPrimary(true);
        userLibraryRepository.save(ul);
    }
}
