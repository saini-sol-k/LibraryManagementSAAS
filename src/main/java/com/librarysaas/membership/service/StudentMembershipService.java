package com.librarysaas.membership.service;

import com.librarysaas.membership.dto.StudentMembershipRequest;
import com.librarysaas.membership.dto.StudentMembershipResponse;
import com.librarysaas.membership.dto.StudentMembershipUpdateRequest;

import java.util.List;

/**
 * Student membership lifecycle within one library.
 *
 * Every operation is library scoped and re-checks the caller's membership of
 * that library. Nothing here deletes a row: a membership that ends is closed by
 * status so its history, and student_fee's reference to it, survive.
 */
public interface StudentMembershipService {

    /** Memberships of a library, newest period first. Optionally filtered by status. */
    List<StudentMembershipResponse> getLibraryMemberships(Long libraryId, String status);

    /** One student's membership history, newest period first. */
    List<StudentMembershipResponse> getStudentMemberships(Long studentId);

    StudentMembershipResponse getMembership(Long membershipId);

    StudentMembershipResponse createMembership(Long libraryId, StudentMembershipRequest request);

    /** Edits the number, period and auto-renew flag in place. */
    StudentMembershipResponse updateMembership(Long membershipId, StudentMembershipUpdateRequest request);

    StudentMembershipResponse updateStatus(Long membershipId, String status);

    /**
     * Creates a successor membership for the same student and library. The
     * membership being renewed is closed as EXPIRED rather than rewritten, so
     * the previous period stays on the record.
     */
    StudentMembershipResponse renewMembership(Long membershipId, StudentMembershipUpdateRequest request);
}
