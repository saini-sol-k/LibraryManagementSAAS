package com.librarysaas.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerRefreshTenantIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    public void refreshedAccessTokenResolvesTenantAndEnforcesMembershipAndPermissions() throws Exception {
        // Use manager1 who belongs to library 1 and has STUDENT_VIEW permission
        var loginReq = Map.of("identifier", "manager1@brightfuture.example", "password", "Password@123");

        var loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = mapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.at("/data/accessToken").asText(null);
        String refreshToken = loginJson.at("/data/refreshToken").asText(null);

        assertThat(accessToken).isNotNull();
        assertThat(refreshToken).isNotNull();

        // Access student in library 1 (student id 1) with access token -> allowed
        var r1 = mvc.perform(get("/api/students/1")
                .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        if (r1.getResolvedException() != null) {
            // Re-throw to surface full stack trace in test output
            throw new RuntimeException("Request to /api/students/1 failed", r1.getResolvedException());
        }

        JsonNode s1 = mapper.readTree(r1.getResponse().getContentAsString());
        assertThat(s1.at("/data/id").asLong()).isEqualTo(1L);

        // Refresh tokens -> rotate
        var refreshResult = mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshJson = mapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccess = refreshJson.at("/data/accessToken").asText(null);
        String newRefresh = refreshJson.at("/data/refreshToken").asText(null);

        assertThat(newAccess).isNotNull();
        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // Use new access token to access same student -> allowed
        var r2 = mvc.perform(get("/api/students/1")
                .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode s2 = mapper.readTree(r2.getResponse().getContentAsString());
        assertThat(s2.at("/data/id").asLong()).isEqualTo(1L);

        // Attempt to access student in library 2 (student id 4) with refreshed token -> forbidden (403)
        mvc.perform(get("/api/students/4")
                .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isForbidden());

        // Attempt to spoof header X-Library-Id to bypass (set to 1) while requesting student 4 -> still forbidden
        mvc.perform(get("/api/students/4")
                .header("Authorization", "Bearer " + newAccess)
                .header("X-Library-Id", "1"))
                .andExpect(status().isForbidden());
    }
}
