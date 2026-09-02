package com.librarysaas.student;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class StudentTenantIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private com.librarysaas.student.repository.StudentRepository studentRepository;

    private String loginAndGetAccessToken(String identifier, String password) throws Exception {
        var loginReq = Map.of("identifier", identifier, "password", password);
        var res = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        return json.at("/data/accessToken").asText(null);
    }

    @Test
    public void loginAndAccessStudentSucceedsForAuthorizedLibrary() throws Exception {
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        var r = mvc.perform(get("/api/students/1")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(body.at("/data/id").asLong()).isEqualTo(1L);
    }

    @Test
    public void createStudentWithoutLibraryIdIsAssignedToServerResolvedLibrary() throws Exception {
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        String uniqueCode = "ITST-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> req = Map.of(
                "studentCode", uniqueCode,
                "firstName", "Test",
                "lastName", "Student",
                "joiningDate", "2026-08-01"
        );

        var res = mvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        long createdId = body.at("/data/id").asLong();
        long libraryId = body.at("/data/libraryId").asLong();

        // manager1 belongs to library 1 per seed data
        assertThat(libraryId).isEqualTo(1L);

        // GET the created student should succeed
        var get = mvc.perform(get("/api/students/" + createdId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode getBody = mapper.readTree(get.getResponse().getContentAsString());
        assertThat(getBody.at("/data/id").asLong()).isEqualTo(createdId);
        assertThat(getBody.at("/data/libraryId").asLong()).isEqualTo(1L);
    }

    @Test
    public void headerSpoofingCannotBypassTenantOnCreate() throws Exception {
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        String uniqueCode = "ITST-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> req = Map.of(
                "studentCode", uniqueCode,
                "firstName", "Spoof",
                "lastName", "Attempt",
                "joiningDate", "2026-08-01"
        );

        var res = mvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .header("X-Library-Id", "2") // attempt to spoof to library 2
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        long libraryId = body.at("/data/libraryId").asLong();
        // Spoof should not override JWT-resolved library (manager1 -> library 1)
        assertThat(libraryId).isEqualTo(1L);
    }

    @Test
    public void crossTenantAccessUpdateAndDeleteAreForbidden() throws Exception {
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        // Student id 4 belongs to library 2 (seed data)
        Map<String, Object> updateReq = Map.of("firstName", "X", "lastName", "Y");

        var upd = mvc.perform(put("/api/students/4")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andReturn();

        int updStatus = upd.getResponse().getStatus();
        // Implementation may either return 403 Forbidden (explicit deny) or 404 Not Found
        // (service scoped lookup does not reveal cross-tenant resource). Accept either.
        assertThat(updStatus == 403 || updStatus == 404).isTrue();

        var del = mvc.perform(delete("/api/students/4")
                .header("Authorization", "Bearer " + token))
                .andReturn();
        int delStatus = del.getResponse().getStatus();
        assertThat(delStatus == 403 || delStatus == 404).isTrue();

        // Confirm student 4 still exists in the repository (delete should not have removed it)
        assertThat(studentRepository.findById(4L).isPresent()).isTrue();
    }

    @Test
    public void duplicateStudentCodeInSameLibraryReturnsConflict() throws Exception {
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        String dupCode = "DUP-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> req = Map.of(
                "studentCode", dupCode,
                "firstName", "Dup",
                "joiningDate", "2026-08-01"
        );

        // first create should succeed
        mvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // second create with same code should fail with 409
        mvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }


    @Test
    public void nonExistentStudentReturns404NotInternalError() throws Exception {
        // Reproduces the reported request exactly: valid JWT + X-Library-Id header,
        // student id that does not exist. Must be a 404 business error, never INTERNAL_ERROR.
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        mvc.perform(get("/api/students/1001111")
                .header("Authorization", "Bearer " + token)
                .header("X-Library-Id", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Student not found"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void crossTenantStudentGetIsForbidden() throws Exception {
        // manager1 belongs to library 1; student 4 belongs to library 2 (seed data).
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        mvc.perform(get("/api/students/4")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void headerSpoofingCannotBypassMembershipOnGet() throws Exception {
        // X-Library-Id must not be trusted as a substitute for server-side membership.
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        mvc.perform(get("/api/students/4")
                .header("Authorization", "Bearer " + token)
                .header("X-Library-Id", "2") // attempt to spoof into library 2
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void unmappedStudentSubPathReturns404NotInternalError() throws Exception {
        String token = loginAndGetAccessToken("manager1@brightfuture.example", "Password@123");

        mvc.perform(get("/api/students/1/does-not-exist")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
