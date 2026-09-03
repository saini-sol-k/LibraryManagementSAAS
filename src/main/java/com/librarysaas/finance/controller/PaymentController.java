package com.librarysaas.finance.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.finance.dto.PaymentRequest;
import com.librarysaas.finance.dto.PaymentResponse;
import com.librarysaas.finance.service.PaymentService;
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
 * Money received from students.
 *
 * A payment is created under the invoice it settles, which is what makes the
 * balance check unavoidable and lets the student and library be inherited rather
 * than supplied. There is no update and no delete: the payment table has no
 * updated_at, so a change would leave no trace. PAYMENT_REFUND exists as a
 * permission but a reversal has nowhere to live in this schema, so no refund
 * endpoint is offered.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Payments", description = "Payments received against student invoices")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/libraries/{libraryId}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    @Operation(summary = "List a library's payments",
            description = "Most recent first. Requires PAYMENT_VIEW and membership of the library.")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listLibraryPayments(
            @PathVariable Long libraryId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payments retrieved",
                paymentService.getLibraryPayments(libraryId)));
    }

    @PostMapping("/student-fees/{studentFeeId}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    @Operation(summary = "Record a payment against an invoice",
            description = "The receipt number must be free within the library, the amount must be "
                    + "greater than zero, and it may not exceed the invoice's outstanding balance: "
                    + "the schema has no way to represent an overpayment. The invoice is locked "
                    + "while the balance is checked, so two simultaneous payments cannot both be "
                    + "accepted. The invoice status is brought up to date in the same transaction.")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @PathVariable Long studentFeeId,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Payment recorded",
                        paymentService.recordPayment(studentFeeId, request)));
    }

    @GetMapping("/student-fees/{studentFeeId}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    @Operation(summary = "List the payments against one invoice",
            description = "Most recent first.")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listFeePayments(
            @PathVariable Long studentFeeId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payments retrieved",
                paymentService.getFeePayments(studentFeeId)));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    @Operation(summary = "Get one payment",
            description = "Requires membership of the library the payment belongs to.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment retrieved",
                paymentService.getPayment(paymentId)));
    }

    @GetMapping("/students/{studentId}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    @Operation(summary = "List one student's payments",
            description = "Most recent first. Requires membership of the student's library.")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listStudentPayments(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payments retrieved",
                paymentService.getStudentPayments(studentId)));
    }
}
