package com.librarysaas.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or update payload for a student document.
 *
 * studentId is deliberately absent: it comes from the path on create and from
 * the stored row on update, so a caller cannot move a document onto another
 * student by editing the body. Status is absent too, because ACTIVE is the only
 * value V1__initial_schema evidences.
 *
 * documentUrl is a reference to where the file lives. This API stores and
 * returns that reference and never handles binary content.
 */
public class StudentDocumentRequest {

    @NotBlank(message = "Document type is required")
    @Size(max = 50, message = "Document type must not exceed 50 characters")
    private String documentType;

    @Size(max = 100, message = "Document number must not exceed 100 characters")
    private String documentNumber;

    @Size(max = 500, message = "Document reference must not exceed 500 characters")
    private String documentUrl;

    public StudentDocumentRequest() {}

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
}
