package com.librarysaas.finance.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.finance.dto.StudentFeeRequest;
import com.librarysaas.finance.dto.StudentFeeResponse;
import com.librarysaas.finance.entity.FeePlan;
import com.librarysaas.finance.entity.StudentFee;
import com.librarysaas.finance.repository.FeePlanRepository;
import com.librarysaas.finance.repository.PaymentRepository;
import com.librarysaas.finance.repository.StudentFeeRepository;
import com.librarysaas.finance.service.StudentFeeService;
import com.librarysaas.library.entity.Library;
import com.librarysaas.membership.entity.StudentMembership;
import com.librarysaas.membership.repository.StudentMembershipRepository;
import com.librarysaas.student.entity.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Invoice rules.
 *
 * Two things are computed rather than accepted:
 *
 * <ul>
 *   <li><b>The total.</b> {@code total = amount - discount + tax}, which is the
 *       arithmetic every seeded invoice satisfies. A caller supplies the three
 *       parts and never the total, so the two can never disagree.</li>
 *   <li><b>The status.</b> PENDING, PARTIALLY_PAID and PAID all appear in the
 *       seed and each matches the payments recorded against that row. Status is
 *       therefore derived from payments and has no endpoint of its own.</li>
 * </ul>
 *
 * All money is BigDecimal against DECIMAL(12,2) columns. Nothing here touches a
 * floating-point type.
 */
