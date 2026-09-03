package com.librarysaas.attendance.repository;

import com.librarysaas.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * One day of a library's attendance, newest check-in first, with the student
     * and seat fetched. Uses idx_attendance_library_date.
     */
    @Query("SELECT a FROM Attendance a JOIN FETCH a.student LEFT JOIN FETCH a.seat "
            + "WHERE a.library.libraryId = :libraryId AND a.attendanceDate = :date "
            + "ORDER BY a.checkInTime DESC, a.attendanceId DESC")
    List<Attendance> findByLibraryAndDate(@Param("libraryId") Long libraryId,
                                          @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.student LEFT JOIN FETCH a.seat "
            + "WHERE a.library.libraryId = :libraryId AND a.attendanceDate = :date "
            + "AND a.status = :status "
            + "ORDER BY a.checkInTime DESC, a.attendanceId DESC")
    List<Attendance> findByLibraryAndDateAndStatus(@Param("libraryId") Long libraryId,
                                                   @Param("date") LocalDate date,
                                                   @Param("status") String status);

    /** One student's visit history, newest first. Uses idx_attendance_student_date. */
    @Query("SELECT a FROM Attendance a JOIN FETCH a.student LEFT JOIN FETCH a.seat "
            + "WHERE a.student.studentId = :studentId "
            + "ORDER BY a.checkInTime DESC, a.attendanceId DESC")
    List<Attendance> findByStudent(@Param("studentId") Long studentId);

    /**
     * The student's open visit, if any.
     *
     * "Open" is defined by the absence of a check-out time rather than by status
     * alone, so a row could never be reopened for a second check-in by editing
     * its status. Nothing in the schema prevents two open rows, so this is the
     * only thing enforcing one visit at a time.
     */
    @Query("SELECT a FROM Attendance a "
            + "WHERE a.student.studentId = :studentId AND a.checkOutTime IS NULL "
            + "ORDER BY a.checkInTime DESC")
    List<Attendance> findOpenByStudent(@Param("studentId") Long studentId);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.student LEFT JOIN FETCH a.seat "
            + "WHERE a.attendanceId = :attendanceId")
    Optional<Attendance> findByIdWithDetail(@Param("attendanceId") Long attendanceId);
}
