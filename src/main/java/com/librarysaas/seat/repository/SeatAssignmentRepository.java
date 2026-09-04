package com.librarysaas.seat.repository;

import com.librarysaas.seat.entity.SeatAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * V1__initial_schema.sql has no unique constraint on active assignments and
 * states that the service layer must allow only one active assignment per
 * student and one active student per seat. These finders are what that rule is
 * enforced with.
 */
public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    @Query("SELECT a FROM SeatAssignment a "
            + "JOIN FETCH a.student "
            + "WHERE a.seat.seatId = :seatId AND a.status = 'ACTIVE'")
    Optional<SeatAssignment> findActiveBySeatId(@Param("seatId") Long seatId);

    @Query("SELECT a FROM SeatAssignment a "
            + "JOIN FETCH a.seat "
            + "WHERE a.student.studentId = :studentId AND a.status = 'ACTIVE'")
    Optional<SeatAssignment> findActiveByStudentId(@Param("studentId") Long studentId);

    /** Active allocations for a whole library, used to decorate the seat list in one query. */
    @Query("SELECT a FROM SeatAssignment a "
            + "JOIN FETCH a.student "
            + "WHERE a.library.libraryId = :libraryId AND a.status = 'ACTIVE'")
    List<SeatAssignment> findActiveByLibraryId(@Param("libraryId") Long libraryId);

    @Query("SELECT a FROM SeatAssignment a "
            + "JOIN FETCH a.student "
            + "WHERE a.seat.seatId = :seatId "
            + "ORDER BY a.startDate DESC, a.assignmentId DESC")
    List<SeatAssignment> findHistoryBySeatId(@Param("seatId") Long seatId);

    /**
     * Which of these seats are currently allocated. Used before a seat-count
     * reduction withdraws seats, so the check is one query for the
     * whole block rather than one per seat.
     */
    @Query("SELECT DISTINCT a.seat.seatId FROM SeatAssignment a "
            + "WHERE a.status = 'ACTIVE' AND a.seat.seatId IN :seatIds")
    List<Long> findActiveSeatIdsIn(@Param("seatIds") Collection<Long> seatIds);

    /**
     * Which of these seats any assignment has ever pointed at, active or released.
     * A reduction retires such a seat instead of deleting it, so the allocation
     * history keeps resolving to a real row.
     */
    @Query("SELECT DISTINCT a.seat.seatId FROM SeatAssignment a WHERE a.seat.seatId IN :seatIds")
    List<Long> findSeatIdsWithAssignments(@Param("seatIds") Collection<Long> seatIds);
}
