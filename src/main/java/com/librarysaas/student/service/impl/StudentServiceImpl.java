package com.librarysaas.student.service.impl;

import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.student.dto.*;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import com.librarysaas.student.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final LibraryRepository libraryRepository;
    private final com.librarysaas.security.TenantAuthorizationService tenantAuthorizationService;


    @Autowired
    public StudentServiceImpl(StudentRepository studentRepository, LibraryRepository libraryRepository, com.librarysaas.security.TenantAuthorizationService tenantAuthorizationService) {
        this.studentRepository = studentRepository;
        this.libraryRepository = libraryRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public StudentResponse createStudent(StudentCreateRequest req) {
        // Resolve the target library from authoritative sources (TenantContext or user's primary library).
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) {
            resolvedLibraryId = tenantAuthorizationService.findPrimaryLibraryIdForUser(currentUserId).orElse(null);
        }

        if (resolvedLibraryId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        Library lib = libraryRepository.findById(resolvedLibraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        // Authorization: ensure authenticated user has access to the resolved library
        tenantAuthorizationService.requireLibraryAccess(currentUserId, lib.getLibraryId());

        // Unique student code per library
        if (studentRepository.existsByLibraryLibraryIdAndStudentCode(resolvedLibraryId, req.getStudentCode())) {
            throw new ConflictException("Student code already exists in this library", "STUDENT_CODE_ALREADY_EXISTS");
        }

        Student s = new Student();
        s.setLibrary(lib);
        s.setStudentCode(req.getStudentCode());
        s.setFirstName(req.getFirstName());
        s.setLastName(req.getLastName());
        s.setMobile(req.getMobile());
        s.setEmail(req.getEmail());
        s.setDateOfBirth(req.getDateOfBirth());
        s.setGender(req.getGender());
        s.setJoiningDate(req.getJoiningDate());
        s.setStatus(req.getStatus());
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());

        Student saved = studentRepository.save(s);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentResponse getStudent(Long id, Long libraryId) {
        // Determine current user and validate library membership
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // If libraryId is null, prefer TenantContext or the user's primary library; still require membership
        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) resolvedLibraryId = libraryId;
        if (resolvedLibraryId == null) {
            // Try user's primary library
            resolvedLibraryId = tenantAuthorizationService.getCurrentUserId()
                    .flatMap(uid -> tenantAuthorizationService.findPrimaryLibraryIdForUser(uid))
                    .orElse(null);
        }

        if (resolvedLibraryId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // Fetch the student by id - throw 404 if not found
        Student s = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        Long studentLibId = s.getLibrary() != null ? s.getLibrary().getLibraryId() : null;
        
        // Guard against missing library association
        if (studentLibId == null) {
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }

        // Ensure the authenticated user has access to the student's library.
        // Only an authorization denial becomes a 403; database/system failures must
        // propagate so they are still reported as INTERNAL_ERROR instead of being
        // masked as a permission problem.
        try {
            tenantAuthorizationService.requireLibraryAccess(currentUserId, studentLibId);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        // If a resolvedLibraryId is present (from TenantContext or request), ensure it matches the student's library
        if (resolvedLibraryId != null && !resolvedLibraryId.equals(studentLibId)) {
            throw new ForbiddenException("You do not have access to this resource", "RESOURCE_ACCESS_DENIED");
        }

        return toResponse(s);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public Page<StudentSummaryResponse> getStudents(Long libraryId, String search, String status, Pageable pageable) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) resolvedLibraryId = libraryId;
        if (resolvedLibraryId == null) {
            resolvedLibraryId = tenantAuthorizationService.findPrimaryLibraryIdForUser(currentUserId).orElse(null);
        }

        if (resolvedLibraryId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        tenantAuthorizationService.requireLibraryAccess(currentUserId, resolvedLibraryId);

        Page<Student> page = studentRepository.search(resolvedLibraryId, status, search, pageable);
        return new PageImpl<>(page.getContent().stream().map(this::toSummary).collect(Collectors.toList()), pageable, page.getTotalElements());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentResponse updateStudent(Long id, Long libraryId, StudentUpdateRequest req) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) resolvedLibraryId = libraryId;
        if (resolvedLibraryId == null) {
            resolvedLibraryId = tenantAuthorizationService.findPrimaryLibraryIdForUser(currentUserId).orElse(null);
        }

        if (resolvedLibraryId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        tenantAuthorizationService.requireLibraryAccess(currentUserId, resolvedLibraryId);

        Student s = studentRepository.findByStudentIdAndLibraryLibraryId(id, resolvedLibraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        s.setFirstName(req.getFirstName());
        s.setLastName(req.getLastName());
        s.setMobile(req.getMobile());
        s.setEmail(req.getEmail());
        s.setDateOfBirth(req.getDateOfBirth());
        if (req.getJoiningDate() != null) s.setJoiningDate(req.getJoiningDate());
        if (req.getStatus() != null) s.setStatus(req.getStatus());
        s.setUpdatedAt(LocalDateTime.now());

        Student saved = studentRepository.save(s);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_DELETE')")
    public void deleteStudent(Long id, Long libraryId) {
        Long currentUserId = tenantAuthorizationService.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) resolvedLibraryId = libraryId;
        if (resolvedLibraryId == null) {
            resolvedLibraryId = tenantAuthorizationService.findPrimaryLibraryIdForUser(currentUserId).orElse(null);
        }

        if (resolvedLibraryId == null) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }

        tenantAuthorizationService.requireLibraryAccess(currentUserId, resolvedLibraryId);

        Student s = studentRepository.findByStudentIdAndLibraryLibraryId(id, resolvedLibraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));
        // Soft delete by status if required; for now perform hard delete
        studentRepository.delete(s);
    }

    private StudentResponse toResponse(Student s) {
        StudentResponse r = new StudentResponse();
        r.setId(s.getStudentId());
        r.setLibraryId(s.getLibrary() != null ? s.getLibrary().getLibraryId() : null);
        r.setStudentCode(s.getStudentCode());
        r.setFirstName(s.getFirstName());
        r.setLastName(s.getLastName());
        r.setMobile(s.getMobile());
        r.setEmail(s.getEmail());
        r.setDateOfBirth(s.getDateOfBirth());
        r.setGender(s.getGender());
        r.setJoiningDate(s.getJoiningDate());
        r.setStatus(s.getStatus());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }

    private StudentSummaryResponse toSummary(Student s) {
        StudentSummaryResponse r = new StudentSummaryResponse();
        r.setId(s.getStudentId());
        r.setLibraryId(s.getLibrary() != null ? s.getLibrary().getLibraryId() : null);
        r.setStudentCode(s.getStudentCode());
        r.setFirstName(s.getFirstName());
        r.setLastName(s.getLastName());
        r.setMobile(s.getMobile());
        r.setStatus(s.getStatus());
        return r;
    }
}
