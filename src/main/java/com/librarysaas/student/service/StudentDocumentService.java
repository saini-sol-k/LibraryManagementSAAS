package com.librarysaas.student.service;

import com.librarysaas.student.dto.StudentDocumentRequest;
import com.librarysaas.student.dto.StudentDocumentResponse;

import java.util.List;

/**
 * Documents held on a student's file.
 *
 * Every operation resolves the student's library and re-checks the caller's
 * membership of it. Documents store a reference to a file, never the file. There
 * is no delete: the schema offers only an ACTIVE status and no archive state, so
 * removing a document is not a decision this module can make safely.
 */
public interface StudentDocumentService {

    List<StudentDocumentResponse> getStudentDocuments(Long studentId);

    StudentDocumentResponse getDocument(Long documentId);

    StudentDocumentResponse createDocument(Long studentId, StudentDocumentRequest request);

    StudentDocumentResponse updateDocument(Long documentId, StudentDocumentRequest request);
}
