package com.librarysaas.student.service.impl;

import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.organization.entity.Address;
import com.librarysaas.organization.repository.AddressRepository;
import com.librarysaas.student.dto.EmergencyContactAddressRequest;
import com.librarysaas.student.dto.EmergencyContactRequest;
import com.librarysaas.student.dto.EmergencyContactResponse;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.entity.StudentEmergencyContact;
import com.librarysaas.student.repository.StudentEmergencyContactRepository;
import com.librarysaas.student.service.StudentEmergencyContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Emergency contact rules.
 *
 * Two decisions shape this class:
 *
 * <ul>
 *   <li><b>The address is created inline, never referenced.</b> {@code address}
 *       is a global table with no tenant column, so an id accepted from a caller
 *       could point at another tenant's row. This service creates the address on
 *       first use, inside the same transaction as the contact, so a contact can
 *       only ever reference an address this service made for it. There is no
 *       code path that attaches an existing address.</li>
 *   <li><b>An address row is never edited or deleted.</b> Address rows are
 *       genuinely shared: in V1's own seed, address 4 is the home address of
 *       students 1 and 3 and also the address of two emergency contacts. So an
 *       edit writes a new row and re-points the contact, and a delete removes
 *       only the contact. Mutating or removing a shared row would corrupt or
 *       lose another owner's data; an extra row costs nothing.</li>
 * </ul>
 *
 * The schema places no unique constraint on isPrimary, so the
 * one-primary-per-student rule is enforced here, following the pattern the staff
 * membership service already uses for a primary tenant.
 */
@Service
public class StudentEmergencyContactServiceImpl implements StudentEmergencyContactService {

    /** No contact can carry this id, so it excludes nothing from a search. */
    private static final long NO_EXCLUSION = -1L;

    private final StudentEmergencyContactRepository contactRepository;
    private final AddressRepository addressRepository;
    private final StudentProfileGuard guard;

    @Autowired
    public StudentEmergencyContactServiceImpl(StudentEmergencyContactRepository contactRepository,
                                              AddressRepository addressRepository,
                                              StudentProfileGuard guard) {
        this.contactRepository = contactRepository;
        this.addressRepository = addressRepository;
        this.guard = guard;
    }

