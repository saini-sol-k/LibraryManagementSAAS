package com.librarysaas.student.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.student.dto.EmergencyContactRequest;
import com.librarysaas.student.dto.EmergencyContactResponse;
import com.librarysaas.student.service.StudentEmergencyContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * People to contact about a student.
 *
 * The collection nests under the student; a single contact sits at top level.
 *
 * The address travels inline as a nested object and is never referenced by id.
 * The address table is global with no tenant column, so accepting an id would
 * let a contact be pointed at another tenant's address. Deleting a contact
 * removes only the contact: the address row is preserved, because it may be
 * shared and a wrong delete would lose data.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Student Emergency Contacts",
        description = "Next-of-kin contacts for a student, with an inline address")
public class StudentEmergencyContactController {

    private final StudentEmergencyContactService contactService;

    @Autowired
    public StudentEmergencyContactController(StudentEmergencyContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/students/{studentId}/emergency-contacts")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "List a student's emergency contacts",
            description = "Primary contact first. Requires STUDENT_VIEW and membership of the "
                    + "student's library.")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> listContacts(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Emergency contacts retrieved",
                contactService.getStudentContacts(studentId)));
    }

    @PostMapping("/students/{studentId}/emergency-contacts")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Add an emergency contact",
            description = "The address, if given, is created inline and belongs to this contact. "
                    + "An existing address cannot be attached by id. Marking the contact primary "
                    + "demotes the student's previous primary contact.")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> createContact(
            @PathVariable Long studentId,
            @Valid @RequestBody EmergencyContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Emergency contact added",
                        contactService.createContact(studentId, request)));
    }

    @GetMapping("/student-emergency-contacts/{contactId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "Get one emergency contact",
            description = "Requires membership of the library the student belongs to.")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> getContact(
            @PathVariable Long contactId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Emergency contact retrieved",
                contactService.getContact(contactId)));
    }

    @PutMapping("/student-emergency-contacts/{contactId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Update an emergency contact",
            description = "An address supplied here updates the contact's own address row in "
                    + "place. Omitting the address leaves the existing one unchanged rather than "
                    + "clearing it. The contact never moves to another student.")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> updateContact(
            @PathVariable Long contactId,
            @Valid @RequestBody EmergencyContactRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Emergency contact updated",
                contactService.updateContact(contactId, request)));
    }

    @DeleteMapping("/student-emergency-contacts/{contactId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Remove an emergency contact",
            description = "Removes the contact only. The address row it referenced is kept, "
                    + "because addresses are shared with other owners and exclusive ownership "
                    + "cannot be proven safely.")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable Long contactId) {
        contactService.deleteContact(contactId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Emergency contact removed", null));
    }
}
