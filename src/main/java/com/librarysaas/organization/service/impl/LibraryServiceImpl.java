package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.DuplicateResourceException;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LibraryServiceImpl implements LibraryService {

    private static final String STATUS_ACTIVE = OrganizationServiceImpl.STATUS_ACTIVE;
    private static final String STATUS_INACTIVE = OrganizationServiceImpl.STATUS_INACTIVE;

    private static final Set<String> ALLOWED_STATUSES =
            Set.of(STATUS_ACTIVE, STATUS_INACTIVE, OrganizationServiceImpl.STATUS_SUSPENDED);

    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    private static final String DEFAULT_CURRENCY = "INR";

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

        // A library may only be opened under an operating organization.
        if (!STATUS_ACTIVE.equals(org.getStatus())) {
            throw new BusinessException("Cannot create a library under an inactive organization",
                    "ORGANIZATION_INACTIVE");
        }

        String libraryCode = normalizeCode(request.getLibraryCode());
        if (libraryCode == null) {
            throw new BusinessException("Library code is required", "LIBRARY_CODE_REQUIRED");
        }

        // Library code is unique per organization (uk_library_org_code)
        if (libraryRepository.findByLibraryCodeAndOrganizationId(libraryCode, organizationId).isPresent()) {
            throw new DuplicateResourceException("Library code already exists in this organization",
                    "LIBRARY_CODE_ALREADY_EXISTS");
        }

        LocalTime openingTime = parseTime(request.getOpeningTime(), "openingTime");
        LocalTime closingTime = parseTime(request.getClosingTime(), "closingTime");
        requireValidOperatingHours(openingTime, closingTime);

        Library lib = new Library();
        lib.setOrganization(org);
        lib.setLibraryCode(libraryCode);
        lib.setName(request.getName());
        lib.setDescription(request.getDescription());
        lib.setEmail(request.getEmail());
        lib.setMobile(request.getMobile());
        lib.setStatus(STATUS_ACTIVE);
        lib.setTimezone(request.getTimezone() != null ? request.getTimezone() : DEFAULT_TIMEZONE);
        lib.setCurrency(request.getCurrency() != null ? request.getCurrency() : DEFAULT_CURRENCY);
        lib.setOpeningTime(openingTime);
        lib.setClosingTime(closingTime);

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

        List<Library> libraries = libraryRepository.findByOrganizationIdAndStatus(organizationId, STATUS_ACTIVE);
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
            String status = OrganizationServiceImpl.normalizeStatus(request.getStatus());
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new BusinessException("Invalid library status: " + request.getStatus(),
                        "INVALID_LIBRARY_STATUS");
            }
            // A library cannot be reactivated while its organization is not operating.
            if (STATUS_ACTIVE.equals(status) && !STATUS_ACTIVE.equals(lib.getStatus())) {
                requireActiveOrganization(lib);
            }
            lib.setStatus(status);
        }
        if (request.getTimezone() != null) {
            lib.setTimezone(request.getTimezone());
        }
        if (request.getCurrency() != null) {
            lib.setCurrency(request.getCurrency());
        }
        if (request.getOpeningTime() != null) {
            lib.setOpeningTime(parseTime(request.getOpeningTime(), "openingTime"));
        }
        if (request.getClosingTime() != null) {
            lib.setClosingTime(parseTime(request.getClosingTime(), "closingTime"));
        }
        // Validate against the resulting pair, not just the fields present in the request.
        requireValidOperatingHours(lib.getOpeningTime(), lib.getClosingTime());

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

        if (STATUS_INACTIVE.equals(lib.getStatus())) {
            throw new ConflictException("Library is already inactive", "LIBRARY_ALREADY_INACTIVE");
        }

        lib.setStatus(STATUS_INACTIVE);
        lib.setUpdatedAt(LocalDateTime.now());
        libraryRepository.save(lib);
    }

    private void requireActiveOrganization(Library lib) {
        Organization org = lib.getOrganization();
        if (org == null || !STATUS_ACTIVE.equals(org.getStatus())) {
            throw new BusinessException("Cannot activate a library under an inactive organization",
                    "ORGANIZATION_INACTIVE");
        }
    }

    /**
     * Operating hours are supplied as strings by the API layer. An unparseable value is a
     * client mistake, not a server fault, so it must not escape as a raw
     * {@link DateTimeParseException}.
     */
    private static LocalTime parseTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    "Invalid " + fieldName + ": expected HH:mm or HH:mm:ss but was '" + value + "'",
                    "INVALID_TIME_FORMAT");
        }
    }

    private static void requireValidOperatingHours(LocalTime openingTime, LocalTime closingTime) {
        if (openingTime != null && closingTime != null && !openingTime.isBefore(closingTime)) {
            throw new BusinessException("Opening time must be before closing time",
                    "INVALID_OPERATING_HOURS");
        }
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }
}