    /* ---------------------------------------------------------------- reads */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public List<EmergencyContactResponse> getStudentContacts(Long studentId) {
        guard.requireAccessibleStudent(studentId);

        return contactRepository.findAllByStudent(studentId).stream()
                .map(EmergencyContactResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public EmergencyContactResponse getContact(Long contactId) {
        return EmergencyContactResponse.from(requireContact(contactId));
    }

    /* --------------------------------------------------------------- writes */

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public EmergencyContactResponse createContact(Long studentId, EmergencyContactRequest request) {
        Student student = guard.requireAccessibleStudent(studentId);

        boolean primary = Boolean.TRUE.equals(request.getIsPrimary());
        if (primary) {
            demoteExistingPrimary(studentId, NO_EXCLUSION);
        }

        LocalDateTime now = LocalDateTime.now();

        StudentEmergencyContact contact = new StudentEmergencyContact();
        contact.setStudent(student);
        applyFields(contact, request);
        contact.setIsPrimary(primary);
        contact.setAddress(request.getAddress() == null ? null : createAddress(request.getAddress()));
        contact.setCreatedAt(now);
        contact.setUpdatedAt(now);

        return EmergencyContactResponse.from(contactRepository.saveAndFlush(contact));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public EmergencyContactResponse updateContact(Long contactId, EmergencyContactRequest request) {
        StudentEmergencyContact contact = requireContact(contactId);
        Long studentId = contact.getStudent().getStudentId();

        boolean primary = Boolean.TRUE.equals(request.getIsPrimary());
        if (primary) {
            demoteExistingPrimary(studentId, contactId);
        }

        applyFields(contact, request);
        contact.setIsPrimary(primary);

        // An omitted address means "unchanged", not "remove": a missing field
        // must never silently discard an address the contact already has.
        //
        // A supplied address always writes a NEW row and re-points the contact,
        // rather than editing the row in place. Address rows are genuinely
        // shared: in V1's own seed, address 4 is both the home address of two
        // students and the address of two emergency contacts. Editing it in
        // place to change one contact would silently rewrite a student's home
        // address and another contact's too. Writing a new row cannot affect any
        // other holder. The previous row is left alone for the same reason
        // deletion leaves it alone.
        EmergencyContactAddressRequest addressRequest = request.getAddress();
        if (addressRequest != null) {
            contact.setAddress(createAddress(addressRequest));
        }

        contact.setUpdatedAt(LocalDateTime.now());
        return EmergencyContactResponse.from(contactRepository.saveAndFlush(contact));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public void deleteContact(Long contactId) {
        StudentEmergencyContact contact = requireContact(contactId);

        // Only the contact goes. The address row stays, deliberately: see the
        // class comment. An orphan costs nothing; a wrong delete loses data.
        contactRepository.delete(contact);
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves a contact and authorises against the library of the student on
     * its own row, so a contact id from another tenant is refused rather than
     * served. The contact resolves first, so an unknown id is 404, not 403.
     */
    private StudentEmergencyContact requireContact(Long contactId) {
        StudentEmergencyContact contact = contactRepository.findByIdWithDetail(contactId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Emergency contact not found", "EMERGENCY_CONTACT_NOT_FOUND"));

        Student student = contact.getStudent();
        if (student == null) {
            throw new ResourceNotFoundException(
                    "Emergency contact not found", "EMERGENCY_CONTACT_NOT_FOUND");
        }
        guard.requireLibraryAccess(guard.libraryIdOf(student));
        return contact;
    }

    /** A student has at most one primary contact; demote any other before promoting. */
    private void demoteExistingPrimary(Long studentId, Long excludedContactId) {
        List<StudentEmergencyContact> current =
                contactRepository.findPrimaryByStudentExcluding(studentId, excludedContactId);

        for (StudentEmergencyContact existing : current) {
            existing.setIsPrimary(false);
            existing.setUpdatedAt(LocalDateTime.now());
            contactRepository.save(existing);
        }
    }

    private void applyFields(StudentEmergencyContact contact, EmergencyContactRequest request) {
        contact.setFirstName(request.getFirstName().trim());
        contact.setLastName(trimToNull(request.getLastName()));
        contact.setRelationship(trimToNull(request.getRelationship()));
        contact.setMobile(trimToNull(request.getMobile()));
        contact.setEmail(trimToNull(request.getEmail()));
    }

    /** Creates a fresh address row owned, in practice, by this contact alone. */
    private Address createAddress(EmergencyContactAddressRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Long actor = guard.currentUserId();

        Address address = new Address();
        applyAddress(address, request);
        address.setCreatedAt(now);
        address.setCreatedBy(actor);
        address.setUpdatedAt(now);
        address.setUpdatedBy(actor);

        return addressRepository.saveAndFlush(address);
    }

    private void applyAddress(Address address, EmergencyContactAddressRequest request) {
        address.setAddressLine1(request.getAddressLine1().trim());
        address.setAddressLine2(trimToNull(request.getAddressLine2()));
        address.setAddressLine3(trimToNull(request.getAddressLine3()));
        address.setLandmark(trimToNull(request.getLandmark()));
        address.setCity(request.getCity().trim());
        address.setDistrict(trimToNull(request.getDistrict()));
        address.setState(request.getState().trim());
        address.setCountry(trimToNull(request.getCountry()));
        address.setPostalCode(request.getPostalCode().trim());
        address.setPhone1(trimToNull(request.getPhone1()));
        address.setPhone2(trimToNull(request.getPhone2()));
        address.setEmail(trimToNull(request.getEmail()));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
