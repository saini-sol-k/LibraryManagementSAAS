package com.librarysaas.finance.service;

import com.librarysaas.finance.dto.StudentFeeRequest;
import com.librarysaas.finance.dto.StudentFeeResponse;

import java.util.List;

/**
 * Invoices raised against students.
 *
 * Every operation is library scoped and re-checks the caller's membership of
 * that library. Invoices are never deleted and their status is derived from the
 * payments recorded against them, never set by a caller.
 */
public interface StudentFeeService {

    List<StudentFeeResponse> getLibraryFees(Long libraryId, String status);

    List<StudentFeeResponse> getStudentFees(Long studentId);

    StudentFeeResponse getFee(Long studentFeeId);

    StudentFeeResponse createFee(Long libraryId, StudentFeeRequest request);
}
