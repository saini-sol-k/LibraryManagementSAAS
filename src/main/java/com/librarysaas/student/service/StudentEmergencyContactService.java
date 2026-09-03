package com.librarysaas.student.service;

import com.librarysaas.student.dto.EmergencyContactRequest;
import com.librarysaas.student.dto.EmergencyContactResponse;

import java.util.List;

/**
 * People to contact about a student.
 *
 * Every operation resolves the student's library and re-checks the caller's
 * membership of it. The address travels inline and is never referenced by id.
 * Deleting a contact removes the contact row only; the address row is preserved,
 * because proving exclusive ownership of a globally shared address is not
 * something this module can do safely.
 */
public interface StudentEmergencyContactService {

    List<EmergencyContactResponse> getStudentContacts(Long studentId);

    EmergencyContactResponse getContact(Long contactId);

    EmergencyContactResponse createContact(Long studentId, EmergencyContactRequest request);

    EmergencyContactResponse updateContact(Long contactId, EmergencyContactRequest request);

    /** Removes the contact. The address row it referenced is left intact. */
    void deleteContact(Long contactId);
}
