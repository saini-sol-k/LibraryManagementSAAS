package com.librarysaas.student.repository;

import com.librarysaas.student.entity.StudentEmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentEmergencyContactRepository
        extends JpaRepository<StudentEmergencyContact, Long> {

    /** One student's contacts, primary first. Uses idx_emergency_student. */
    @Query("SELECT c FROM StudentEmergencyContact c JOIN FETCH c.student "
            + "LEFT JOIN FETCH c.address "
            + "WHERE c.student.studentId = :studentId "
            + "ORDER BY c.isPrimary DESC, c.emergencyContactId")
    List<StudentEmergencyContact> findAllByStudent(@Param("studentId") Long studentId);

    @Query("SELECT c FROM StudentEmergencyContact c JOIN FETCH c.student "
            + "LEFT JOIN FETCH c.address WHERE c.emergencyContactId = :contactId")
    Optional<StudentEmergencyContact> findByIdWithDetail(@Param("contactId") Long contactId);

    /**
     * The student's current primary contact, if any.
     *
     * Nothing in the schema constrains this to one row, so promoting a contact
     * has to demote the incumbent explicitly. Ordered so the result is stable if
     * historic data ever holds more than one.
     */
    @Query("SELECT c FROM StudentEmergencyContact c "
            + "WHERE c.student.studentId = :studentId AND c.isPrimary = true "
            + "AND c.emergencyContactId <> :excludedId "
            + "ORDER BY c.emergencyContactId")
    List<StudentEmergencyContact> findPrimaryByStudentExcluding(@Param("studentId") Long studentId,
                                                                @Param("excludedId") Long excludedId);
}
