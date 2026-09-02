package com.librarysaas.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.student.controller.StudentController;
import com.librarysaas.student.dto.*;
import com.librarysaas.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StudentController.class, excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
public class StudentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private StudentService studentService;

    @Test
    public void createStudent() throws Exception {
        StudentCreateRequest req = new StudentCreateRequest();
        req.setStudentCode("STU100");
        req.setFirstName("Test");
        req.setJoiningDate(LocalDate.now());

        StudentResponse resp = new StudentResponse();
        resp.setId(100L);
        resp.setLibraryId(1L);
        resp.setStudentCode("STU100");

        when(studentService.createStudent(any())).thenReturn(resp);

        // set tenant context (filters are disabled in this test)
        com.librarysaas.security.TenantContext.setLibraryId(1L);

        mvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));

        com.librarysaas.security.TenantContext.clear();
    }

    @Test
    public void getStudent() throws Exception {
        StudentResponse resp = new StudentResponse();
        resp.setId(1L);
        resp.setLibraryId(1L);
        resp.setStudentCode("STU001");

        when(studentService.getStudent(1L, 1L)).thenReturn(resp);

        com.librarysaas.security.TenantContext.setLibraryId(1L);

        mvc.perform(get("/api/students/1").header("X-Library-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));

        com.librarysaas.security.TenantContext.clear();
    }

    @Test
    public void listStudents() throws Exception {
        StudentSummaryResponse s = new StudentSummaryResponse();
        s.setId(1L);
        s.setLibraryId(1L);
        s.setStudentCode("STU001");

        when(studentService.getStudents(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(s), PageRequest.of(0,20), 1));

        com.librarysaas.security.TenantContext.setLibraryId(1L);

        mvc.perform(get("/api/students").header("X-Library-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        com.librarysaas.security.TenantContext.clear();
    }
}
