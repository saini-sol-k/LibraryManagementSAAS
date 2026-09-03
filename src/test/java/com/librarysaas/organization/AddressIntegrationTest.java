package com.librarysaas.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2A address management, exercised end to end against the seeded
 * Testcontainers MySQL with real JWTs.
 *
 * Seed data these tests rely on:
 *   manager1 -> library 1 (organization 1), no ORGANIZATION_VIEW
 *   superadmin -> all tenants
 *   students 1-3 in library 1, student 4 in library 2, student 5 in library 3
 */
public class AddressIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String login(String identifier, String password) throws Exception {
        var res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).at("/data/accessToken").asText(null);
    }

    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private Map<String, Object> address(String line1, String type, boolean primary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressLine1", line1);
        body.put("city", "Saharanpur");
        body.put("state", "Uttar Pradesh");
        body.put("postalCode", "247001");
        body.put("country", "India");
        if (type != null) body.put("addressType", type);
        body.put("isPrimary", primary);
        return body;
    }

    /* ------------------------------------------------------- happy path */

    @Test
    public void createAndRetrieveStudentAddress() throws Exception {
        String token = managerToken();

        var created = mvc.perform(post("/api/students/1/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("12 Test Lane", "PERMANENT", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.addressLine1").value("12 Test Lane"))
                .andExpect(jsonPath("$.data.addressType").value("PERMANENT"))
                .andExpect(jsonPath("$.data.isPrimary").value(true))
                .andReturn();

        long addressId = mapper.readTree(created.getResponse().getContentAsString())
                .at("/data/addressId").asLong();

        // Retrieval returns what was written.
        var listed = mvc.perform(get("/api/students/1/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode body = mapper.readTree(listed.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode node : body.at("/data")) {
            if (node.get("addressId").asLong() == addressId) found = true;
        }
        assertThat(found).isTrue();

        // Update.
        mvc.perform(put("/api/students/1/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("12 Updated Lane", "PERMANENT", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressLine1").value("12 Updated Lane"));

        // Clean up so the test is repeatable.
        mvc.perform(delete("/api/students/1/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * V1__initial_schema.sql seeds every student address as HOME, so HOME must
     * be a recognised type. Before this was fixed the API answered 400
     * INVALID_ADDRESS_TYPE, which made the seeded type impossible to create.
     * Found by exercising the API against the seeded database rather than only
     * against addresses the test had created itself.
     */
    @Test
    public void homeIsARecognisedStudentAddressType() throws Exception {
        mvc.perform(post("/api/students/1/addresses")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("1 Home Road", "HOME", false))))
                // Rejected for being a duplicate, never for being an unknown type.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_TYPE_ALREADY_EXISTS"));
    }

    /** Omitting the type falls back to HOME, the type the seed data uses. */
    @Test
    public void omittedStudentAddressTypeDefaultsToHome() throws Exception {
        mvc.perform(post("/api/students/1/addresses")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("1 Default Road", null, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_TYPE_ALREADY_EXISTS"));
    }

    /** A seeded HOME address must stay editable. Restores what it changed. */
    @Test
    public void seededHomeAddressRemainsEditable() throws Exception {
        String token = managerToken();

        var before = mvc.perform(get("/api/students/2/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = mapper.readTree(before.getResponse().getContentAsString()).at("/data/0");
        long addressId = seeded.get("addressId").asLong();
        assertThat(seeded.get("addressType").asText()).isEqualTo("HOME");

        Map<String, Object> edit = new LinkedHashMap<>();
        seeded.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull()) edit.put(entry.getKey(), entry.getValue().asText());
        });
        edit.remove("addressId");
        edit.remove("createdAt");
        edit.remove("updatedAt");
        edit.put("isPrimary", seeded.get("isPrimary").asBoolean());

        Map<String, Object> changed = new LinkedHashMap<>(edit);
        changed.put("addressLine1", "Seeded Line Edited");

        mvc.perform(put("/api/students/2/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressLine1").value("Seeded Line Edited"))
                // The link keys on the type, so it survives the edit unchanged.
                .andExpect(jsonPath("$.data.addressType").value("HOME"));

        // Put the seeded row back the way it was.
        mvc.perform(put("/api/students/2/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressLine1").value(seeded.get("addressLine1").asText()));
    }
    /* ------------------------------------------------------- validation */

    @Test
    public void missingRequiredFieldsReturnValidationError() throws Exception {
        mvc.perform(post("/api/students/1/addresses")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.addressLine1").exists())
                .andExpect(jsonPath("$.data.city").exists())
                .andExpect(jsonPath("$.data.state").exists())
                .andExpect(jsonPath("$.data.postalCode").exists());
    }

    @Test
    public void invalidAddressTypeIsRejectedAsBusinessError() throws Exception {
        mvc.perform(post("/api/students/1/addresses")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("1 Bad Type Road", "NONSENSE", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADDRESS_TYPE"));
    }

    /* -------------------------------------------------------- not found */

    @Test
    public void addressNotFoundForStudentReturns404() throws Exception {
        mvc.perform(put("/api/students/1/addresses/999999")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("1 Nowhere", "CURRENT", false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    public void missingStudentReturns404() throws Exception {
        mvc.perform(get("/api/students/1001111/addresses")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    /* ----------------------------------------------------- authorisation */

    @Test
    public void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/students/1/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    public void crossTenantStudentAddressIsForbidden() throws Exception {
        // manager1 belongs to library 1; student 4 belongs to library 2.
        mvc.perform(get("/api/students/4/addresses")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void crossTenantWriteIsForbidden() throws Exception {
        mvc.perform(post("/api/students/4/addresses")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("9 Other Tenant Road", "CURRENT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    public void crossTenantLibraryAddressIsForbidden() throws Exception {
        // Library 3 belongs to organization 2; manager1 is not a member.
        mvc.perform(get("/api/libraries/3/addresses")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void organizationAddressRequiresOrganizationPermission() throws Exception {
        // manager1 has no ORGANIZATION_VIEW, so method security denies this.
        mvc.perform(get("/api/organizations/1/addresses")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isForbidden());
    }

    /* ------------------------------------------- seeded data still visible */

    @Test
    public void superAdminSeesSeededOrganizationAndLibraryAddresses() throws Exception {
        String token = superAdminToken();

        mvc.perform(get("/api/organizations/1/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].addressType").value("BUSINESS"))
                .andExpect(jsonPath("$.data[0].city").value("Saharanpur"));

        mvc.perform(get("/api/libraries/1/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].addressType").value("BUSINESS"));
    }

    @Test
    public void duplicateAddressTypeForSameOwnerIsRejected() throws Exception {
        String token = superAdminToken();

        // Library 1 already has a seeded BUSINESS address.
        mvc.perform(post("/api/libraries/1/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(address("2 Duplicate Road", "BUSINESS", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_TYPE_ALREADY_EXISTS"));
    }
}
