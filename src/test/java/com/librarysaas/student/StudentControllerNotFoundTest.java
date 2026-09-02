package com.librarysaas.student;

import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.student.controller.StudentController;
import com.librarysaas.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for Student GET endpoint with 404 Not Found scenarios
 */
@WebMvcTest(controllers = StudentController.class, excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
public class StudentControllerNotFoundTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private StudentService studentService;

    @BeforeEach
    public void setUp() {
        com.librarysaas.security.TenantContext.setLibraryId(1L);
    }

    @Test
    public void testGetNonExistentStudent_ShouldReturn404() throws Exception {
        // Arrange: Service throws ResourceNotFoundException for non-existent student
        when(studentService.getStudent(anyLong(), anyLong()))
                .thenThrow(new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        // Act & Assert: Should return HTTP 404 with proper error response
        mvc.perform(get("/api/students/1001111")
                .header("X-Library-Id", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())  // HTTP 404
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Student not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void testGetNonExistentStudent_ShouldNotReturn500() throws Exception {
        // Arrange: Service throws ResourceNotFoundException
        when(studentService.getStudent(anyLong(), anyLong()))
                .thenThrow(new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        // Act & Assert: Should NOT return INTERNAL_ERROR (must return STUDENT_NOT_FOUND instead)
        mvc.perform(get("/api/students/9999999")
                .header("X-Library-Id", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())  // Must be 404, not 500
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void testUnexpectedException_StillReturns500InternalError() throws Exception {
        // Arrange: an unexpected programming/database failure, not a business error
        when(studentService.getStudent(anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        // Act & Assert: must still be reported as INTERNAL_ERROR / HTTP 500
        mvc.perform(get("/api/students/1")
                .header("X-Library-Id", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    public void testDatabaseFailure_StillReturns500InternalError() throws Exception {
        when(studentService.getStudent(anyLong(), anyLong()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        mvc.perform(get("/api/students/1")
                .header("X-Library-Id", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    public void testForbidden_StillReturns403() throws Exception {
        // Wrong-library / membership denial must keep its existing 403 behaviour
        when(studentService.getStudent(anyLong(), anyLong()))
                .thenThrow(new com.librarysaas.common.exception.ForbiddenException(
                        "You do not have permission to perform this operation"));

        mvc.perform(get("/api/students/4")
                .header("X-Library-Id", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
