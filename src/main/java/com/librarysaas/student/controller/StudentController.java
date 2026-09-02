package com.librarysaas.student.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.student.dto.*;
import com.librarysaas.student.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    @Autowired
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentResponse>> create(@Valid @RequestBody StudentCreateRequest req) {
        StudentResponse resp = studentService.createStudent(req);
        return new ResponseEntity<>(new ApiResponse<>(true, "Student created", resp), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StudentSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long libraryIdParam
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Long resolvedLibraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (resolvedLibraryId == null) resolvedLibraryId = libraryIdParam;
        Page<StudentSummaryResponse> resp = studentService.getStudents(resolvedLibraryId, search, status, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Students retrieved", resp));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> get(@PathVariable Long id, @RequestParam(required = false) Long libraryIdParam) {
        Long libraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (libraryId == null) libraryId = libraryIdParam;
        StudentResponse resp = studentService.getStudent(id, libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student retrieved", resp));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> update(@PathVariable Long id, @RequestParam(required = false) Long libraryIdParam, @Valid @RequestBody StudentUpdateRequest req) {
        Long libraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (libraryId == null) libraryId = libraryIdParam;
        StudentResponse resp = studentService.updateStudent(id, libraryId, req);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student updated", resp));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @RequestParam(required = false) Long libraryIdParam) {
        Long libraryId = com.librarysaas.security.TenantContext.getLibraryId();
        if (libraryId == null) libraryId = libraryIdParam;
        studentService.deleteStudent(id, libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Student deleted", null));
    }
}
