package com.librarysaas.organization.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.dto.AddressRequest;
import com.librarysaas.organization.dto.AddressResponse;
import com.librarysaas.organization.entity.Address;
import com.librarysaas.organization.entity.LibraryAddress;
import com.librarysaas.organization.entity.OrganizationAddress;
import com.librarysaas.organization.repository.AddressRepository;
import com.librarysaas.organization.repository.LibraryAddressRepository;
import com.librarysaas.organization.repository.OrganizationAddressRepository;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.service.AddressService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.entity.StudentAddress;
import com.librarysaas.student.repository.StudentAddressRepository;
import com.librarysaas.student.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Address operations for organizations, libraries and students.
 *
 * There is no ADDRESS_* permission in the schema, so authorisation reuses the
 * owner's: changing an organization's address needs ORGANIZATION_UPDATE, a
 * library's needs LIBRARY_UPDATE and a student's needs STUDENT_UPDATE. Tenant
 * isolation reuses TenantAuthorizationService rather than introducing a second
 * resolution mechanism.
 */
@Service
public class AddressServiceImpl implements AddressService {

    /**
     * Accepted address types. Both sets are supersets of what
     * V1__initial_schema.sql already seeds - BUSINESS for organizations and
     * libraries, HOME for students - so existing rows stay editable and the
     * type the product's own data uses can still be created.
     */
    private static final Set<String> BUSINESS_TYPES = Set.of("BUSINESS", "BILLING", "SHIPPING", "OTHER");
    private static final Set<String> PERSONAL_TYPES =
            Set.of("HOME", "PERMANENT", "CURRENT", "CORRESPONDENCE", "OTHER");

    private static final String DEFAULT_BUSINESS_TYPE = "BUSINESS";
    private static final String DEFAULT_PERSONAL_TYPE = "HOME";

