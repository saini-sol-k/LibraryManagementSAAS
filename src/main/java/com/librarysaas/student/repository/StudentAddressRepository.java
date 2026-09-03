package com.librarysaas.student.repository;

import com.librarysaas.student.entity.StudentAddress;
import com.librarysaas.student.entity.StudentAddressKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAddressRepository extends JpaRepository<StudentAddress, StudentAddressKey> {

    @Query("SELECT sa FROM StudentAddress sa JOIN FETCH sa.address "
            + "WHERE sa.id.studentId = :studentId ORDER BY sa.isPrimary DESC, sa.id.addressType ASC")
    List<StudentAddress> findByStudentId(@Param("studentId") Long studentId);

    /** Owner-scoped read: an address id alone is never enough to reach a row. */
    @Query("SELECT sa FROM StudentAddress sa JOIN FETCH sa.address "
            + "WHERE sa.id.studentId = :studentId AND sa.id.addressId = :addressId")
    Optional<StudentAddress> findByStudentIdAndAddressId(
            @Param("studentId") Long studentId, @Param("addressId") Long addressId);

    @Query("SELECT sa FROM StudentAddress sa "
            + "WHERE sa.id.studentId = :studentId AND sa.id.addressType = :addressType")
    Optional<StudentAddress> findByStudentIdAndAddressType(
            @Param("studentId") Long studentId, @Param("addressType") String addressType);

    @Query("SELECT sa FROM StudentAddress sa WHERE sa.id.studentId = :studentId AND sa.isPrimary = true")
    List<StudentAddress> findPrimaryByStudentId(@Param("studentId") Long studentId);
}
