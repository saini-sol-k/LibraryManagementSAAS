package com.librarysaas.finance.entity;

import com.librarysaas.library.entity.Library;
import com.librarysaas.membership.entity.StudentMembership;
import com.librarysaas.student.entity.Student;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An invoice raised against a student.
 *
 * The seeded rows evidence the arithmetic this module enforces:
 * {@code total_amount = amount - discount_amount + tax_amount}. The total is
 * always computed from the three parts rather than accepted from a caller.
 *
 * Status is derived, never set directly: PENDING with nothing paid,
 * PARTIALLY_PAID once some of the total is settled, PAID when it all is. All
 * three appear in V1__initial_schema and each matches the payments seeded
 * against it.
 *
 * The fee plan and the membership are both optional, as the schema allows, but
 * when present each must belong to the same library, and the membership to the
 * same student.
 */
@Entity
@Table(name = "student_fee")
public class StudentFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_fee_id")
    private Long studentFeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** Optional: the membership period this invoice bills for. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private StudentMembership membership;

    /** Optional: the plan the amount was taken from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_plan_id")
    private FeePlan feePlan;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public Long getStudentFeeId() { return studentFeeId; }
    public void setStudentFeeId(Long studentFeeId) { this.studentFeeId = studentFeeId; }

    public Library getLibrary() { return library; }
    public void setLibrary(Library library) { this.library = library; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public StudentMembership getMembership() { return membership; }
    public void setMembership(StudentMembership membership) { this.membership = membership; }

    public FeePlan getFeePlan() { return feePlan; }
    public void setFeePlan(FeePlan feePlan) { this.feePlan = feePlan; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
