package com.librarysaas.student.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A document held on a student's file, such as an identity card.
 *
 * {@code documentUrl} is a reference to where the file lives, not the file
 * itself: V1__initial_schema stores a path like {@code students/1/aadhaar.pdf}
 * in a VARCHAR(500). Nothing in this module reads, writes or serves binary
 * content.
 *
 * The schema places no unique constraint on the type or the number, so a student
 * may legitimately hold several documents of the same kind, and no uniqueness is
 * enforced here either.
 */
@Entity
@Table(name = "student_document")
public class StudentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    /** A path or locator, never binary content. */
    @Column(name = "document_url", length = 500)
    private String documentUrl;

    /**
     * ACTIVE is the column default and the only value V1 evidences, so it is the
     * only value this module ever writes.
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
