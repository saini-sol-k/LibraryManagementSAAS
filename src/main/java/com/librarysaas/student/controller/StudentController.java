package com.librarysaas.student.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.student.dto.*;
import com.librarysaas.student.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Student APIs.
 *
 * Tenant scope is resolved from TenantContext (populated server-side from the JWT), with
 * libraryIdParam only as a fallback. StudentService repeats this resolution and validates
 * membership in every case, so the header can never widen access on its own.
 */
@RestController
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Students enrolled at a library")
public class StudentController {

    private final StudentService studentService;

    @Autowired
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    @Operation(summary = "Create a student",
            description = "The student is assigned to the caller's server-resolved library. "
                    + "Student code must be unique within that library.")
    public ResponseEntity<ApiResponse<StudentResponse>> create(@Valid @RequestBody StudentCreateRequest req) {
        StudentResponse resp = studentService.createStudent(req);
        return new ResponseEntity<>(new ApiResponse<>(true, "Student created", resp), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List students",
            description = "Paged list scoped to the caller's library, optionally filtered by "
                    + "name/mobile search and status.")
    public ResponseEntity<ApiResponse<Page<StudentSummaryResponse>>> list(
            @Parameter(description = "Matches first name, last name or mobile")
            @RequestParam(required = false) String search,
            @Parameter(description = "Exact student status, e.g. ACTIVE")
            @RequestParam(required = false) String status,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Fallback library scope, used only when no tenant is resolved from the token")
            @RequestParam(required = false) Long libraryIdParam
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) resolvedLibraryId = libraryIdParam;
        Page<StudentSummaryResponse> resp = studentService.getStudents(resolvedLibraryId, search, status, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Students retrieved", resp));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a student by id",
            description = "Returns 404 when the student does not exist, and 403 when it belongs "
                    + "to a library the caller is not a member of.")
    public ResponseEntity<ApiResponse<StudentResponse>> get(
            @PathVariable Long id,
            @Parameter(description = "Fallback library scope, used only when no tenant is resolved from the token")
            @RequestParam(required = false) Long libraryIdParam) {
        Long libraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (libraryId == null) libraryId = libraryIdParam;
        StudentResponse resp = studentService.getStudent(id, libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student retrieved", resp));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a student",
            description = "Scoped to the caller's library; a student in another library is not visible.")
    public ResponseEntity<ApiResponse<StudentResponse>> update(
            @PathVariable Long id,
            @Parameter(description = "Fallback library scope, used only when no tenant is resolved from the token")
            @RequestParam(required = false) Long libraryIdParam,
            @Valid @RequestBody StudentUpdateRequest req) {
        Long libraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (libraryId == null) libraryId = libraryIdParam;
        StudentResponse resp = studentService.updateStudent(id, libraryId, req);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student updated", resp));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a student",
            description = "Scoped to the caller's library; a student in another library is not visible.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @Parameter(description = "Fallback library scope, used only when no tenant is resolved from the token")
            @RequestParam(required = false) Long libraryIdParam) {
        Long libraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (libraryId == null) libraryId = libraryIdParam;
        studentService.deleteStudent(id, libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student deleted", null));
    }
}
