package com.librarysaas.reporting.repository;

import com.librarysaas.library.entity.Library;
import com.librarysaas.membership.entity.StudentMembership;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only aggregate queries for reporting.
 *
 * Every query here takes {@code libraryId} and binds it inside the statement, so
 * the database returns one tenant's numbers and no other. Nothing in this
 * interface reads more than one library and narrows afterwards: an aggregate
 * computed across tenants and filtered in Java would leak a figure derived from
 * every library at once, and a wrong total is far harder to notice than a wrong
 * row. Removing a {@code library_id} predicate here is what the leakage test in
 * ReportingIntegrationTest is built to catch.
 *
 * Counts and sums are computed by the database. No method returns rows for Java
 * to fold, apart from the expiring-membership list, which is a genuine list and
 * is bounded by its date window.
 *
 * The queries are shaped to the indexes V1 already provides:
 * student(library_id, status), seat(library_id, status),
 * student_membership(library_id, status) and (library_id, end_date),
 * attendance(library_id, attendance_date), payment(library_id, payment_date)
 * and student_fee(library_id, due_date).
 *
 * It extends the bare Repository marker rather than JpaRepository on purpose:
 * reporting must expose no save, delete or flush of any kind.
 */
public interface ReportingRepository extends Repository<Library, Long> {

    /* --------------------------------------------------------------- students */

    @Query("SELECT COUNT(s) FROM Student s WHERE s.library.libraryId = :libraryId")
    long countStudents(@Param("libraryId") Long libraryId);

    /** Rows of [status, count]; the status vocabulary is whatever the data holds. */
    @Query("SELECT s.status, COUNT(s) FROM Student s "
            + "WHERE s.library.libraryId = :libraryId GROUP BY s.status ORDER BY s.status")
    List<Object[]> countStudentsByStatus(@Param("libraryId") Long libraryId);

    /* ------------------------------------------------------------------ seats */

    /** Rows of [status, count]. Occupied and available are read from these. */
    @Query("SELECT st.status, COUNT(st) FROM Seat st "
            + "WHERE st.library.libraryId = :libraryId GROUP BY st.status ORDER BY st.status")
    List<Object[]> countSeatsByStatus(@Param("libraryId") Long libraryId);

    /* ------------------------------------------------------------ memberships */

    @Query("SELECT COUNT(m) FROM StudentMembership m "
            + "WHERE m.library.libraryId = :libraryId AND m.status = :status")
    long countMembershipsByStatus(@Param("libraryId") Long libraryId,
                                  @Param("status") String status);

    /**
     * Active memberships whose period ends inside the window, soonest first.
     *
     * Only ACTIVE rows are considered: a cancelled or already-expired membership
     * is not "expiring". The window is a closed range on end_date, which is what
     * idx_membership_expiry indexes.
     */
    @Query("SELECT m FROM StudentMembership m JOIN FETCH m.student "
            + "WHERE m.library.libraryId = :libraryId AND m.status = :status "
            + "AND m.endDate >= :fromDate AND m.endDate <= :toDate "
            + "ORDER BY m.endDate, m.membershipId")
    List<StudentMembership> findExpiringMemberships(@Param("libraryId") Long libraryId,
                                                    @Param("status") String status,
                                                    @Param("fromDate") LocalDate fromDate,
                                                    @Param("toDate") LocalDate toDate);

    /* ------------------------------------------------------------- attendance */

    /**
     * Visits recorded on one calendar day.
     *
     * attendance_date is a DATE, so the day is matched by equality against the
     * library's own local date, computed by the service from library.timezone.
     */
    @Query("SELECT COUNT(a) FROM Attendance a "
            + "WHERE a.library.libraryId = :libraryId AND a.attendanceDate = :day")
    long countAttendanceOnDay(@Param("libraryId") Long libraryId, @Param("day") LocalDate day);

    /**
     * Students still in the library: a visit on that day with no check-out time.
     *
     * Defined by the absence of check_out_time rather than by status, matching
     * the rule the attendance module itself uses to decide whether a student may
     * check in again.
     */
    @Query("SELECT COUNT(a) FROM Attendance a "
            + "WHERE a.library.libraryId = :libraryId AND a.attendanceDate = :day "
            + "AND a.checkOutTime IS NULL")
    long countCurrentlyInside(@Param("libraryId") Long libraryId, @Param("day") LocalDate day);

    /* ---------------------------------------------------------------- payments */

