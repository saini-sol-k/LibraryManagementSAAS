package com.librarysaas.finance.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The tenant rules every financial service shares, in one place.
 *
 * Money is the worst place for two subtly different copies of an access check,
 * so fee plans, invoices and payments all come through here.
 *
 * The two halves are deliberately kept apart:
 *
 * <ul>
 *   <li><b>Caller authorisation</b> asks whether the signed-in user may act in
 *       this library, and delegates to TenantAuthorizationService. A super admin
 *       legitimately passes it for every library.</li>
 *   <li><b>Resource ownership</b> asks whether a target record actually belongs
 *       where the request claims. It is a plain field comparison that reads no
 *       security context, so it holds identically for every role, a super admin
 *       included. Phase 2C shipped a defect by conflating the two, and Phase 2E
 *       kept them separate; this does the same.</li>
 * </ul>
 */
@Component
public class FinanceTenantGuard {

    private final LibraryRepository libraryRepository;
    private final StudentRepository studentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public FinanceTenantGuard(LibraryRepository libraryRepository,
                              StudentRepository studentRepository,
                              TenantAuthorizationService tenantAuthorizationService) {
        this.libraryRepository = libraryRepository;
        this.studentRepository = studentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /**
     * Resolves the library and confirms the caller belongs to it. The library is
     * looked up first so an unknown id is 404 rather than 403.
     */
    public Library requireLibrary(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        requireLibraryAccess(libraryId);
        return library;
    }

    /**
     * Caller authorisation only. Converts an authorisation denial into
     * ForbiddenException and lets anything else propagate, so an infrastructure
     * failure still surfaces as an internal error rather than being disguised as
     * a permission problem.
     */
    public void requireLibraryAccess(Long libraryId) {
        try {
            tenantAuthorizationService.requireLibraryAccess(currentUserId(), libraryId);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
    }

    /**
     * Resource ownership: the student must actually belong to this library.
     *
     * Deliberately not TenantAuthorizationService.hasLibraryAccess, which
     * answers a question about the caller and short-circuits to true for a super
     * admin. Using that here would let a super admin bill a student of another
     * tenant. This compares the student's own stored library instead.
     */
    public Student requireStudentInLibrary(Long studentId, Long libraryId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        Long studentLibraryId = student.getLibrary() == null
                ? null
                : student.getLibrary().getLibraryId();

        if (studentLibraryId == null || !studentLibraryId.equals(libraryId)) {
            throw new BusinessException("Student does not belong to this library",
                    "STUDENT_NOT_IN_LIBRARY");
        }
        return student;
    }

    /** The library a student belongs to, for authorising a student-scoped read. */
    public Long libraryIdOf(Student student) {
        Long libraryId = student.getLibrary() == null ? null : student.getLibrary().getLibraryId();
        if (libraryId == null) {
            // A student with no library cannot be tenant-checked, so it must not
            // be readable rather than defaulting open.
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }
        return libraryId;
    }

    public Student requireStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));
    }

    public Long currentUserId() {
        return tenantAuthorizationService.getCurrentUserId().orElse(null);
    }
}