    private final AddressRepository addressRepository;
    private final OrganizationAddressRepository organizationAddressRepository;
    private final LibraryAddressRepository libraryAddressRepository;
    private final StudentAddressRepository studentAddressRepository;
    private final OrganizationRepository organizationRepository;
    private final LibraryRepository libraryRepository;
    private final StudentRepository studentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public AddressServiceImpl(
            AddressRepository addressRepository,
            OrganizationAddressRepository organizationAddressRepository,
            LibraryAddressRepository libraryAddressRepository,
            StudentAddressRepository studentAddressRepository,
            OrganizationRepository organizationRepository,
            LibraryRepository libraryRepository,
            StudentRepository studentRepository,
            TenantAuthorizationService tenantAuthorizationService) {
        this.addressRepository = addressRepository;
        this.organizationAddressRepository = organizationAddressRepository;
        this.libraryAddressRepository = libraryAddressRepository;
        this.studentAddressRepository = studentAddressRepository;
        this.organizationRepository = organizationRepository;
        this.libraryRepository = libraryRepository;
        this.studentRepository = studentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /* ------------------------------------------------------------ organization */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public List<AddressResponse> getOrganizationAddresses(Long organizationId) {
        requireOrganization(organizationId);
        return organizationAddressRepository.findByOrganizationId(organizationId).stream()
                .map(link -> AddressResponse.from(link.getAddress(),
                        link.getId().getAddressType(), link.getIsPrimary()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public AddressResponse addOrganizationAddress(Long organizationId, AddressRequest request) {
        requireOrganization(organizationId);

        String type = normaliseType(request.getAddressType(), DEFAULT_BUSINESS_TYPE, BUSINESS_TYPES);
        if (organizationAddressRepository.findByOrganizationIdAndAddressType(organizationId, type).isPresent()) {
            throw new ConflictException(
                    "This organization already has a " + type + " address", "ADDRESS_TYPE_ALREADY_EXISTS");
        }

        boolean primary = Boolean.TRUE.equals(request.getIsPrimary());
        if (primary) {
            organizationAddressRepository.findPrimaryByOrganizationId(organizationId)
                    .forEach(existing -> {
                        existing.setIsPrimary(false);
                        organizationAddressRepository.save(existing);
                    });
        }

        Address address = addressRepository.save(toNewAddress(request));

        OrganizationAddress link = new OrganizationAddress(organizationId, address.getAddressId(), type);
        link.setAddress(address);
        link.setIsPrimary(primary);
        link.setCreatedAt(LocalDateTime.now());
        organizationAddressRepository.save(link);

        return AddressResponse.from(address, type, primary);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public AddressResponse updateOrganizationAddress(Long organizationId, Long addressId, AddressRequest request) {
        requireOrganization(organizationId);

        OrganizationAddress link = organizationAddressRepository
                .findByOrganizationIdAndAddressId(organizationId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found for this organization", "ADDRESS_NOT_FOUND"));

        applyPrimary(request, () -> organizationAddressRepository.findPrimaryByOrganizationId(organizationId)
                .stream()
                .filter(other -> !other.getId().equals(link.getId()))
                .forEach(other -> {
                    other.setIsPrimary(false);
                    organizationAddressRepository.save(other);
                }), link::setIsPrimary);
        organizationAddressRepository.save(link);

        Address address = applyTo(link.getAddress(), request);
        addressRepository.save(address);

        return AddressResponse.from(address, link.getId().getAddressType(), link.getIsPrimary());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public void removeOrganizationAddress(Long organizationId, Long addressId) {
        requireOrganization(organizationId);

        OrganizationAddress link = organizationAddressRepository
                .findByOrganizationIdAndAddressId(organizationId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found for this organization", "ADDRESS_NOT_FOUND"));

        Address address = link.getAddress();
        organizationAddressRepository.delete(link);
        // The address row exists only for this owner, so remove it too rather
        // than leaving an orphan behind.
        addressRepository.delete(address);
    }

    /* ---------------------------------------------------------------- library */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public List<AddressResponse> getLibraryAddresses(Long libraryId) {
        requireLibrary(libraryId);
        return libraryAddressRepository.findByLibraryId(libraryId).stream()
                .map(link -> AddressResponse.from(link.getAddress(),
                        link.getId().getAddressType(), link.getIsPrimary()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('LIBRARY_UPDATE')")
    public AddressResponse addLibraryAddress(Long libraryId, AddressRequest request) {
        requireLibrary(libraryId);

        String type = normaliseType(request.getAddressType(), DEFAULT_BUSINESS_TYPE, BUSINESS_TYPES);
        if (libraryAddressRepository.findByLibraryIdAndAddressType(libraryId, type).isPresent()) {
            throw new ConflictException(
                    "This library already has a " + type + " address", "ADDRESS_TYPE_ALREADY_EXISTS");
        }

        boolean primary = Boolean.TRUE.equals(request.getIsPrimary());
        if (primary) {
            libraryAddressRepository.findPrimaryByLibraryId(libraryId).forEach(existing -> {
                existing.setIsPrimary(false);
                libraryAddressRepository.save(existing);
            });
        }

        Address address = addressRepository.save(toNewAddress(request));

        LibraryAddress link = new LibraryAddress(libraryId, address.getAddressId(), type);
        link.setAddress(address);
        link.setIsPrimary(primary);
        link.setCreatedAt(LocalDateTime.now());
        libraryAddressRepository.save(link);

        return AddressResponse.from(address, type, primary);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('LIBRARY_UPDATE')")
    public AddressResponse updateLibraryAddress(Long libraryId, Long addressId, AddressRequest request) {
        requireLibrary(libraryId);

        LibraryAddress link = libraryAddressRepository
                .findByLibraryIdAndAddressId(libraryId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found for this library", "ADDRESS_NOT_FOUND"));

        applyPrimary(request, () -> libraryAddressRepository.findPrimaryByLibraryId(libraryId).stream()
                .filter(other -> !other.getId().equals(link.getId()))
                .forEach(other -> {
                    other.setIsPrimary(false);
                    libraryAddressRepository.save(other);
                }), link::setIsPrimary);
        libraryAddressRepository.save(link);

        Address address = applyTo(link.getAddress(), request);
        addressRepository.save(address);

        return AddressResponse.from(address, link.getId().getAddressType(), link.getIsPrimary());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('LIBRARY_UPDATE')")
    public void removeLibraryAddress(Long libraryId, Long addressId) {
        requireLibrary(libraryId);

        LibraryAddress link = libraryAddressRepository
                .findByLibraryIdAndAddressId(libraryId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found for this library", "ADDRESS_NOT_FOUND"));

        Address address = link.getAddress();
        libraryAddressRepository.delete(link);
        addressRepository.delete(address);
    }

    /* ---------------------------------------------------------------- student */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public List<AddressResponse> getStudentAddresses(Long studentId) {
        requireStudent(studentId);
        return studentAddressRepository.findByStudentId(studentId).stream()
                .map(link -> AddressResponse.from(link.getAddress(),
                        link.getId().getAddressType(), link.getIsPrimary()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public AddressResponse addStudentAddress(Long studentId, AddressRequest request) {
        requireStudent(studentId);

        String type = normaliseType(request.getAddressType(), DEFAULT_PERSONAL_TYPE, PERSONAL_TYPES);
        if (studentAddressRepository.findByStudentIdAndAddressType(studentId, type).isPresent()) {
            throw new ConflictException(
                    "This student already has a " + type + " address", "ADDRESS_TYPE_ALREADY_EXISTS");
        }

        boolean primary = Boolean.TRUE.equals(request.getIsPrimary());
        if (primary) {
            studentAddressRepository.findPrimaryByStudentId(studentId).forEach(existing -> {
                existing.setIsPrimary(false);
                studentAddressRepository.save(existing);
            });
        }

        Address address = addressRepository.save(toNewAddress(request));

        StudentAddress link = new StudentAddress(studentId, address.getAddressId(), type);
        link.setAddress(address);
        link.setIsPrimary(primary);
        link.setCreatedAt(LocalDateTime.now());
        studentAddressRepository.save(link);

        return AddressResponse.from(address, type, primary);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public AddressResponse updateStudentAddress(Long studentId, Long addressId, AddressRequest request) {
        requireStudent(studentId);

        StudentAddress link = studentAddressRepository
                .findByStudentIdAndAddressId(studentId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found for this student", "ADDRESS_NOT_FOUND"));

        applyPrimary(request, () -> studentAddressRepository.findPrimaryByStudentId(studentId).stream()
                .filter(other -> !other.getId().equals(link.getId()))
                .forEach(other -> {
                    other.setIsPrimary(false);
                    studentAddressRepository.save(other);
                }), link::setIsPrimary);
        studentAddressRepository.save(link);

        Address address = applyTo(link.getAddress(), request);
        addressRepository.save(address);

        return AddressResponse.from(address, link.getId().getAddressType(), link.getIsPrimary());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public void removeStudentAddress(Long studentId, Long addressId) {
        requireStudent(studentId);

        StudentAddress link = studentAddressRepository
                .findByStudentIdAndAddressId(studentId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found for this student", "ADDRESS_NOT_FOUND"));

        Address address = link.getAddress();
        studentAddressRepository.delete(link);
        addressRepository.delete(address);
    }

    /* ------------------------------------------------------- tenant guards */

    private Long currentUserId() {
        return tenantAuthorizationService.getCurrentUserId()
                .orElseThrow(() -> new ForbiddenException(
                        "You do not have permission to perform this operation"));
    }

    private void requireOrganization(Long organizationId) {
        Long userId = currentUserId();
        requireAccess(() -> tenantAuthorizationService.requireOrganizationAccess(userId, organizationId));
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found", "ORGANIZATION_NOT_FOUND"));
    }

    private void requireLibrary(Long libraryId) {
        Long userId = currentUserId();
        requireAccess(() -> tenantAuthorizationService.requireLibraryAccess(userId, libraryId));
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));
        if (library.getOrganization() == null) {
            throw new BusinessException(
                    "Library is not linked to an organization", "LIBRARY_ORGANIZATION_MISSING");
        }
    }

    /**
     * A student is reached through the library that owns it, which is how the
     * rest of the application scopes students. The lookup happens first so a
     * missing student reports 404 rather than 403.
     */
    private void requireStudent(Long studentId) {
        Long userId = currentUserId();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        Long libraryId = student.getLibrary() != null ? student.getLibrary().getLibraryId() : null;
        if (libraryId == null) {
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }
        requireAccess(() -> tenantAuthorizationService.requireLibraryAccess(userId, libraryId));
    }

    /**
     * Translates the tenant service's AccessDeniedException into the project's
     * ForbiddenException. Only an authorisation denial is converted; anything
     * else propagates so infrastructure failures still surface as INTERNAL_ERROR.
     */
    private void requireAccess(Runnable check) {
        try {
            check.run();
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
    }

    /* -------------------------------------------------------------- mapping */

    private String normaliseType(String requested, String fallback, Set<String> allowed) {
        String type = requested == null || requested.isBlank()
                ? fallback
                : requested.trim().toUpperCase();
        if (!allowed.contains(type)) {
            throw new BusinessException(
                    "Invalid address type: " + requested + ". Allowed: " + String.join(", ", allowed),
                    "INVALID_ADDRESS_TYPE");
        }
        return type;
    }

    /** Runs the demotion of other primaries only when the request asks for it. */
    private void applyPrimary(AddressRequest request, Runnable demoteOthers,
                              java.util.function.Consumer<Boolean> setPrimary) {
        if (request.getIsPrimary() == null) return;
        if (Boolean.TRUE.equals(request.getIsPrimary())) demoteOthers.run();
        setPrimary.accept(request.getIsPrimary());
    }

    private Address toNewAddress(AddressRequest request) {
        Address address = new Address();
        applyTo(address, request);
        address.setCreatedAt(LocalDateTime.now());
        return address;
    }

    private Address applyTo(Address address, AddressRequest request) {
        address.setFirstName(trim(request.getFirstName()));
        address.setLastName(trim(request.getLastName()));
        address.setAddressLine1(trim(request.getAddressLine1()));
        address.setAddressLine2(trim(request.getAddressLine2()));
        address.setAddressLine3(trim(request.getAddressLine3()));
        address.setLandmark(trim(request.getLandmark()));
        address.setCity(trim(request.getCity()));
        address.setDistrict(trim(request.getDistrict()));
        address.setState(trim(request.getState()));
        address.setCountry(trim(request.getCountry()));
        address.setPostalCode(trim(request.getPostalCode()));
        address.setPhone1(trim(request.getPhone1()));
        address.setPhone2(trim(request.getPhone2()));
        address.setEmail(trim(request.getEmail()));
        address.setUpdatedAt(LocalDateTime.now());
        return address;
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
