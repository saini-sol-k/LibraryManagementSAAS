package com.librarysaas.student.repository;

import com.librarysaas.student.entity.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {

    /** One student's documents, newest first. Uses idx_student_document_student. */
    @Query("SELECT d FROM StudentDocument d JOIN FETCH d.student "
            + "WHERE d.student.studentId = :studentId "
            + "ORDER BY d.documentId DESC")
    List<StudentDocument> findAllByStudent(@Param("studentId") Long studentId);

    /**
     * The student is fetched with the document so the service can read the
     * owning library from the row itself rather than trusting the request.
     */
    @Query("SELECT d FROM StudentDocument d JOIN FETCH d.student WHERE d.documentId = :documentId")
    Optional<StudentDocument> findByIdWithStudent(@Param("documentId") Long documentId);
}
