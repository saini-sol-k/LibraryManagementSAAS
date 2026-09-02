package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.dto.LibraryCreateRequest;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.LibraryUpdateRequest;
import com.librarysaas.organization.entity.Organization;
import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.repository.UserLibraryRepository;
import com.librarysaas.organization.service.LibraryService;
import com.librarysaas.security.TenantAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LibraryServiceImpl implements LibraryService {

    private final LibraryRepository libraryRepository;
    private final OrganizationRepository organizationRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public LibraryServiceImpl(LibraryRepository libraryRepository,
                            OrganizationRepository organizationRepository,
                            UserLibraryRepository userLibraryRepository,
                            TenantAuthorizationService tenantAuthorizationService) {
        this.libraryRepository = libraryRepository;
        this.organizationRepository = organizationRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('LIBRARY_CREATE')")
    public LibraryResponse createLibrary(Long organizationId, LibraryCreateRequest request) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Verify user has access to this organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found", "ORGANIZATION_NOT_FOUND"));

        Library lib = new Library();
        lib.setOrganization(org);
        lib.setLibraryCode(request.getLibraryCode());
        lib.setName(request.getName());
        lib.setDescription(request.getDescription());
        lib.setEmail(request.getEmail());
        lib.setMobile(request.getMobile());
        lib.setStatus("ACTIVE");
        lib.setTimezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Kolkata");
        lib.setCurrency(request.getCurrency() != null ? request.getCurrency() : "INR");
        
        if (request.getOpeningTime() != null) {
            lib.setOpeningTime(LocalTime.parse(request.getOpeningTime()));
        }
        if (request.getClosingTime() != null) {
            lib.setClosingTime(LocalTime.parse(request.getClosingTime()));
        }

        lib.setCreatedAt(LocalDateTime.now());
        lib.setUpdatedAt(LocalDateTime.now());

        Library saved = libraryRepository.save(lib);
        return LibraryResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public LibraryResponse getLibrary(Long libraryId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Verify user has access to this library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, libraryId);

        Library lib = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        return LibraryResponse.from(lib);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public List<LibraryResponse> listLibrariesForUser() {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        List<UserLibrary> userLibs = userLibraryRepository.findActiveByUserId(currentUserId);
        return userLibs.stream()
                .map(ul -> LibraryResponse.from(ul.getLibrary()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public List<LibraryResponse> listLibrariesByOrganization(Long organizationId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Verify user has access to this organization
        tenantAuthorizationService.requireOrganizationAccess(currentUserId, organizationId);

        List<Library> libraries = libraryRepository.findByOrganizationIdAndStatus(organizationId, "ACTIVE");
        return libraries.stream()
                .map(LibraryResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('LIBRARY_UPDATE')")
    public LibraryResponse updateLibrary(Long libraryId, LibraryUpdateRequest request) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Verify user has access to this library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, libraryId);

        Library lib = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        if (request.getName() != null) {
            lib.setName(request.getName());
        }
        if (request.getDescription() != null) {
            lib.setDescription(request.getDescription());
        }
        if (request.getEmail() != null) {
            lib.setEmail(request.getEmail());
        }
        if (request.getMobile() != null) {
            lib.setMobile(request.getMobile());
        }
        if (request.getStatus() != null) {
            lib.setStatus(request.getStatus());
        }
        if (request.getTimezone() != null) {
            lib.setTimezone(request.getTimezone());
        }
        if (request.getCurrency() != null) {
            lib.setCurrency(request.getCurrency());
        }
        if (request.getOpeningTime() != null) {
            lib.setOpeningTime(LocalTime.parse(request.getOpeningTime()));
        }
        if (request.getClosingTime() != null) {
            lib.setClosingTime(LocalTime.parse(request.getClosingTime()));
        }

        lib.setUpdatedAt(LocalDateTime.now());
        Library saved = libraryRepository.save(lib);
        return LibraryResponse.from(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('LIBRARY_STATUS_UPDATE')")
    public void deactivateLibrary(Long libraryId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        Library lib = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        // Verify user has access to the library's organization
        if (lib.getOrganization() != null) {
            tenantAuthorizationService.requireOrganizationAccess(currentUserId, lib.getOrganization().getOrganizationId());
        }

        lib.setStatus("INACTIVE");
        lib.setUpdatedAt(LocalDateTime.now());
        libraryRepository.save(lib);
    }
}
