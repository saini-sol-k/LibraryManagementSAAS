package com.librarysaas.finance.repository;

import com.librarysaas.finance.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p JOIN FETCH p.student LEFT JOIN FETCH p.studentFee "
            + "WHERE p.paymentId = :paymentId")
    Optional<Payment> findByIdWithDetail(@Param("paymentId") Long paymentId);

    /**
     * What has actually been settled against one invoice.
     *
     * Sums in the database rather than in Java so the total is exact decimal
     * arithmetic end to end, and returns zero rather than null for an invoice
     * with no payments yet. Only SUCCESS rows count towards a balance.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.studentFee.studentFeeId = :studentFeeId AND p.status = 'SUCCESS'")
    BigDecimal sumSettledForFee(@Param("studentFeeId") Long studentFeeId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.student LEFT JOIN FETCH p.studentFee "
            + "WHERE p.studentFee.studentFeeId = :studentFeeId "
            + "ORDER BY p.paymentDate DESC, p.paymentId DESC")
    List<Payment> findAllByStudentFee(@Param("studentFeeId") Long studentFeeId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.student LEFT JOIN FETCH p.studentFee "
            + "WHERE p.library.libraryId = :libraryId "
            + "ORDER BY p.paymentDate DESC, p.paymentId DESC")
    List<Payment> findAllByLibrary(@Param("libraryId") Long libraryId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.student LEFT JOIN FETCH p.studentFee "
            + "WHERE p.student.studentId = :studentId "
            + "ORDER BY p.paymentDate DESC, p.paymentId DESC")
    List<Payment> findAllByStudent(@Param("studentId") Long studentId);

    /** Receipt numbers are unique per library (uk_payment_receipt). */
    boolean existsByLibraryLibraryIdAndReceiptNumber(Long libraryId, String receiptNumber);
}
