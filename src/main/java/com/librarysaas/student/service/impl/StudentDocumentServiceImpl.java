package com.librarysaas.student.service.impl;

import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.student.dto.StudentDocumentRequest;
import com.librarysaas.student.dto.StudentDocumentResponse;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.entity.StudentDocument;
import com.librarysaas.student.repository.StudentDocumentRepository;
import com.librarysaas.student.service.StudentDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Student document rules.
 *
 * Deliberately small, because the schema supports little: there is no unique
 * constraint on the type or the number, so duplicates are allowed and no
 * conflict is raised; ACTIVE is the only status V1 evidences, so no status
 * endpoint exists; and document_url is a path, so no binary content is handled.
 *
 * Reads use STUDENT_VIEW and writes STUDENT_UPDATE. No document-specific
 * permission exists in the schema and inventing one would need a migration to
 * seed it and its role grants.
 */
@Service
public class StudentDocumentServiceImpl implements StudentDocumentService {

    /** The column default, and the only value V1__initial_schema evidences. */
    static final String STATUS_ACTIVE = "ACTIVE";

    private final StudentDocumentRepository documentRepository;
    private final StudentProfileGuard guard;

    @Autowired
    public StudentDocumentServiceImpl(StudentDocumentRepository documentRepository,
                                      StudentProfileGuard guard) {
        this.documentRepository = documentRepository;
        this.guard = guard;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public List<StudentDocumentResponse> getStudentDocuments(Long studentId) {
        guard.requireAccessibleStudent(studentId);

        return documentRepository.findAllByStudent(studentId).stream()
                .map(StudentDocumentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentDocumentResponse getDocument(Long documentId) {
        return StudentDocumentResponse.from(requireDocument(documentId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentDocumentResponse createDocument(Long studentId, StudentDocumentRequest request) {
        Student student = guard.requireAccessibleStudent(studentId);

        LocalDateTime now = LocalDateTime.now();
        Long actor = guard.currentUserId();

        StudentDocument document = new StudentDocument();
        document.setStudent(student);
        document.setDocumentType(request.getDocumentType().trim().toUpperCase());
        document.setDocumentNumber(trimToNull(request.getDocumentNumber()));
        document.setDocumentUrl(trimToNull(request.getDocumentUrl()));
        document.setStatus(STATUS_ACTIVE);
        document.setCreatedAt(now);
        document.setCreatedBy(actor);
        document.setUpdatedAt(now);
        document.setUpdatedBy(actor);

        return StudentDocumentResponse.from(documentRepository.saveAndFlush(document));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentDocumentResponse updateDocument(Long documentId, StudentDocumentRequest request) {
        StudentDocument document = requireDocument(documentId);

        // The student is never reassigned: a document stays on the file it was
        // filed against, so there is no way to move one between students.
        document.setDocumentType(request.getDocumentType().trim().toUpperCase());
        document.setDocumentNumber(trimToNull(request.getDocumentNumber()));
        document.setDocumentUrl(trimToNull(request.getDocumentUrl()));
        document.setUpdatedAt(LocalDateTime.now());
        document.setUpdatedBy(guard.currentUserId());

        return StudentDocumentResponse.from(documentRepository.saveAndFlush(document));
    }

    /**
     * Resolves a document and authorises against the library of the student on
     * its own row, so a document id from another tenant is refused rather than
     * served. The document resolves first, so an unknown id is 404, not 403.
     */
    private StudentDocument requireDocument(Long documentId) {
        StudentDocument document = documentRepository.findByIdWithStudent(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found", "STUDENT_DOCUMENT_NOT_FOUND"));

        Student student = document.getStudent();
        if (student == null) {
            throw new ResourceNotFoundException("Document not found", "STUDENT_DOCUMENT_NOT_FOUND");
        }
        guard.requireLibraryAccess(guard.libraryIdOf(student));
        return document;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
