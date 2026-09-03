package com.librarysaas.finance.repository;

import com.librarysaas.finance.entity.StudentFee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentFeeRepository extends JpaRepository<StudentFee, Long> {

    @Query("SELECT f FROM StudentFee f JOIN FETCH f.student LEFT JOIN FETCH f.feePlan "
            + "WHERE f.studentFeeId = :studentFeeId")
    Optional<StudentFee> findByIdWithDetail(@Param("studentFeeId") Long studentFeeId);

    /**
     * The same invoice, taken under a database write lock.
     *
     * Recording a payment is read-then-write: the outstanding balance is
     * computed and then compared against the amount offered. student_fee has no
     * version column, so optimistic locking is unavailable and two concurrent
     * payments could otherwise both pass that check and overpay the invoice.
     * Locking the row serialises them, which is the only protection the existing
     * schema permits without a migration.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM StudentFee f WHERE f.studentFeeId = :studentFeeId")
    Optional<StudentFee> findByIdForUpdate(@Param("studentFeeId") Long studentFeeId);

    @Query("SELECT f FROM StudentFee f JOIN FETCH f.student LEFT JOIN FETCH f.feePlan "
            + "WHERE f.library.libraryId = :libraryId "
            + "ORDER BY f.dueDate DESC, f.studentFeeId DESC")
    List<StudentFee> findAllByLibrary(@Param("libraryId") Long libraryId);

    @Query("SELECT f FROM StudentFee f JOIN FETCH f.student LEFT JOIN FETCH f.feePlan "
            + "WHERE f.library.libraryId = :libraryId AND f.status = :status "
            + "ORDER BY f.dueDate DESC, f.studentFeeId DESC")
    List<StudentFee> findAllByLibraryAndStatus(@Param("libraryId") Long libraryId,
                                               @Param("status") String status);

    @Query("SELECT f FROM StudentFee f JOIN FETCH f.student LEFT JOIN FETCH f.feePlan "
            + "WHERE f.student.studentId = :studentId "
            + "ORDER BY f.dueDate DESC, f.studentFeeId DESC")
    List<StudentFee> findAllByStudent(@Param("studentId") Long studentId);

    /** Invoice numbers are unique per library (uk_student_fee_invoice). */
    boolean existsByLibraryLibraryIdAndInvoiceNumber(Long libraryId, String invoiceNumber);

    /** True when any invoice still references this plan, so a plan is never orphaned. */
    boolean existsByFeePlanFeePlanId(Long feePlanId);
}