@Service
public class StudentFeeServiceImpl implements StudentFeeService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";
    static final String STATUS_PAID = "PAID";

    private static final Set<String> ALLOWED_STATUSES =
            Set.of(STATUS_PENDING, STATUS_PARTIALLY_PAID, STATUS_PAID);

    private final StudentFeeRepository studentFeeRepository;
    private final FeePlanRepository feePlanRepository;
    private final PaymentRepository paymentRepository;
    private final StudentMembershipRepository membershipRepository;
    private final FinanceTenantGuard guard;

    @Autowired
    public StudentFeeServiceImpl(StudentFeeRepository studentFeeRepository,
                                 FeePlanRepository feePlanRepository,
                                 PaymentRepository paymentRepository,
                                 StudentMembershipRepository membershipRepository,
                                 FinanceTenantGuard guard) {
        this.studentFeeRepository = studentFeeRepository;
        this.feePlanRepository = feePlanRepository;
        this.paymentRepository = paymentRepository;
        this.membershipRepository = membershipRepository;
        this.guard = guard;
    }

    /**
     * The invoice status implied by what has been settled against it.
     *
     * Shared with PaymentServiceImpl so a payment and a read can never disagree
     * about what a balance means.
     */
    static String statusFor(BigDecimal totalAmount, BigDecimal paidAmount) {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;

        if (paid.signum() <= 0) {
            return STATUS_PENDING;
        }
        // compareTo, never equals: 1500.00 and 1500.0 are equal in value but not
        // as BigDecimal objects, and only the value matters here.
        return paid.compareTo(total) >= 0 ? STATUS_PAID : STATUS_PARTIALLY_PAID;
    }

    /* ---------------------------------------------------------------- reads */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    public List<StudentFeeResponse> getLibraryFees(Long libraryId, String status) {
        guard.requireLibrary(libraryId);

        List<StudentFee> fees = status == null || status.isBlank()
                ? studentFeeRepository.findAllByLibrary(libraryId)
                : studentFeeRepository.findAllByLibraryAndStatus(libraryId, normaliseStatus(status));

        return fees.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    public List<StudentFeeResponse> getStudentFees(Long studentId) {
        // The student resolves first so an unknown id is 404 rather than 403.
        Student student = guard.requireStudent(studentId);
        guard.requireLibraryAccess(guard.libraryIdOf(student));

        return studentFeeRepository.findAllByStudent(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    public StudentFeeResponse getFee(Long studentFeeId) {
        return toResponse(requireFee(studentFeeId));
    }

    /* --------------------------------------------------------------- writes */

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    public StudentFeeResponse createFee(Long libraryId, StudentFeeRequest request) {
        Library library = guard.requireLibrary(libraryId);
        Student student = guard.requireStudentInLibrary(request.getStudentId(), libraryId);

        FeePlan plan = resolveFeePlan(libraryId, request.getFeePlanId());
        StudentMembership membership = resolveMembership(request.getMembershipId(), student, libraryId);

        String invoiceNumber = request.getInvoiceNumber().trim();
        if (studentFeeRepository.existsByLibraryLibraryIdAndInvoiceNumber(libraryId, invoiceNumber)) {
            throw new ConflictException(
                    "Invoice number " + invoiceNumber + " is already used in this library",
                    "INVOICE_NUMBER_ALREADY_EXISTS");
        }

        // The plan price is the default when no amount is stated, which is the
        // only reason to attach a plan to an invoice at all.
        BigDecimal amount = request.getAmount() != null
                ? request.getAmount()
                : (plan != null ? plan.getAmount() : null);
        if (amount == null) {
            throw new BusinessException("An amount is required when no fee plan is chosen",
                    "INVALID_FEE_AMOUNT");
        }

        BigDecimal discount = orZero(request.getDiscountAmount());
        BigDecimal tax = orZero(request.getTaxAmount());
        requireNonNegative(amount, discount, tax);

        BigDecimal total = amount.subtract(discount).add(tax);
        if (total.signum() < 0) {
            throw new BusinessException(
                    "The discount cannot exceed the amount plus tax, which would make the invoice negative",
                    "INVALID_FEE_AMOUNT");
        }

        LocalDateTime now = LocalDateTime.now();
        Long actor = guard.currentUserId();

        StudentFee fee = new StudentFee();
        fee.setLibrary(library);
        fee.setStudent(student);
        fee.setFeePlan(plan);
        fee.setMembership(membership);
        fee.setInvoiceNumber(invoiceNumber);
        fee.setAmount(amount);
        fee.setDiscountAmount(discount);
        fee.setTaxAmount(tax);
        fee.setTotalAmount(total);
        fee.setDueDate(request.getDueDate());
        // A brand new invoice has no payments, so its derived status is PENDING.
        // A zero-total invoice is settled the moment it is raised.
        fee.setStatus(statusFor(total, BigDecimal.ZERO));
        fee.setCreatedAt(now);
        fee.setCreatedBy(actor);
        fee.setUpdatedAt(now);
        fee.setUpdatedBy(actor);

        return toResponse(studentFeeRepository.saveAndFlush(fee));
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves an invoice and authorises against the library on its own row, so
     * an invoice id from another tenant is refused rather than served.
     */
    private StudentFee requireFee(Long studentFeeId) {
        StudentFee fee = studentFeeRepository.findByIdWithDetail(studentFeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found", "STUDENT_FEE_NOT_FOUND"));

        Long libraryId = fee.getLibrary() == null ? null : fee.getLibrary().getLibraryId();
        if (libraryId == null) {
            throw new ResourceNotFoundException("Invoice not found", "STUDENT_FEE_NOT_FOUND");
        }
        guard.requireLibraryAccess(libraryId);
        return fee;
    }

    /** Scoping the lookup to the library stops a plan from another tenant being billed. */
    private FeePlan resolveFeePlan(Long libraryId, Long feePlanId) {
        if (feePlanId == null) {
            return null;
        }
        FeePlan plan = feePlanRepository.findByFeePlanIdAndLibraryLibraryId(feePlanId, libraryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Fee plan not found in this library", "FEE_PLAN_NOT_FOUND"));

        if (!FeePlanServiceImpl.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new BusinessException("Cannot bill against a retired fee plan", "FEE_PLAN_INACTIVE");
        }
        return plan;
    }

    /**
     * A membership may only be billed to the student who holds it, in the
     * library that issued it. Both are plain field comparisons, so neither can
     * be bypassed by a caller's privileges.
     */
    private StudentMembership resolveMembership(Long membershipId, Student student, Long libraryId) {
        if (membershipId == null) {
            return null;
        }
        StudentMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership not found", "STUDENT_MEMBERSHIP_NOT_FOUND"));

        Long membershipStudentId = membership.getStudent() == null
                ? null
                : membership.getStudent().getStudentId();
        Long membershipLibraryId = membership.getLibrary() == null
                ? null
                : membership.getLibrary().getLibraryId();

        if (membershipStudentId == null || !membershipStudentId.equals(student.getStudentId())
                || membershipLibraryId == null || !membershipLibraryId.equals(libraryId)) {
            throw new BusinessException("That membership does not belong to this student",
                    "MEMBERSHIP_NOT_FOR_STUDENT");
        }
        return membership;
    }

    private StudentFeeResponse toResponse(StudentFee fee) {
        return StudentFeeResponse.from(fee,
                paymentRepository.sumSettledForFee(fee.getStudentFeeId()));
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void requireNonNegative(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value == null || value.signum() < 0) {
                throw new BusinessException("Monetary amounts cannot be negative",
                        "INVALID_FEE_AMOUNT");
            }
        }
    }

    private String normaliseStatus(String requested) {
        String status = requested == null ? "" : requested.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    "Invalid invoice status: " + requested + ". Allowed: "
                            + String.join(", ", ALLOWED_STATUSES),
                    "INVALID_STUDENT_FEE_STATUS");
        }
        return status;
    }
}
