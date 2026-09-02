package com.librarysaas.student.service;

import com.librarysaas.student.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentService {

    StudentResponse createStudent(StudentCreateRequest req);

    StudentResponse getStudent(Long id, Long libraryId);

    Page<StudentSummaryResponse> getStudents(Long libraryId, String search, String status, Pageable pageable);

    StudentResponse updateStudent(Long id, Long libraryId, StudentUpdateRequest req);

    void deleteStudent(Long id, Long libraryId);
}
