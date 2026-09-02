package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.DuplicateResourceException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.dto.OrganizationCreateRequest;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.organization.dto.OrganizationUpdateRequest;
import com.librarysaas.organization.entity.Organization;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.service.OrganizationService;
import com.librarysaas.security.TenantAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_INACTIVE = "INACTIVE";
    static final String STATUS_SUSPENDED = "SUSPENDED";

    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE, STATUS_SUSPENDED);

    private final OrganizationRepository organizationRepository;
    private final LibraryRepository libraryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                 LibraryRepository libraryRepository,
                                 TenantAuthorizationService tenantAuthorizationService) {
        this.organizationRepository = organizationRepository;
        this.libraryRepository = libraryRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public OrganizationResponse createOrganization(OrganizationCreateRequest request) {
        // Only SUPER_ADMIN can create organizations
        if (!tenantAuthorizationService.isSuperAdmin()) {
            throw new ForbiddenException("Only SUPER_ADMIN can create organizations");
        }

        String organizationCode = normalizeCode(request.getOrganizationCode());
        if (organizationCode == null) {
            throw new BusinessException("Organization code is required", "ORGANIZATION_CODE_REQUIRED");
        }

        // Organization code is globally unique (uk_organization_code)
        if (organizationRepository.findByOrganizationCode(organizationCode).isPresent()) {
            throw new DuplicateResourceException("Organization code already exists",
                    "ORGANIZATION_CODE_ALREADY_EXISTS");
        }

        Organization org = new Organization();
        org.setOrganizationCode(organizationCode);
        org.setName(request.getName());
        org.setLegalName(request.getLegalName());
        org.setEmail(request.getEmail());
        org.setMobile(request.getMobile());
        org.setStatus(STATUS_ACTIVE);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());

        Organization saved = organizationRepository.save(org);
        return OrganizationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public OrganizationResponse getOrganization(Long organizationId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Verify user has access to this organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found", "ORGANIZATION_NOT_FOUND"));

        return OrganizationResponse.from(org);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public List<OrganizationResponse> listUserOrganizations() {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Only ACTIVE organizations reached through an ACTIVE membership are visible.
        return organizationRepository.findActiveByUserId(currentUserId).stream()
                .map(OrganizationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public OrganizationResponse updateOrganization(Long organizationId, OrganizationUpdateRequest request) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Verify user has access to this organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found", "ORGANIZATION_NOT_FOUND"));

        if (request.getName() != null) {
            org.setName(request.getName());
        }
        if (request.getLegalName() != null) {
            org.setLegalName(request.getLegalName());
        }
        if (request.getEmail() != null) {
            org.setEmail(request.getEmail());
        }
        if (request.getMobile() != null) {
            org.setMobile(request.getMobile());
        }
        if (request.getStatus() != null) {
            String status = normalizeStatus(request.getStatus());
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new BusinessException("Invalid organization status: " + request.getStatus(),
                        "INVALID_ORGANIZATION_STATUS");
            }
            // Deactivating through update must respect the same rule as deactivateOrganization.
            if (!STATUS_ACTIVE.equals(status) && STATUS_ACTIVE.equals(org.getStatus())) {
                requireNoActiveLibraries(organizationId);
            }
            org.setStatus(status);
        }

        org.setUpdatedAt(LocalDateTime.now());
        Organization saved = organizationRepository.save(org);
        return OrganizationResponse.from(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public void deactivateOrganization(Long organizationId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Only SUPER_ADMIN can deactivate organizations
        if (!tenantAuthorizationService.isSuperAdmin()) {
            throw new ForbiddenException("Only SUPER_ADMIN can deactivate organizations");
        }

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found", "ORGANIZATION_NOT_FOUND"));

        if (STATUS_INACTIVE.equals(org.getStatus())) {
            throw new ConflictException("Organization is already inactive", "ORGANIZATION_ALREADY_INACTIVE");
        }

        // An organization cannot be retired while it still owns operating libraries.
        requireNoActiveLibraries(organizationId);

        org.setStatus(STATUS_INACTIVE);
        org.setUpdatedAt(LocalDateTime.now());
        organizationRepository.save(org);
    }

    /**
     * Guards the organization lifecycle: deactivating an organization that still has
     * ACTIVE libraries would strand those libraries and every membership beneath them.
     */
    private void requireNoActiveLibraries(Long organizationId) {
        int activeLibraries = libraryRepository.findByOrganizationIdAndStatus(organizationId, STATUS_ACTIVE).size();
        if (activeLibraries > 0) {
            throw new BusinessException(
                    "Cannot deactivate an organization that still has " + activeLibraries + " active librar"
                            + (activeLibraries == 1 ? "y" : "ies"),
                    "ORGANIZATION_HAS_ACTIVE_LIBRARIES");
        }
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    static String normalizeStatus(String status) {
        return status == null ? null : status.trim().toUpperCase();
    }
}
