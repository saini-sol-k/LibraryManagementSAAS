package com.librarysaas.membership.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.membership.dto.StudentMembershipRequest;
import com.librarysaas.membership.dto.StudentMembershipResponse;
import com.librarysaas.membership.dto.StudentMembershipStatusRequest;
import com.librarysaas.membership.dto.StudentMembershipUpdateRequest;
import com.librarysaas.membership.service.StudentMembershipService;
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
 * Student memberships: a student's dated entitlement to use a library.
 *
 * Distinct from {@code MembershipController}, which manages which staff users
 * belong to an organization or a library.
 *
 * The collection is nested under the library that owns it, so a create is
 * tenant-checkable from the path alone. Single-resource operations sit at the
 * top level because a membership id is globally unique, and the service
 * authorises against the library on the membership's own row. There is no
 * delete: ending a membership is a status change, which keeps the history and
 * the row that student_fee will reference.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Student Memberships",
        description = "Dated student memberships of a library, with renewal and status changes")
public class StudentMembershipController {

    private final StudentMembershipService studentMembershipService;

    @Autowired
    public StudentMembershipController(StudentMembershipService studentMembershipService) {
        this.studentMembershipService = studentMembershipService;
    }

    @GetMapping("/libraries/{libraryId}/student-memberships")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "List a library's student memberships",
            description = "Newest period first. Optional status filter: ACTIVE, EXPIRED or "
                    + "CANCELLED. Requires STUDENT_VIEW and membership of the library.")
    public ResponseEntity<ApiResponse<List<StudentMembershipResponse>>> listLibraryMemberships(
            @PathVariable Long libraryId,
            @RequestParam(required = false) String status) {
        List<StudentMembershipResponse> memberships =
                studentMembershipService.getLibraryMemberships(libraryId, status);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student memberships retrieved", memberships));
    }

    @PostMapping("/libraries/{libraryId}/student-memberships")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Create a student membership",
            description = "The student must belong to this library. The membership number must be "
                    + "free within the library, the end date must be after the start date, and the "
                    + "period must not overlap another active membership for that student.")
    public ResponseEntity<ApiResponse<StudentMembershipResponse>> createMembership(
            @PathVariable Long libraryId,
            @Valid @RequestBody StudentMembershipRequest request) {
        StudentMembershipResponse membership =
                studentMembershipService.createMembership(libraryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Student membership created", membership));
    }

    @GetMapping("/student-memberships/{membershipId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "Get one student membership",
            description = "Requires membership of the library the record belongs to.")
    public ResponseEntity<ApiResponse<StudentMembershipResponse>> getMembership(
            @PathVariable Long membershipId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Student membership retrieved",
                studentMembershipService.getMembership(membershipId)));
    }

    @PutMapping("/student-memberships/{membershipId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Update a student membership",
            description = "Edits the membership number, the period and the auto-renew flag in "
                    + "place. The student and the library never change. Status is changed through "
                    + "its own endpoint.")
    public ResponseEntity<ApiResponse<StudentMembershipResponse>> updateMembership(
            @PathVariable Long membershipId,
            @Valid @RequestBody StudentMembershipUpdateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Student membership updated",
                studentMembershipService.updateMembership(membershipId, request)));
    }

    @PutMapping("/student-memberships/{membershipId}/status")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Change a student membership's status",
            description = "ACTIVE, EXPIRED or CANCELLED. Cancelling keeps the row and its dates, "
                    + "so the history survives. Setting ACTIVE re-checks that the period does not "
                    + "overlap another active membership.")
    public ResponseEntity<ApiResponse<StudentMembershipResponse>> updateStatus(
            @PathVariable Long membershipId,
            @Valid @RequestBody StudentMembershipStatusRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Student membership status updated",
                studentMembershipService.updateStatus(membershipId, request.getStatus())));
    }

    @PostMapping("/student-memberships/{membershipId}/renew")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Renew a student membership",
            description = "Creates a successor membership for the same student and library over "
                    + "the supplied period, and closes the previous one as EXPIRED. The previous "
                    + "period is never rewritten.")
    public ResponseEntity<ApiResponse<StudentMembershipResponse>> renewMembership(
            @PathVariable Long membershipId,
            @Valid @RequestBody StudentMembershipUpdateRequest request) {
        StudentMembershipResponse successor =
                studentMembershipService.renewMembership(membershipId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Student membership renewed", successor));
    }

    @GetMapping("/students/{studentId}/memberships")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "List one student's memberships",
            description = "Full history, newest period first. Requires membership of the "
                    + "student's library.")
    public ResponseEntity<ApiResponse<List<StudentMembershipResponse>>> listStudentMemberships(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Student memberships retrieved",
                studentMembershipService.getStudentMemberships(studentId)));
    }
}
