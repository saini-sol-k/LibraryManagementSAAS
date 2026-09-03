package com.librarysaas.finance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Record money received against an invoice.
 *
 * The invoice comes from the path, and the student and library are taken from
 * that invoice rather than the body, so a payment can never be attached to a
 * different student or tenant than the invoice it settles.
 *
 * The amount must be strictly positive: a zero payment records nothing and a
 * negative one would be a reversal, which this schema cannot represent. Whether
 * it fits the outstanding balance is decided by PaymentService.
 */
public class PaymentRequest {

    @NotBlank(message = "Receipt number is required")
    @Size(max = 50, message = "Receipt number must not exceed 50 characters")
    private String receiptNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2,
            message = "Amount must have at most 10 digits and 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    @Size(max = 30, message = "Payment method must not exceed 30 characters")
    private String paymentMethod;

    /** Optional. Indexed but not unique in the schema, so it is not an identity. */
    @Size(max = 150, message = "Transaction reference must not exceed 150 characters")
    private String transactionReference;

    public PaymentRequest() {}

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
}
