package com.librarysaas.finance.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.finance.dto.PaymentRequest;
import com.librarysaas.finance.dto.PaymentResponse;
import com.librarysaas.finance.entity.Payment;
import com.librarysaas.finance.entity.StudentFee;
import com.librarysaas.finance.repository.PaymentRepository;
import com.librarysaas.finance.repository.StudentFeeRepository;
import com.librarysaas.finance.service.PaymentService;
import com.librarysaas.student.entity.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment rules.
 *
 * Recording money is the one operation in this module where a race would cost
 * something real, so it is written defensively:
 *
 * <ul>
 *   <li>The invoice is re-read <b>under a database write lock</b> before its
 *       balance is computed. student_fee has no version column, so optimistic
 *       locking is unavailable; without the lock two simultaneous payments could
 *       each see the same balance and both be accepted, overpaying the invoice.
 *       The lock is held for the rest of the transaction, which also covers the
 *       status update.</li>
 *   <li>The balance is exact decimal arithmetic, summed in the database. Nothing
 *       here passes through a floating-point type.</li>
 *   <li>A payment larger than the outstanding balance is refused. The schema has
 *       no credit, advance or overpaid representation, so accepting one would
 *       produce a number the rest of the system cannot describe.</li>
 *   <li>The student and library come from the invoice, never from the caller, so
 *       a payment cannot be attached to a different student or tenant.</li>
 * </ul>
 *
 * Payments are append-only: there is no update and no delete, because the table
 * has no updated_at to record a change against. PAYMENT_REFUND exists as a
 * permission but a reversal has nowhere to live in this schema.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    static final String STATUS_SUCCESS = "SUCCESS";

    private final PaymentRepository paymentRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final FinanceTenantGuard guard;

    @Autowired
    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              StudentFeeRepository studentFeeRepository,
                              FinanceTenantGuard guard) {
        this.paymentRepository = paymentRepository;
        this.studentFeeRepository = studentFeeRepository;
        this.guard = guard;
    }

    /* ---------------------------------------------------------------- reads */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public List<PaymentResponse> getLibraryPayments(Long libraryId) {
        guard.requireLibrary(libraryId);
        return paymentRepository.findAllByLibrary(libraryId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public List<PaymentResponse> getStudentPayments(Long studentId) {
        Student student = guard.requireStudent(studentId);
        guard.requireLibraryAccess(guard.libraryIdOf(student));

        return paymentRepository.findAllByStudent(studentId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public List<PaymentResponse> getFeePayments(Long studentFeeId) {
        requireFeeReadable(studentFeeId);
        return paymentRepository.findAllByStudentFee(studentFeeId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findByIdWithDetail(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found", "PAYMENT_NOT_FOUND"));

        Long libraryId = payment.getLibrary() == null ? null : payment.getLibrary().getLibraryId();
        if (libraryId == null) {
            throw new ResourceNotFoundException("Payment not found", "PAYMENT_NOT_FOUND");
        }
        guard.requireLibraryAccess(libraryId);
        return PaymentResponse.from(payment);
    }

    /* --------------------------------------------------------------- writes */

    /**
     * READ_COMMITTED is required here, not a preference.
     *
     * MySQL defaults to REPEATABLE READ, under which a transaction's snapshot is
     * fixed by its first plain read. In this method that read happens while
     * resolving and authorising the invoice, before the row lock is taken. A
     * second payment that blocks on the lock would then resume and sum the
     * payments against that stale snapshot, missing the payment which just
     * committed, and both would be accepted. Reading committed data instead
     * means the sum taken after the lock reflects what is actually banked, so
     * the lock and the balance check agree.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public PaymentResponse recordPayment(Long studentFeeId, PaymentRequest request) {
        // Resolve and authorise before taking any lock, so an unauthorised or
        // unknown request never holds a row.
        StudentFee readable = requireFeeReadable(studentFeeId);
        Long libraryId = readable.getLibrary().getLibraryId();

        String receiptNumber = request.getReceiptNumber().trim();
        if (paymentRepository.existsByLibraryLibraryIdAndReceiptNumber(libraryId, receiptNumber)) {
            throw new ConflictException(
                    "Receipt number " + receiptNumber + " has already been used in this library",
                    "RECEIPT_NUMBER_ALREADY_EXISTS");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("A payment must be greater than zero",
                    "INVALID_PAYMENT_AMOUNT");
        }

        // From here the invoice is locked: the balance read below and the status
        // written at the end cannot interleave with another payment.
        StudentFee fee = studentFeeRepository.findByIdForUpdate(studentFeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found", "STUDENT_FEE_NOT_FOUND"));

        BigDecimal total = fee.getTotalAmount() == null ? BigDecimal.ZERO : fee.getTotalAmount();
        BigDecimal alreadyPaid = orZero(paymentRepository.sumSettledForFee(studentFeeId));
        BigDecimal balance = total.subtract(alreadyPaid);

        if (balance.signum() <= 0) {
            throw new ConflictException("This invoice is already settled in full",
                    "STUDENT_FEE_ALREADY_PAID");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BusinessException(
                    "Payment of " + amount + " exceeds the outstanding balance of " + balance,
                    "PAYMENT_EXCEEDS_BALANCE");
        }

        LocalDateTime now = LocalDateTime.now();
        Long actor = guard.currentUserId();

        Payment payment = new Payment();
        // Student and library are inherited from the invoice, never taken from
        // the request, so a payment can never cross to another student or tenant.
        payment.setLibrary(fee.getLibrary());
        payment.setStudent(fee.getStudent());
        payment.setStudentFee(fee);
        payment.setReceiptNumber(receiptNumber);
        payment.setAmount(amount);
        payment.setPaymentMethod(request.getPaymentMethod().trim().toUpperCase());
        payment.setTransactionReference(trimToNull(request.getTransactionReference()));
        payment.setPaymentDate(now);
        payment.setStatus(STATUS_SUCCESS);
        payment.setCreatedAt(now);
        payment.setCreatedBy(actor);

        Payment saved = paymentRepository.saveAndFlush(payment);

        // Bring the invoice status in line with what is now settled. Derived
        // from the same rule the read path uses, so the two cannot disagree.
        BigDecimal paidNow = alreadyPaid.add(amount);
        fee.setStatus(StudentFeeServiceImpl.statusFor(total, paidNow));
        fee.setUpdatedAt(now);
        fee.setUpdatedBy(actor);
        studentFeeRepository.saveAndFlush(fee);

        return PaymentResponse.from(saved);
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves an invoice and authorises against the library on its own row, so
     * an invoice id from another tenant is refused rather than served.
     */
    private StudentFee requireFeeReadable(Long studentFeeId) {
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

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
