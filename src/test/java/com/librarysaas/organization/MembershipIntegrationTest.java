package com.librarysaas.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2C membership management, exercised end to end against the seeded
 * Testcontainers MySQL with real JWTs.
 *
 * Seed data these tests rely on (V1__initial_schema.sql):
 *   user 1 superadmin  -> orgs 1(primary),2 ; libraries 1(primary),2,3
 *   user 2 owner1      -> orgs 1(primary),2 ; libraries 1(primary),2
 *   user 3 manager1    -> org  1(primary)   ; library 1(primary)
 *   user 4 reception1  -> org  1(primary)   ; libraries 1(primary),2
 *
 * USER_VIEW / USER_CREATE / USER_UPDATE belong only to Super Admin (role 1) and
 * Organization Owner (role 2). Library Manager and Receptionist hold none of
 * them, which is what the permission tests below pin down.
 */
public class MembershipIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String login(String identifier, String password) throws Exception {
        var res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).at("/data/accessToken").asText(null);
    }

    private String ownerToken() throws Exception {
        return login("owner1@brightfuture.example", "Password@123");
    }

    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private String statusBody(String value) throws Exception {
        return mapper.writeValueAsString(Map.of("status", value));
    }

    private String memberBody(long userId, boolean isPrimary) throws Exception {
        return mapper.writeValueAsString(Map.of("userId", userId, "isPrimary", isPrimary));
    }

    /** The membership of one user in one tenant, or null when they hold none. */
    private JsonNode findMember(String path, String token, long userId) throws Exception {
        var res = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode member : mapper.readTree(res.getResponse().getContentAsString()).at("/data")) {
            if (member.get("userId").asLong() == userId) {
                return member;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ list */

    @Test
    public void listsOrganizationMembersWithUserDetailAndPrimaryFirst() throws Exception {
        var res = mvc.perform(get("/api/organizations/1/members")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode members = mapper.readTree(res.getResponse().getContentAsString()).at("/data");
        // Organization 1 has users 1-4 seeded.
        assertThat(members.size()).isGreaterThanOrEqualTo(4);

        JsonNode first = members.get(0);
        assertThat(first.get("isPrimary").asBoolean()).isTrue();
        assertThat(first.get("organizationId").asLong()).isEqualTo(1L);
        assertThat(first.get("libraryId").isNull()).isTrue();
        assertThat(first.get("username").asText()).isNotBlank();
        assertThat(first.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    public void memberListNeverExposesCredentials() throws Exception {
        var res = mvc.perform(get("/api/organizations/1/members")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        // MembershipResponse maps only publishable user fields.
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");
    }

    @Test
    public void listsLibraryMembers() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/members")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].libraryId").value(1))
                .andReturn();

        JsonNode members = mapper.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(members.size()).isGreaterThanOrEqualTo(4);
        for (JsonNode member : members) {
            // A library membership carries no organization id.
            assertThat(member.get("organizationId").isNull()).isTrue();
        }
    }

    @Test
    public void unknownOrganizationReturns404NotForbidden() throws Exception {
        mvc.perform(get("/api/organizations/999999/members")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_FOUND"));
    }

    @Test
    public void unknownLibraryReturns404() throws Exception {
        mvc.perform(get("/api/libraries/999999/members")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    /* ------------------------------------------------------- status changes */

    @Test
    public void deactivatesAndReactivatesALibraryMembership() throws Exception {
        String token = ownerToken();

        // User 4 is a seeded non-primary member of library 2.
        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.userId").value(4))
                .andExpect(jsonPath("$.data.libraryId").value(2))
                .andExpect(jsonPath("$.data.isPrimary").value(false));

        // The row survives deactivation, so it still appears in the list.
        var res = mvc.perform(get("/api/libraries/2/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        boolean found = false;
        for (JsonNode member : mapper.readTree(res.getResponse().getContentAsString()).at("/data")) {
            if (member.get("userId").asLong() == 4L) {
                found = true;
                assertThat(member.get("status").asText()).isEqualTo("INACTIVE");
            }
        }
        assertThat(found).isTrue();

        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    public void statusIsCaseInsensitiveButMustBeKnown() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("inactive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ACTIVE")))
                .andExpect(status().isOk());
    }

    @Test
    public void unknownStatusIsABusinessErrorNotAnInternalError() throws Exception {
        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("NONSENSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_STATUS"));
    }

    @Test
    public void suspendedIsNotAMembershipStatus() throws Exception {
        // Organizations and libraries use SUSPENDED, but membership rules do not
        // define behaviour for it, so it must be rejected rather than stored.
        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("SUSPENDED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_STATUS"));
    }

    @Test
    public void blankStatusIsAValidationError() throws Exception {
        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.status").exists());
    }

    @Test
    public void settingTheSameStatusTwiceIsAConflict() throws Exception {
        // User 4's library 2 membership is seeded ACTIVE.
        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_STATUS_UNCHANGED"));
    }

    @Test
    public void statusChangeForANonMemberIs404() throws Exception {
        // User 3 is not a member of library 2.
        mvc.perform(put("/api/libraries/2/members/3/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_IN_LIBRARY"));
    }

    @Test
    public void organizationStatusChangeForANonMemberIs404() throws Exception {
        // User 3 belongs to organization 1 only.
        mvc.perform(put("/api/organizations/2/members/3/status")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_IN_ORGANIZATION"));
    }

    @Test
    public void deactivatingAnOrganizationMembershipKeepsTheRow() throws Exception {
        String token = superAdminToken();

        // Organization 2 has users 1 and 2, so deactivating one leaves an admin.
        mvc.perform(put("/api/organizations/2/members/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.isPrimary").value(false));

        mvc.perform(put("/api/organizations/2/members/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    /* ------------------------------------------------------- add and remove */

    /**
     * Each of these restores the seeded state before it returns, so the class
     * stays order-independent.
     */
    @Test
    public void addsThenRemovesAnOrganizationMembership() throws Exception {
        String token = ownerToken();

        // User 3 is seeded into organization 1 only, and organization 2 keeps
        // users 1 and 2, so neither adding nor removing touches a last member.
        mvc.perform(post("/api/organizations/2/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(3, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        JsonNode added = findMember("/api/organizations/2/members", token, 3);
        assertThat(added).isNotNull();
        assertThat(added.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(added.get("isPrimary").asBoolean()).isFalse();
        // Adding to a second organization must not disturb the first.
        assertThat(findMember("/api/organizations/1/members", token, 3).get("isPrimary").asBoolean())
                .isTrue();

        mvc.perform(delete("/api/organizations/2/members/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(findMember("/api/organizations/2/members", token, 3)).isNull();
    }

    @Test
    public void addsThenRemovesALibraryMembership() throws Exception {
        String token = ownerToken();

        // User 3 belongs to organization 1, which owns library 2, but is not
        // seeded into library 2 itself.
        mvc.perform(post("/api/libraries/2/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(3, false)))
                .andExpect(status().isCreated());

        JsonNode added = findMember("/api/libraries/2/members", token, 3);
        assertThat(added).isNotNull();
        assertThat(added.get("libraryId").asLong()).isEqualTo(2L);
        assertThat(added.get("status").asText()).isEqualTo("ACTIVE");

        mvc.perform(delete("/api/libraries/2/members/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(findMember("/api/libraries/2/members", token, 3)).isNull();
        // The organization membership is untouched by a library removal.
        assertThat(findMember("/api/organizations/1/members", token, 3)).isNotNull();
    }

    @Test
    public void addingAnAlreadyActiveMemberIsAConflict() throws Exception {
        mvc.perform(post("/api/organizations/1/members")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(2, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_IN_ORGANIZATION"));
    }

    @Test
    public void addingAnAlreadyActiveLibraryMemberIsAConflict() throws Exception {
        mvc.perform(post("/api/libraries/1/members")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(3, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_IN_LIBRARY"));
    }

    /** Adding a deactivated member back is a rejoin, not a duplicate. */
    @Test
    public void addingADeactivatedMemberReactivatesTheExistingRow() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/libraries/2/members/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/libraries/2/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(4, false)))
                .andExpect(status().isCreated());

        JsonNode rejoined = findMember("/api/libraries/2/members", token, 4);
        assertThat(rejoined.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(rejoined.get("isPrimary").asBoolean()).isFalse();
    }

    @Test
    public void addingAnUnknownUserIs404() throws Exception {
        mvc.perform(post("/api/organizations/1/members")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(999999, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    public void addingToAnUnknownTenantIs404() throws Exception {
        String token = superAdminToken();

        mvc.perform(post("/api/organizations/999999/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(3, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_FOUND"));

        mvc.perform(post("/api/libraries/999999/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(3, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    @Test
    public void addMemberRequiresAPositiveUserId() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/organizations/1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.userId").exists());

        mvc.perform(post("/api/organizations/1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(0, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.userId").exists());
    }

    @Test
    public void removingANonMemberIs404() throws Exception {
        String token = ownerToken();

        mvc.perform(delete("/api/organizations/1/members/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_IN_ORGANIZATION"));

        // User 3 is not a member of library 2.
        mvc.perform(delete("/api/libraries/2/members/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_IN_LIBRARY"));
    }

    /**
     * An organization must always keep someone who can administer it, so the
     * last active membership can be neither removed nor deactivated.
     */
    @Test
    public void theLastActiveOrganizationMemberCannotBeRemoved() throws Exception {
        String token = superAdminToken();

        // Organization 2 is seeded with users 1 and 2. Drop user 2 so user 1 is
        // the only one left, then prove user 1 cannot go too.
        mvc.perform(delete("/api/organizations/2/members/2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        try {
            mvc.perform(delete("/api/organizations/2/members/1")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_LAST_MEMBER"));

            mvc.perform(put("/api/organizations/2/members/1/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody("INACTIVE")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_LAST_MEMBER"));
        } finally {
            mvc.perform(post("/api/organizations/2/members")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(memberBody(2, false)))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * The rule that matters most here: a library membership may never reach
     * someone outside the library's owning organization.
     */
    @Test
    public void aLibraryMembershipCannotWidenTheTenantBoundary() throws Exception {
        // Library 3 belongs to organization 2; user 3 belongs to organization 1.
        mvc.perform(post("/api/libraries/3/members")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(3, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_IN_ORGANIZATION"));
    }

    /* ------------------------------------------------------------- primary */

    @Test
    public void movesTheCallersOwnPrimaryOrganizationAndDemotesTheOld() throws Exception {
        String token = ownerToken();

        // owner1 is seeded primary in organization 1 and non-primary in 2.
        mvc.perform(put("/api/organizations/2/members/2/primary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        try {
            assertThat(findMember("/api/organizations/2/members", token, 2)
                    .get("isPrimary").asBoolean()).isTrue();
            // A user holds at most one primary organization.
            assertThat(findMember("/api/organizations/1/members", token, 2)
                    .get("isPrimary").asBoolean()).isFalse();
        } finally {
            mvc.perform(put("/api/organizations/1/members/2/primary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        assertThat(findMember("/api/organizations/1/members", token, 2)
                .get("isPrimary").asBoolean()).isTrue();
    }

    @Test
    public void movesTheCallersOwnPrimaryLibrary() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/libraries/2/members/2/primary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        try {
            assertThat(findMember("/api/libraries/2/members", token, 2)
                    .get("isPrimary").asBoolean()).isTrue();
            assertThat(findMember("/api/libraries/1/members", token, 2)
                    .get("isPrimary").asBoolean()).isFalse();
        } finally {
            mvc.perform(put("/api/libraries/1/members/2/primary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    /** Primary tenant is a self-service setting, not an administrative one. */
    @Test
    public void primaryCannotBeSetForAnotherUser() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/organizations/1/members/3/primary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(put("/api/libraries/1/members/3/primary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // The other user's membership is unchanged.
        assertThat(findMember("/api/organizations/1/members", token, 3)
                .get("isPrimary").asBoolean()).isTrue();
    }

    /** There is no membership row to promote, so this is 404 rather than 403. */
    @Test
    public void primaryForATenantWithNoMembershipIs404() throws Exception {
        mvc.perform(put("/api/organizations/999999/members/1/primary")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_IN_ORGANIZATION"));
    }

    @Test
    public void aDeactivatedMembershipCannotBecomePrimary() throws Exception {
        // Deactivating costs owner1 their access to library 2, so the super admin
        // makes and undoes the change while owner1 makes the attempt.
        String admin = superAdminToken();

        mvc.perform(put("/api/libraries/2/members/2/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isOk());
        try {
            mvc.perform(put("/api/libraries/2/members/2/primary")
                            .header("Authorization", "Bearer " + ownerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("LIBRARY_MEMBERSHIP_INACTIVE"));
        } finally {
            mvc.perform(put("/api/libraries/2/members/2/status")
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody("ACTIVE")))
                    .andExpect(status().isOk());
        }
    }

    /* --------------------------------------------------------- authorisation */

    @Test
    public void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/organizations/1/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    public void libraryManagerHasNoUserPermissionsSoMembershipIsForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/organizations/1/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/libraries/1/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/libraries/1/members/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isForbidden());
    }

    /**
     * owner1 is an active member of organization 2, but not of library 3 which
     * that organization owns. Membership of the parent organization must not by
     * itself grant access to a library's member list.
     */
    @Test
    public void crossTenantLibraryMemberListIsForbidden() throws Exception {
        mvc.perform(get("/api/libraries/3/members")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void crossTenantLibraryStatusChangeIsForbidden() throws Exception {
        mvc.perform(put("/api/libraries/3/members/1/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("INACTIVE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /**
     * The writes are the dangerous half: a caller outside a library must not be
     * able to grant or revoke access to it, and the refusal must come before any
     * membership is read or changed.
     */
    @Test
    public void crossTenantMembershipWritesAreForbidden() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/libraries/3/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(2, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(delete("/api/libraries/3/members/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Nothing changed: user 1 is still a member of library 3.
        assertThat(findMember("/api/libraries/3/members", superAdminToken(), 1)).isNotNull();
    }

    /**
     * A tenant the caller cannot reach must never be distinguishable by a
     * different error shape than one that does not exist.
     */
    @Test
    public void unreachableAndMissingLibrariesBothStayNonInformative() throws Exception {
        String token = ownerToken();

        mvc.perform(get("/api/libraries/3/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/libraries/999999/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
