package com.librarysaas.student.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.student.dto.StudentDocumentRequest;
import com.librarysaas.student.dto.StudentDocumentResponse;
import com.librarysaas.student.service.StudentDocumentService;
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
 * Documents held on a student's file.
 *
 * The collection nests under the student, and a single document sits at top
 * level because its id is globally unique, matching the shape used since Phase
 * 2D. Documents record a reference to where a file lives; this API never
 * receives or serves binary content.
 *
 * There is no delete and no status endpoint. ACTIVE is the only status
 * V1__initial_schema evidences, so there is no archived state to move a document
 * into and no safe basis for removing one.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Student Documents", description = "Document references held on a student's file")
public class StudentDocumentController {

    private final StudentDocumentService studentDocumentService;

    @Autowired
    public StudentDocumentController(StudentDocumentService studentDocumentService) {
        this.studentDocumentService = studentDocumentService;
    }

    @GetMapping("/students/{studentId}/documents")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "List a student's documents",
            description = "Newest first. Requires STUDENT_VIEW and membership of the student's "
                    + "library.")
    public ResponseEntity<ApiResponse<List<StudentDocumentResponse>>> listDocuments(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Documents retrieved",
                studentDocumentService.getStudentDocuments(studentId)));
    }

    @PostMapping("/students/{studentId}/documents")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Record a document on a student's file",
            description = "Stores the document type, number and a reference to where the file "
                    + "lives. The schema places no uniqueness on type or number, so a student may "
                    + "hold several of the same kind.")
    public ResponseEntity<ApiResponse<StudentDocumentResponse>> createDocument(
            @PathVariable Long studentId,
            @Valid @RequestBody StudentDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Document recorded",
                        studentDocumentService.createDocument(studentId, request)));
    }

    @GetMapping("/student-documents/{documentId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    @Operation(summary = "Get one document",
            description = "Requires membership of the library the student belongs to.")
    public ResponseEntity<ApiResponse<StudentDocumentResponse>> getDocument(
            @PathVariable Long documentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Document retrieved",
                studentDocumentService.getDocument(documentId)));
    }

    @PutMapping("/student-documents/{documentId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    @Operation(summary = "Update a document's details",
            description = "Edits the type, number and reference. The document stays on the file it "
                    + "was recorded against; it can never be moved to another student.")
    public ResponseEntity<ApiResponse<StudentDocumentResponse>> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody StudentDocumentRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Document updated",
                studentDocumentService.updateDocument(documentId, request)));
    }
}
