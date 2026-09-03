package com.librarysaas.finance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import com.librarysaas.finance.entity.StudentFee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One invoice, with what has been settled against it and what is still owed.
 *
 * {@code paidAmount} and {@code balanceAmount} are computed from the payments
 * rather than stored, so a caller never sees a balance that has drifted from the
 * payment rows. Both are exact decimal values; no monetary figure here has
 * passed through a floating-point type.
 */
public class StudentFeeResponse {

    private Long studentFeeId;
    private Long libraryId;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private Long membershipId;
    private Long feePlanId;
    private String feePlanName;
    private String invoiceNumber;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal amount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal discountAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal taxAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal totalAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal paidAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal balanceAmount;
    private LocalDate dueDate;
    private String status;
    private Boolean overdue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * @param paidAmount the sum of successful payments, supplied by the service
     *                   because it is a query across another table.
     */
    public static StudentFeeResponse from(StudentFee fee, BigDecimal paidAmount) {
        StudentFeeResponse r = new StudentFeeResponse();
        r.studentFeeId = fee.getStudentFeeId();

        if (fee.getLibrary() != null) {
            r.libraryId = fee.getLibrary().getLibraryId();
        }
        if (fee.getStudent() != null) {
            r.studentId = fee.getStudent().getStudentId();
            r.studentCode = fee.getStudent().getStudentCode();
            String first = fee.getStudent().getFirstName();
            String last = fee.getStudent().getLastName();
            r.studentName = last == null || last.isBlank() ? first : first + " " + last;
        }
        if (fee.getMembership() != null) {
            r.membershipId = fee.getMembership().getMembershipId();
        }
        if (fee.getFeePlan() != null) {
            r.feePlanId = fee.getFeePlan().getFeePlanId();
            r.feePlanName = fee.getFeePlan().getName();
        }

        r.invoiceNumber = fee.getInvoiceNumber();
        r.amount = fee.getAmount();
        r.discountAmount = fee.getDiscountAmount();
        r.taxAmount = fee.getTaxAmount();
        r.totalAmount = fee.getTotalAmount();

        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        r.paidAmount = paid;
        r.balanceAmount = fee.getTotalAmount() == null
                ? null
                : fee.getTotalAmount().subtract(paid);

        r.dueDate = fee.getDueDate();
        r.status = fee.getStatus();
        // Derived at read time: nothing sweeps an unpaid invoice past its due
        // date into another status, so the flag is computed rather than stored.
        r.overdue = fee.getDueDate() != null
                && fee.getDueDate().isBefore(LocalDate.now())
                && r.balanceAmount != null
                && r.balanceAmount.signum() > 0;

        r.createdAt = fee.getCreatedAt();
        r.updatedAt = fee.getUpdatedAt();
        return r;
    }

    public Long getStudentFeeId() { return studentFeeId; }
    public Long getLibraryId() { return libraryId; }
    public Long getStudentId() { return studentId; }
    public String getStudentCode() { return studentCode; }
    public String getStudentName() { return studentName; }
    public Long getMembershipId() { return membershipId; }
    public Long getFeePlanId() { return feePlanId; }
    public String getFeePlanName() { return feePlanName; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public String getStatus() { return status; }
    public Boolean getOverdue() { return overdue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
