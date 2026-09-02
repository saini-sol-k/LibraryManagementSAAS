package com.librarysaas.student.repository;

import com.librarysaas.student.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Page<Student> findByLibraryLibraryId(Long libraryId, Pageable pageable);

    java.util.Optional<Student> findByStudentIdAndLibraryLibraryId(Long id, Long libraryId);

    java.util.Optional<Student> findByStudentCodeAndLibraryLibraryId(String studentCode, Long libraryId);

    boolean existsByLibraryLibraryIdAndStudentCode(Long libraryId, String studentCode);

    @Query("SELECT s FROM Student s " +
            "WHERE (:libraryId IS NULL OR s.library.libraryId = :libraryId) " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:search IS NULL OR (LOWER(s.firstName) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR s.mobile LIKE CONCAT('%',:search,'%')))"
    )
    Page<Student> search(@Param("libraryId") Long libraryId,
                         @Param("status") String status,
                         @Param("search") String search,
                         Pageable pageable);
}
