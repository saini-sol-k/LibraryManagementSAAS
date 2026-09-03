package com.librarysaas.student.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The tenant rules the student-profile services share.
 *
 * Neither student_document nor student_emergency_contact carries a library
 * column, so the tenant is reached through the student. That makes the two
 * halves of the check especially easy to conflate, so they are kept apart here
 * exactly as FinanceTenantGuard does:
 *
 * <ul>
 *   <li><b>Caller authorisation</b> asks whether the signed-in user may act in a
 *       library and delegates to TenantAuthorizationService. A super admin
 *       legitimately passes it everywhere.</li>
 *   <li><b>Resource ownership</b> asks which library a student actually belongs
 *       to, by reading the stored relationship. It consults no security context,
 *       so it holds identically for every role, a super admin included. Phase 2C
 *       shipped a defect by conflating the two; this does not.</li>
 * </ul>
 */
@Component
public class StudentProfileGuard {

    private final StudentRepository studentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public StudentProfileGuard(StudentRepository studentRepository,
                               TenantAuthorizationService tenantAuthorizationService) {
        this.studentRepository = studentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /**
     * Resolves a student and confirms the caller belongs to that student's
     * library. The student is looked up first so an unknown id is 404, not 403.
     */
    public Student requireAccessibleStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        requireLibraryAccess(libraryIdOf(student));
        return student;
    }

    /**
     * The library a student belongs to, read from the stored relationship.
     *
     * A student with no library cannot be tenant-checked, so it is reported as
     * absent rather than defaulting open.
     */
    public Long libraryIdOf(Student student) {
        Long libraryId = student.getLibrary() == null ? null : student.getLibrary().getLibraryId();
        if (libraryId == null) {
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }
        return libraryId;
    }

    /**
     * Ownership check for a record reached by its own id: the student it belongs
     * to must be the one the caller may act on. Used to state plainly that a
     * profile record from another library is refused, whatever the caller's
     * privileges.
     */
    public void requireStudentInLibrary(Student student, Long libraryId) {
        Long studentLibraryId = student.getLibrary() == null
                ? null
                : student.getLibrary().getLibraryId();

        if (studentLibraryId == null || !studentLibraryId.equals(libraryId)) {
            throw new BusinessException("Student does not belong to this library",
                    "STUDENT_NOT_IN_LIBRARY");
        }
    }

    /**
     * Caller authorisation only. Converts an authorisation denial into
     * ForbiddenException and lets anything else propagate, so an infrastructure
     * failure still surfaces as an internal error rather than a permission one.
     */
    public void requireLibraryAccess(Long libraryId) {
        try {
            tenantAuthorizationService.requireLibraryAccess(currentUserId(), libraryId);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
    }

    public Long currentUserId() {
        return tenantAuthorizationService.getCurrentUserId().orElse(null);
    }
}
