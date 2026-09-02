package com.librarysaas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.entity.Organization;
import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.repository.UserLibraryRepository;
import com.librarysaas.organization.repository.UserOrganizationRepository;
import com.librarysaas.security.model.User;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.student.dto.StudentCreateRequest;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Integration tests for API error handling.
 * Tests validation, 404, 401, 403, 409, and 500 error scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
public class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private UserLibraryRepository userLibraryRepository;

    @Autowired
    private StudentRepository studentRepository;

    private User testUser;
    private Organization testOrg;
    private Library testLibrary;

    @BeforeEach
    public void setup() {
        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedpassword");
        testUser.setFirstName("Test");
        testUser.setStatus("ACTIVE");
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser = userRepository.save(testUser);

        // Create test organization
        testOrg = new Organization();
        testOrg.setOrganizationCode("TEST_ORG");
        testOrg.setName("Test Organization");
        testOrg.setStatus("ACTIVE");
        testOrg.setCreatedAt(LocalDateTime.now());
        testOrg.setUpdatedAt(LocalDateTime.now());
        testOrg = organizationRepository.save(testOrg);

        // Create test library
        testLibrary = new Library();
        testLibrary.setOrganization(testOrg);
        testLibrary.setLibraryCode("TEST_LIB");
        testLibrary.setName("Test Library");
        testLibrary.setStatus("ACTIVE");
        testLibrary.setCreatedAt(LocalDateTime.now());
        testLibrary.setUpdatedAt(LocalDateTime.now());
        testLibrary = libraryRepository.save(testLibrary);

        // Associate user with organization and library
        UserOrganization uo = new UserOrganization();
        uo.setId(new com.librarysaas.organization.entity.UserOrganizationKey(
                testUser.getUserId(), testOrg.getOrganizationId()));
        uo.setUser(testUser);
        uo.setOrganization(testOrg);
        uo.setIsPrimary(true);
        uo.setStatus("ACTIVE");
        uo.setJoinedAt(LocalDateTime.now());
        uo.setCreatedAt(LocalDateTime.now());
        userOrganizationRepository.save(uo);

        UserLibrary ul = new UserLibrary();
        ul.setId(new com.librarysaas.organization.entity.UserLibraryKey(
                testUser.getUserId(), testLibrary.getLibraryId()));
        ul.setUser(testUser);
        ul.setLibrary(testLibrary);
        ul.setIsPrimary(true);
        ul.setStatus("ACTIVE");
        ul.setJoinedAt(LocalDateTime.now());
        ul.setCreatedAt(LocalDateTime.now());
        userLibraryRepository.save(ul);
    }

    // ==================== VALIDATION ERROR TESTS (HTTP 400) ====================

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_CREATE")
    public void testValidationError_MissingRequiredFields() throws Exception {
        StudentCreateRequest req = new StudentCreateRequest();
        req.setStudentCode(""); // Empty - should fail @NotBlank
        req.setFirstName(""); // Empty - should fail @NotBlank
        // joiningDate not set - should fail @NotNull

        mockMvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").isMap())
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString();
                    assert !content.contains("stack trace");
                    assert !content.contains("Exception");
                });
    }

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_CREATE")
    public void testValidationError_InvalidEmail() throws Exception {
        StudentCreateRequest req = new StudentCreateRequest();
        req.setStudentCode("S001");
        req.setFirstName("John");
        req.setEmail("not-an-email"); // Invalid email
        req.setJoiningDate(LocalDate.now());

        mockMvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.email").exists());
    }

    // ==================== NOT FOUND ERRORS (HTTP 404) ====================

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_VIEW")
    public void testNotFound_StudentDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/students/999999")
                .param("libraryIdParam", testLibrary.getLibraryId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Student not found"))
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"ORGANIZATION_VIEW", "ROLE_SUPER_ADMIN"})
    public void testNotFound_OrganizationDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/organizations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================== CONFLICT ERRORS (HTTP 409) ====================

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_CREATE")
    public void testConflict_DuplicateStudentCode() throws Exception {
        // Create first student
        StudentCreateRequest req = new StudentCreateRequest();
        req.setStudentCode("DUPLICATE_CODE");
        req.setFirstName("John");
        req.setJoiningDate(LocalDate.now());

        mockMvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .param("libraryIdParam", testLibrary.getLibraryId().toString()))
                .andExpect(status().isCreated());

        // Try to create another with same code
        mockMvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .param("libraryIdParam", testLibrary.getLibraryId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Student code already exists in this library"))
                .andExpect(jsonPath("$.errorCode").value("STUDENT_CODE_ALREADY_EXISTS"));
    }

    // ==================== UNAUTHORIZED ERRORS (HTTP 401) ====================

    @Test
    public void testUnauthorized_MissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    public void testUnauthorized_InvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"unknown-user\", \"password\": \"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    // ==================== FORBIDDEN ERRORS (HTTP 403) ====================

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_VIEW")
    public void testForbidden_InsufficientPermission() throws Exception {
        // User has STUDENT_VIEW but not STUDENT_CREATE
        StudentCreateRequest req = new StudentCreateRequest();
        req.setStudentCode("S001");
        req.setFirstName("John");
        req.setJoiningDate(LocalDate.now());

        mockMvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ==================== RESPONSE FORMAT TESTS ====================

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_VIEW")
    public void testErrorResponse_NeverContainsStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/students/999999")
                .param("libraryIdParam", testLibrary.getLibraryId().toString()))
                .andExpect(status().isNotFound())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assert !content.contains("stack trace") : "Response should not contain stack trace";
        assert !content.contains("java.") : "Response should not contain java class names";
        assert !content.contains("Exception") : "Response should not expose exceptions";
    }

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_VIEW")
    public void testErrorResponse_HasRequiredFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/students/999999")
                .param("libraryIdParam", testLibrary.getLibraryId().toString()))
                .andExpect(status().isNotFound())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(content, ApiResponse.class);

        assert response != null;
        assert response.success() == false;
        assert response.message() != null;
        assert response.errorCode() != null;
    }

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_CREATE")
    public void testSuccessResponse_HasCorrectFormat() throws Exception {
        StudentCreateRequest req = new StudentCreateRequest();
        req.setStudentCode("S001");
        req.setFirstName("John");
        req.setJoiningDate(LocalDate.now());

        mockMvc.perform(post("/api/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .param("libraryIdParam", testLibrary.getLibraryId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Student created"))
                .andExpect(jsonPath("$.data").exists());
    }

    // ==================== SPRING MVC CLIENT ERRORS (must not become 500) ====================

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_VIEW")
    public void testNotFound_UnmappedPathReturns404NotInternalError() throws Exception {
        mockMvc.perform(get("/api/students/1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "testuser", authorities = "STUDENT_VIEW")
    public void testMethodNotAllowed_Returns405NotInternalError() throws Exception {
        mockMvc.perform(patch("/api/students/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }
}
