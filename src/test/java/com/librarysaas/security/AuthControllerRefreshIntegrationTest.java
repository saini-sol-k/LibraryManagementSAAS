package com.librarysaas.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.librarysaas.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerRefreshIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    public void loginRefreshLogoutFlow() throws Exception {
        // 1) Login with seeded user (email) and known password from V1
        var loginReq = Map.of("identifier", "superadmin@example.com", "password", "Password@123");

        var loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String loginBody = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = mapper.readTree(loginBody);
        String accessToken = loginJson.at("/data/accessToken").asText(null);
        String refreshToken = loginJson.at("/data/refreshToken").asText(null);

        assertThat(accessToken).isNotNull();
        assertThat(refreshToken).isNotNull();

        // 2) Refresh: rotate the refresh token and receive new access + refresh
        var refreshReq = Map.of("refreshToken", refreshToken);

        var refreshResult = mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshBody = refreshResult.getResponse().getContentAsString();
        JsonNode refreshJson = mapper.readTree(refreshBody);
        String newAccess = refreshJson.at("/data/accessToken").asText(null);
        String newRefresh = refreshJson.at("/data/refreshToken").asText(null);

        assertThat(newAccess).isNotNull();
        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // 3) Attempt to reuse old refresh token (should be invalid / 401)
        mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        // 4) Logout (revoke) the new refresh token
        mvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isOk());

        // 5) After logout, refresh with newRefresh must fail
        mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void refreshProvidesNewAccessTokenThatWorksAgainstStudentApi() throws Exception {
        // Login as seeded manager who belongs to library 1
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

        // Refresh to obtain a new access token
        var refreshReq = Map.of("refreshToken", refreshToken);

        var refreshResult = mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshJson = mapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccess = refreshJson.at("/data/accessToken").asText(null);
        String newRefresh = refreshJson.at("/data/refreshToken").asText(null);

        assertThat(newAccess).isNotNull();
        assertThat(newRefresh).isNotNull();

        // Use the new access token against the Student API to verify tenant resolution and auth
        mvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }
}
