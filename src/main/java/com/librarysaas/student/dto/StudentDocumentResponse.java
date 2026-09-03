package com.librarysaas.student.dto;

import com.librarysaas.student.entity.StudentDocument;

import java.time.LocalDateTime;

/**
 * One document on a student's file.
 *
 * Carries the student id for context but no other student detail, and no
 * created_by / updated_by: who filed a document is internal audit data the UI
 * has no use for.
 */
public class StudentDocumentResponse {

    private Long documentId;
    private Long studentId;
    private String documentType;
    private String documentNumber;
    private String documentUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentDocumentResponse from(StudentDocument document) {
        StudentDocumentResponse r = new StudentDocumentResponse();
        r.documentId = document.getDocumentId();
        if (document.getStudent() != null) {
            r.studentId = document.getStudent().getStudentId();
        }
        r.documentType = document.getDocumentType();
        r.documentNumber = document.getDocumentNumber();
        r.documentUrl = document.getDocumentUrl();
        r.status = document.getStatus();
        r.createdAt = document.getCreatedAt();
        r.updatedAt = document.getUpdatedAt();
        return r;
    }

    public Long getDocumentId() { return documentId; }
    public Long getStudentId() { return studentId; }
    public String getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getDocumentUrl() { return documentUrl; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
