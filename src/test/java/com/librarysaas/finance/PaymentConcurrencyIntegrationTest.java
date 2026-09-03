package com.librarysaas.finance;

import com.librarysaas.finance.dto.PaymentRequest;
import com.librarysaas.finance.dto.StudentFeeRequest;
import com.librarysaas.finance.dto.StudentFeeResponse;
import com.librarysaas.finance.service.PaymentService;
import com.librarysaas.finance.service.StudentFeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Financial integrity under concurrency.
 *
 * The invoice tables carry no version column, so optimistic locking is not
 * available and nothing in the schema stops an invoice being overpaid. The
 * protection is a pessimistic write lock on the invoice row plus READ_COMMITTED
 * isolation, and this test is what demonstrates the pair actually works: without
 * either half, two simultaneous payments would each read the same balance and
 * both be accepted.
 *
 * The service is called directly rather than through MockMvc because the race
 * has to happen in two real threads against one database, each with its own
 * transaction.
 */
public class PaymentConcurrencyIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private StudentFeeService studentFeeService;

    @Autowired
    private PaymentService paymentService;

    private static final AtomicInteger SEQ = new AtomicInteger(7000);

    /**
     * Runs a call as owner1, who is an active member of library 1 and holds the
     * financial permissions. Each thread needs its own SecurityContext, since
     * the context does not inherit across an executor by default.
     */
    private <T> T asOwner(Callable<T> action) throws Exception {
        var authorities = List.of(
                new SimpleGrantedAuthority("FEE_PLAN_VIEW"),
                new SimpleGrantedAuthority("FEE_PLAN_CREATE"),
                new SimpleGrantedAuthority("PAYMENT_VIEW"),
                new SimpleGrantedAuthority("PAYMENT_CREATE"));
        var authentication = new UsernamePasswordAuthenticationToken("owner1", null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            return action.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long newInvoice(String total) throws Exception {
        StudentFeeRequest request = new StudentFeeRequest();
        request.setStudentId(3L);
        request.setInvoiceNumber("INV-CONC" + SEQ.incrementAndGet());
        request.setAmount(new BigDecimal(total));
        request.setDueDate(LocalDate.of(2026, 12, 31));

        StudentFeeResponse fee = asOwner(() -> studentFeeService.createFee(1L, request));
        return fee.getStudentFeeId();
    }

    private PaymentRequest payment(String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setReceiptNumber("REC-CONC" + SEQ.incrementAndGet());
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMethod("CASH");
        return request;
    }

    /**
     * Two payments that each fit the balance on their own, but together exceed
     * it, launched at the same instant against the same invoice.
     *
     * Exactly one must succeed. If both did, the invoice would hold more money
     * than it was ever for, which the schema has no way to represent.
     */
    @Test
    public void twoSimultaneousPaymentsCannotBothSettleTheSameBalance() throws Exception {
        long feeId = newInvoice("100.00");

        // 60 + 60 = 120, which is more than the 100.00 owed.
        PaymentRequest first = payment("60.00");
        PaymentRequest second = payment("60.00");

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Throwable> attemptOne = attempt(startTogether, feeId, first);
            Callable<Throwable> attemptTwo = attempt(startTogether, feeId, second);

            Future<Throwable> resultOne = pool.submit(attemptOne);
            Future<Throwable> resultTwo = pool.submit(attemptTwo);

            Throwable failureOne = resultOne.get(30, TimeUnit.SECONDS);
            Throwable failureTwo = resultTwo.get(30, TimeUnit.SECONDS);

            long succeeded = (failureOne == null ? 1 : 0) + (failureTwo == null ? 1 : 0);
            assertThat(succeeded)
                    .as("exactly one of two racing payments may be accepted")
                    .isEqualTo(1L);

            // The one that lost must have been refused for the right reason.
            Throwable refusal = failureOne != null ? failureOne : failureTwo;
            assertThat(refusal).isNotNull();
            assertThat(refusal.getMessage()).contains("exceeds the outstanding balance");
        } finally {
            pool.shutdownNow();
        }

        // The invoice holds exactly one payment and is not overpaid.
        StudentFeeResponse fee = asOwner(() -> studentFeeService.getFee(feeId));
        assertThat(fee.getPaidAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("60.00"));
        assertThat(fee.getBalanceAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("40.00"));
        assertThat(fee.getStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(asOwner(() -> paymentService.getFeePayments(feeId)).size()).isEqualTo(1);
    }

    /**
     * Two payments that together settle the invoice exactly must both be
     * accepted: the lock has to serialise them, not reject legitimate ones.
     */
    @Test
    public void twoSimultaneousPaymentsThatFitAreBothAccepted() throws Exception {
        long feeId = newInvoice("100.00");

        PaymentRequest first = payment("40.00");
        PaymentRequest second = payment("60.00");

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> resultOne = pool.submit(attempt(startTogether, feeId, first));
            Future<Throwable> resultTwo = pool.submit(attempt(startTogether, feeId, second));

            assertThat(resultOne.get(30, TimeUnit.SECONDS))
                    .as("a payment that fits the balance must not be refused")
                    .isNull();
            assertThat(resultTwo.get(30, TimeUnit.SECONDS))
                    .as("a payment that fits the balance must not be refused")
                    .isNull();
        } finally {
            pool.shutdownNow();
        }

        StudentFeeResponse fee = asOwner(() -> studentFeeService.getFee(feeId));
        assertThat(fee.getPaidAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("100.00"));
        assertThat(fee.getBalanceAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.ZERO);
        // The derived status followed both payments correctly.
        assertThat(fee.getStatus()).isEqualTo("PAID");
    }

    /** Returns the failure, or null when the payment was accepted. */
    private Callable<Throwable> attempt(CyclicBarrier barrier, long feeId, PaymentRequest request) {
        return () -> {
            try {
                barrier.await(20, TimeUnit.SECONDS);
                asOwner(() -> paymentService.recordPayment(feeId, request));
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        };
    }
}