    /**
     * Money banked in a half-open instant range, [from, to).
     *
     * payment_date is a TIMESTAMP, so the day is a range rather than an equality,
     * with both boundaries computed from the library's timezone. Only SUCCESS
     * rows count, matching the Phase 2F balance rule. COALESCE returns an exact
     * zero rather than null when nothing was taken.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.library.libraryId = :libraryId AND p.status = :status "
            + "AND p.paymentDate >= :fromInclusive AND p.paymentDate < :toExclusive")
    BigDecimal sumPaymentsBetween(@Param("libraryId") Long libraryId,
                                  @Param("status") String status,
                                  @Param("fromInclusive") LocalDateTime fromInclusive,
                                  @Param("toExclusive") LocalDateTime toExclusive);

    @Query("SELECT COUNT(p) FROM Payment p "
            + "WHERE p.library.libraryId = :libraryId AND p.status = :status "
            + "AND p.paymentDate >= :fromInclusive AND p.paymentDate < :toExclusive")
    long countPaymentsBetween(@Param("libraryId") Long libraryId,
                              @Param("status") String status,
                              @Param("fromInclusive") LocalDateTime fromInclusive,
                              @Param("toExclusive") LocalDateTime toExclusive);

    /** Rows of [method, count, sum] over the same range, grouped in the database. */
    @Query("SELECT p.paymentMethod, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.library.libraryId = :libraryId AND p.status = :status "
            + "AND p.paymentDate >= :fromInclusive AND p.paymentDate < :toExclusive "
            + "GROUP BY p.paymentMethod ORDER BY p.paymentMethod")
    List<Object[]> sumPaymentsByMethod(@Param("libraryId") Long libraryId,
                                       @Param("status") String status,
                                       @Param("fromInclusive") LocalDateTime fromInclusive,
                                       @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Rows of [localDay, count, sum], bucketed by the library's calendar day.
     *
     * Native because the bucket has to be the library's local date, not the
     * database server's. The stored timestamp is shifted by {@code offsetMinutes}
     * and then truncated to a date, so grouping happens entirely in the database.
     * The WHERE clause still filters on the raw column, so
     * idx_payment_library_date is still used to select the rows before grouping.
     *
     * {@code offsetMinutes} is the difference between the library's zone and the
     * zone the timestamps were written in, not the library's full offset from
     * UTC. Passing the full offset would count the storage zone twice and bucket
     * late-evening payments into the next day. The caller computes it; see
     * ReportingServiceImpl#bucketShiftMinutes.
     *
     * The offset is evaluated once, for the start of the range. Zones with a
     * daylight-saving transition inside the range would bucket the transition
     * day by the earlier offset; Asia/Kolkata, the only zone this system seeds,
     * has no such transition. This is recorded as a known limitation rather than
     * solved with a timezone-table dependency the container may not carry.
     */
    @Query(value = "SELECT DATE(p.payment_date + INTERVAL :offsetMinutes MINUTE) AS local_day, "
            + "COUNT(*), COALESCE(SUM(p.amount), 0) "
            + "FROM payment p "
            + "WHERE p.library_id = :libraryId AND p.status = :status "
            + "AND p.payment_date >= :fromInclusive AND p.payment_date < :toExclusive "
            + "GROUP BY local_day ORDER BY local_day", nativeQuery = true)
    List<Object[]> sumPaymentsByLocalDay(@Param("libraryId") Long libraryId,
                                         @Param("status") String status,
                                         @Param("fromInclusive") LocalDateTime fromInclusive,
                                         @Param("toExclusive") LocalDateTime toExclusive,
                                         @Param("offsetMinutes") int offsetMinutes);

    /* ------------------------------------------------------------- invoicing */

    @Query("SELECT COUNT(f), COALESCE(SUM(f.totalAmount), 0) FROM StudentFee f "
            + "WHERE f.library.libraryId = :libraryId")
    List<Object[]> summariseInvoiced(@Param("libraryId") Long libraryId);

    /**
     * Everything settled against this library's invoices.
     *
     * Restricted to payments that carry an invoice, so the figure subtracts
     * cleanly from the invoiced total.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.library.libraryId = :libraryId AND p.status = :status "
            + "AND p.studentFee IS NOT NULL")
    BigDecimal sumSettledForLibrary(@Param("libraryId") Long libraryId,
                                    @Param("status") String status);

    /**
     * Invoices past their due date that still owe money, as [count, amount].
     *
     * The outstanding amount is the Phase 2F rule applied per invoice: the total
     * less the sum of its successful payments. The correlated subquery keeps the
     * whole calculation in the database rather than pulling invoices back to
     * work out which are short.
     */
    @Query("SELECT COUNT(f), COALESCE(SUM(f.totalAmount - "
            + "  (SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "   WHERE p.studentFee = f AND p.status = :status)), 0) "
            + "FROM StudentFee f "
            + "WHERE f.library.libraryId = :libraryId AND f.dueDate < :asOf "
            + "AND f.totalAmount > "
            + "  (SELECT COALESCE(SUM(p2.amount), 0) FROM Payment p2 "
            + "   WHERE p2.studentFee = f AND p2.status = :status)")
    List<Object[]> summariseOverdue(@Param("libraryId") Long libraryId,
                                    @Param("status") String status,
                                    @Param("asOf") LocalDate asOf);
}
