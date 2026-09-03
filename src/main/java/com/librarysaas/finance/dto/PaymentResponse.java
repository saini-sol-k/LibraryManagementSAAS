package com.librarysaas.finance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import com.librarysaas.finance.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One payment. Carries just enough of the student and invoice to label a row,
 * following the pattern the seat and attendance responses use.
 */
public class PaymentResponse {

    private Long paymentId;
    private Long libraryId;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private Long studentFeeId;
    private String invoiceNumber;
    private String receiptNumber;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionReference;
    private LocalDateTime paymentDate;
    private String status;

    public static PaymentResponse from(Payment payment) {
        PaymentResponse r = new PaymentResponse();
        r.paymentId = payment.getPaymentId();
        if (payment.getLibrary() != null) {
            r.libraryId = payment.getLibrary().getLibraryId();
        }
        if (payment.getStudent() != null) {
            r.studentId = payment.getStudent().getStudentId();
            r.studentCode = payment.getStudent().getStudentCode();
            String first = payment.getStudent().getFirstName();
            String last = payment.getStudent().getLastName();
            r.studentName = last == null || last.isBlank() ? first : first + " " + last;
        }
        if (payment.getStudentFee() != null) {
            r.studentFeeId = payment.getStudentFee().getStudentFeeId();
            r.invoiceNumber = payment.getStudentFee().getInvoiceNumber();
        }
        r.receiptNumber = payment.getReceiptNumber();
        r.amount = payment.getAmount();
        r.paymentMethod = payment.getPaymentMethod();
        r.transactionReference = payment.getTransactionReference();
        r.paymentDate = payment.getPaymentDate();
        r.status = payment.getStatus();
        return r;
    }

    public Long getPaymentId() { return paymentId; }
    public Long getLibraryId() { return libraryId; }
    public Long getStudentId() { return studentId; }
    public String getStudentCode() { return studentCode; }
    public String getStudentName() { return studentName; }
    public Long getStudentFeeId() { return studentFeeId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getReceiptNumber() { return receiptNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionReference() { return transactionReference; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public String getStatus() { return status; }
}
