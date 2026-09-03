package com.librarysaas.membership.repository;

import com.librarysaas.membership.entity.StudentMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentMembershipRepository extends JpaRepository<StudentMembership, Long> {

    /**
     * Scoping the lookup to the library is what stops membership-id probing
     * across tenants: an id from another library reads as absent.
     */
    @Query("SELECT m FROM StudentMembership m JOIN FETCH m.student "
            + "WHERE m.membershipId = :membershipId AND m.library.libraryId = :libraryId")
    Optional<StudentMembership> findByIdAndLibraryId(@Param("membershipId") Long membershipId,
                                                     @Param("libraryId") Long libraryId);

    /** Every membership of a library, newest period first, with the student fetched. */
    @Query("SELECT m FROM StudentMembership m JOIN FETCH m.student "
            + "WHERE m.library.libraryId = :libraryId "
            + "ORDER BY m.startDate DESC, m.membershipId DESC")
    List<StudentMembership> findAllByLibraryIdWithStudent(@Param("libraryId") Long libraryId);

    @Query("SELECT m FROM StudentMembership m JOIN FETCH m.student "
            + "WHERE m.library.libraryId = :libraryId AND m.status = :status "
            + "ORDER BY m.startDate DESC, m.membershipId DESC")
    List<StudentMembership> findAllByLibraryIdAndStatusWithStudent(@Param("libraryId") Long libraryId,
                                                                   @Param("status") String status);

    /** One student's history, newest period first. */
    @Query("SELECT m FROM StudentMembership m JOIN FETCH m.student "
            + "WHERE m.student.studentId = :studentId "
            + "ORDER BY m.startDate DESC, m.membershipId DESC")
    List<StudentMembership> findAllByStudentIdWithStudent(@Param("studentId") Long studentId);

    /**
     * Membership numbers are unique per library (uk_membership_library_number).
     * Checked before insert so the collision is a 409 rather than a constraint
     * violation surfacing from the driver.
     */
    boolean existsByLibraryLibraryIdAndMembershipNumber(Long libraryId, String membershipNumber);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM StudentMembership m "
            + "WHERE m.library.libraryId = :libraryId AND m.membershipNumber = :membershipNumber "
            + "AND m.membershipId <> :excludedId")
    boolean existsByLibraryAndNumberExcluding(@Param("libraryId") Long libraryId,
                                              @Param("membershipNumber") String membershipNumber,
                                              @Param("excludedId") Long excludedId);

    /**
     * Active memberships of one student whose period overlaps [start, end].
     *
     * Two closed ranges overlap when each starts on or before the other ends.
     * {@code excludedId} lets an update ignore the row being edited; pass a
     * negative id when creating, since no row can match it.
     */
    @Query("SELECT m FROM StudentMembership m "
            + "WHERE m.student.studentId = :studentId AND m.status = 'ACTIVE' "
            + "AND m.membershipId <> :excludedId "
            + "AND m.startDate <= :endDate AND m.endDate >= :startDate")
    List<StudentMembership> findActiveOverlapping(@Param("studentId") Long studentId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("excludedId") Long excludedId);
}
