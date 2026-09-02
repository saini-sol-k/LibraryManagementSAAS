package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.organization.dto.OrganizationCreateRequest;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.organization.dto.OrganizationUpdateRequest;
import com.librarysaas.organization.entity.Organization;
import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.repository.UserOrganizationRepository;
import com.librarysaas.organization.service.OrganizationService;
import com.librarysaas.security.TenantAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                 UserOrganizationRepository userOrganizationRepository,
                                 TenantAuthorizationService tenantAuthorizationService) {
        this.organizationRepository = organizationRepository;
        this.userOrganizationRepository = userOrganizationRepository;
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

        Organization org = new Organization();
        org.setOrganizationCode(request.getOrganizationCode());
        org.setName(request.getName());
        org.setLegalName(request.getLegalName());
        org.setEmail(request.getEmail());
        org.setMobile(request.getMobile());
        org.setStatus("ACTIVE");
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

        List<UserOrganization> userOrgs = userOrganizationRepository.findActiveByUserId(currentUserId);
        return userOrgs.stream()
                .map(uo -> OrganizationResponse.from(uo.getOrganization()))
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
            org.setStatus(request.getStatus());
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

        org.setStatus("INACTIVE");
        org.setUpdatedAt(LocalDateTime.now());
        organizationRepository.save(org);
    }
}
