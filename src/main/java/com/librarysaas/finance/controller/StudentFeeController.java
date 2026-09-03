package com.librarysaas.finance.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.finance.dto.StudentFeeRequest;
import com.librarysaas.finance.dto.StudentFeeResponse;
import com.librarysaas.finance.service.StudentFeeService;
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
 * Invoices raised against students.
 *
 * Nested under the library that owns them for the collection, top level for a
 * single invoice. There is no delete and no status endpoint: an invoice's status
 * follows from the payments recorded against it, so it is derived rather than
 * set.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Student Fees", description = "Invoices raised against students, with their balances")
public class StudentFeeController {

    private final StudentFeeService studentFeeService;

    @Autowired
    public StudentFeeController(StudentFeeService studentFeeService) {
        this.studentFeeService = studentFeeService;
    }

    @GetMapping("/libraries/{libraryId}/student-fees")
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    @Operation(summary = "List a library's invoices",
            description = "Newest due date first, each with what has been paid and what is still "
                    + "owed. Optional status filter: PENDING, PARTIALLY_PAID or PAID.")
    public ResponseEntity<ApiResponse<List<StudentFeeResponse>>> listLibraryFees(
            @PathVariable Long libraryId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Invoices retrieved",
                studentFeeService.getLibraryFees(libraryId, status)));
    }

    @PostMapping("/libraries/{libraryId}/student-fees")
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    @Operation(summary = "Raise an invoice",
            description = "The student must belong to this library. A fee plan, if named, must "
                    + "belong to this library and be active, and supplies the amount when none is "
                    + "given. A membership, if named, must belong to this student. The total is "
                    + "computed as amount minus discount plus tax and is never taken from the body.")
    public ResponseEntity<ApiResponse<StudentFeeResponse>> createFee(
            @PathVariable Long libraryId,
            @Valid @RequestBody StudentFeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Invoice created",
                        studentFeeService.createFee(libraryId, request)));
    }

    @GetMapping("/student-fees/{studentFeeId}")
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    @Operation(summary = "Get one invoice",
            description = "Includes the amount paid and the outstanding balance, both computed "
                    + "from the payments rather than stored.")
    public ResponseEntity<ApiResponse<StudentFeeResponse>> getFee(@PathVariable Long studentFeeId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Invoice retrieved",
                studentFeeService.getFee(studentFeeId)));
    }

    @GetMapping("/students/{studentId}/fees")
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    @Operation(summary = "List one student's invoices",
            description = "Newest due date first. Requires membership of the student's library.")
    public ResponseEntity<ApiResponse<List<StudentFeeResponse>>> listStudentFees(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Invoices retrieved",
                studentFeeService.getStudentFees(studentId)));
    }
}
