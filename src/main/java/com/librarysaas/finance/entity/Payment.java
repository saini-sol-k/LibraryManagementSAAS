package com.librarysaas.finance.entity;

import com.librarysaas.library.entity.Library;
import com.librarysaas.student.entity.Student;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Money received from a student against an invoice.
 *
 * Payments are append-only. The table carries created_at and created_by but no
 * updated_at and no version column, so there is nowhere to record when or by
 * whom a payment was altered. This module therefore never modifies a payment
 * once written, and offers no refund: PAYMENT_REFUND exists as a permission but
 * the schema gives a reversal nowhere to live.
 *
 * The receipt number is unique within the library (uk_payment_receipt), which is
 * the schema's own protection against one receipt being banked twice.
 * transaction_reference is indexed but deliberately not unique, so it is not
 * treated as an identity here.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** Nullable in the schema; this module always sets it, so a balance exists to protect. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_fee_id")
    private StudentFee studentFee;

    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "transaction_reference", length = 150)
    private String transactionReference;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Library getLibrary() { return library; }
    public void setLibrary(Library library) { this.library = library; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public StudentFee getStudentFee() { return studentFee; }
    public void setStudentFee(StudentFee studentFee) { this.studentFee = studentFee; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
