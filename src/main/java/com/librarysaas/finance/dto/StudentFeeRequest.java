package com.librarysaas.finance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create payload for an invoice.
 *
 * libraryId comes from the path, never the body. totalAmount is absent on
 * purpose: the service computes it as amount - discount + tax, which is the
 * arithmetic every seeded invoice satisfies, so a caller can never state a total
 * that disagrees with its own parts.
 *
 * All money is BigDecimal, matching the DECIMAL(12,2) columns.
 */
public class StudentFeeRequest {

    @NotNull(message = "Student id is required")
    @Positive(message = "Student id must be a positive number")
    private Long studentId;

    /** Optional. Must belong to the same library when supplied. */
    @Positive(message = "Fee plan id must be a positive number")
    private Long feePlanId;

    /** Optional. Must belong to the same student when supplied. */
    @Positive(message = "Membership id must be a positive number")
    private Long membershipId;

    @NotBlank(message = "Invoice number is required")
    @Size(max = 50, message = "Invoice number must not exceed 50 characters")
    private String invoiceNumber;

    /**
     * Optional when a fee plan is supplied, in which case the plan price is
     * used. Required otherwise.
     */
    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    @Digits(integer = 10, fraction = 2,
            message = "Amount must have at most 10 digits and 2 decimal places")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Discount cannot be negative")
    @Digits(integer = 10, fraction = 2,
            message = "Discount must have at most 10 digits and 2 decimal places")
    private BigDecimal discountAmount;

    @DecimalMin(value = "0.00", message = "Tax cannot be negative")
    @Digits(integer = 10, fraction = 2,
            message = "Tax must have at most 10 digits and 2 decimal places")
    private BigDecimal taxAmount;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    public StudentFeeRequest() {}

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getFeePlanId() { return feePlanId; }
    public void setFeePlanId(Long feePlanId) { this.feePlanId = feePlanId; }

    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
