package com.librarysaas.finance.service;

import com.librarysaas.finance.dto.PaymentRequest;
import com.librarysaas.finance.dto.PaymentResponse;

import java.util.List;

/**
 * Money received against invoices.
 *
 * Payments are append-only: they are recorded and read, never edited or
 * removed, because the payment table has no updated_at to record a change
 * against. Recording one also brings its invoice status up to date.
 */
public interface PaymentService {

    List<PaymentResponse> getLibraryPayments(Long libraryId);

    List<PaymentResponse> getStudentPayments(Long studentId);

    List<PaymentResponse> getFeePayments(Long studentFeeId);

    PaymentResponse getPayment(Long paymentId);

    /** Records a payment against one invoice, under a lock on that invoice. */
    PaymentResponse recordPayment(Long studentFeeId, PaymentRequest request);
}
