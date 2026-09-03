package com.librarysaas.membership;

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
 * Phase 2D student memberships, exercised end to end against the seeded
 * Testcontainers MySQL with real JWTs.
 *
 * Seed data these tests rely on (V1__initial_schema.sql):
 *   library 1 (org 1) -> students 1, 2, 3 ; memberships 1 (MEM001, student 1),
 *                        2 (MEM002, student 2), 3 (MEM003, student 3, ends 2026-08-31)
 *   library 2 (org 1) -> student 4        ; membership 4 (MEM001)
 *   library 3 (org 2) -> student 5        ; membership 5 (MEM001)
 *
 *   user 1 superadmin -> libraries 1, 2, 3
 *   user 2 owner1     -> libraries 1, 2
 *   user 3 manager1   -> library 1 only
 *
 * There is no delete endpoint, so rows created here persist for the rest of the
 * run. Every created membership therefore uses a far-future period (2030+) that
 * cannot overlap the seeded 2026 periods, and list assertions count with
 * "at least" rather than an exact size.
 */
public class StudentMembershipIntegrationTest extends com.librarysaas.IntegrationTestBase {

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

    /** manager1 belongs to library 1 only, which makes them the cross-tenant subject. */
    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private String createBody(long studentId, String number, String start, String end,
                              Boolean autoRenew) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        body.put("membershipNumber", number);
        body.put("startDate", start);
        body.put("endDate", end);
        if (autoRenew != null) {
            body.put("autoRenew", autoRenew);
        }
        return mapper.writeValueAsString(body);
    }

    private String periodBody(String number, String start, String end, Boolean autoRenew)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("membershipNumber", number);
        body.put("startDate", start);
        body.put("endDate", end);
        if (autoRenew != null) {
            body.put("autoRenew", autoRenew);
        }
        return mapper.writeValueAsString(body);
    }

    private String statusBody(String value) throws Exception {
        return mapper.writeValueAsString(Map.of("status", value));
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).at("/data");
    }

    /* ------------------------------------------------------------------ list */

    @Test
    public void listsLibraryMembershipsWithStudentDetail() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode items = data(res.getResponse().getContentAsString());
        assertThat(items.size()).isGreaterThanOrEqualTo(3);

        for (JsonNode item : items) {
            assertThat(item.get("libraryId").asLong()).isEqualTo(1L);
            assertThat(item.get("studentName").asText()).isNotBlank();
            assertThat(item.get("studentCode").asText()).isNotBlank();
            assertThat(item.get("membershipNumber").asText()).isNotBlank();
        }
    }

    @Test
    public void memberListNeverExposesStudentContactDetail() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        // The response carries a label for the student, not the student record.
        assertThat(body).doesNotContain("dateOfBirth");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");
    }

    @Test
    public void filtersLibraryMembershipsByStatus() throws Exception {
        String token = ownerToken();

        var active = mvc.perform(get("/api/libraries/1/student-memberships")
                        .param("status", "active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = data(active.getResponse().getContentAsString());
        assertThat(items.size()).isGreaterThanOrEqualTo(3);
        for (JsonNode item : items) {
            assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
        }

        // Library 1 has no cancelled memberships in the seed.
        var cancelled = mvc.perform(get("/api/libraries/1/student-memberships")
                        .param("status", "CANCELLED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode item : data(cancelled.getResponse().getContentAsString())) {
            assertThat(item.get("status").asText()).isEqualTo("CANCELLED");
        }
    }

    @Test
    public void unknownStatusFilterIsABusinessErrorNotAnInternalError() throws Exception {
        mvc.perform(get("/api/libraries/1/student-memberships")
                        .param("status", "NONSENSE")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_STATUS"));
    }

    @Test
    public void listsOneStudentsMembershipHistory() throws Exception {
        var res = mvc.perform(get("/api/students/1/memberships")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = data(res.getResponse().getContentAsString());
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : items) {
            assertThat(item.get("studentId").asLong()).isEqualTo(1L);
        }
    }

    @Test
    public void membershipHistoryForAnUnknownStudentIs404() throws Exception {
        mvc.perform(get("/api/students/999999/memberships")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void getsOneMembership() throws Exception {
        mvc.perform(get("/api/student-memberships/1")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipId").value(1))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andExpect(jsonPath("$.data.studentId").value(1))
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").exists());
    }

    @Test
    public void unknownMembershipIs404() throws Exception {
        mvc.perform(get("/api/student-memberships/999999")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_MEMBERSHIP_NOT_FOUND"));
    }

    @Test
    public void unknownLibraryIs404() throws Exception {
        mvc.perform(get("/api/libraries/999999/student-memberships")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    /* ---------------------------------------------------------------- create */

    @Test
    public void createsAMembershipForAStudentOfTheLibrary() throws Exception {
        var res = mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM-CREATE-1", "2030-01-01", "2030-06-30", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentId").value(3))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.autoRenew").value(true))
                .andExpect(jsonPath("$.data.startDate").value("2030-01-01"))
                .andExpect(jsonPath("$.data.endDate").value("2030-06-30"))
                .andReturn();

        long id = data(res.getResponse().getContentAsString()).get("membershipId").asLong();
        assertThat(id).isPositive();

        // It is readable straight away through the single-resource endpoint.
        mvc.perform(get("/api/student-memberships/" + id)
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM-CREATE-1"));
    }

    @Test
    public void duplicateMembershipNumberInTheSameLibraryIsAConflict() throws Exception {
        // MEM001 is seeded in library 1.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM001", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_NUMBER_ALREADY_EXISTS"));
    }

    /** The number is unique per library, so the same one is free in another. */
    @Test
    public void theSameMembershipNumberIsAllowedInADifferentLibrary() throws Exception {
        // MEM002 exists in library 1 only; library 2's student 4 may reuse it.
        mvc.perform(post("/api/libraries/2/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(4, "MEM002", "2030-01-01", "2030-06-30", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.libraryId").value(2))
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM002"));
    }

    @Test
    public void overlappingAnActiveMembershipIsAConflict() throws Exception {
        // Student 1 is seeded ACTIVE for 2026-01-10 to 2026-12-31.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(1, "MEM-OVERLAP-1", "2026-06-01", "2026-07-01", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_MEMBERSHIP_OVERLAP"));

        // Touching at the boundary still overlaps: the range is closed at both ends.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(1, "MEM-OVERLAP-2", "2026-12-31", "2027-06-30", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_MEMBERSHIP_OVERLAP"));
    }

    @Test
    public void anEndDateOnOrBeforeTheStartDateIsRejected() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM-BADPERIOD-1", "2030-06-30", "2030-01-01", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_PERIOD"));

        // A zero-length membership entitles the student to nothing.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM-BADPERIOD-2", "2030-01-01", "2030-01-01", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_PERIOD"));
    }

    @Test
    public void createForAnUnknownStudentIs404() throws Exception {
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(999999, "MEM-NOSTUDENT", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void createRequiresEveryMandatoryField() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.studentId").exists())
                .andExpect(jsonPath("$.data.membershipNumber").exists())
                .andExpect(jsonPath("$.data.startDate").exists())
                .andExpect(jsonPath("$.data.endDate").exists());

        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(0, "MEM-BAD", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.studentId").exists());
    }

    /* ------------------------------------------------------ tenant boundary */

    /**
     * The rule that matters most here: a membership may never join a student to
     * a library the student does not belong to.
     *
     * The caller is the super admin deliberately. A super admin's tenant access
     * is unrestricted, so this is exactly the case where a check written against
     * the caller's privileges rather than the student's own row would wrongly
     * pass. That is the Phase 2C defect, and it must not recur here.
     */
    @Test
    public void aMembershipCannotAttachAStudentFromAnotherLibrary() throws Exception {
        // Student 4 belongs to library 2; the membership is requested in library 1.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(4, "MEM-CROSS-1", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));

        // Student 5 is in library 3, under a different organization entirely.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(5, "MEM-CROSS-2", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));

        // Nothing was written: library 1 still holds no membership for student 4.
        var res = mvc.perform(get("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode item : data(res.getResponse().getContentAsString())) {
            assertThat(item.get("studentId").asLong()).isNotEqualTo(4L);
            assertThat(item.get("studentId").asLong()).isNotEqualTo(5L);
        }
    }

    /** The same rule holds for an ordinary tenant-scoped caller. */
    @Test
    public void anOwnerCannotAttachAStudentFromTheirOtherLibraryEither() throws Exception {
        // owner1 belongs to both library 1 and library 2, so authorisation passes
        // and only the student-to-library rule can stop this.
        mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(4, "MEM-CROSS-3", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));
    }

    /* ---------------------------------------------------------------- update */

    @Test
    public void updatesThePeriodAndBumpsTheVersion() throws Exception {
        String token = ownerToken();

        var before = mvc.perform(get("/api/student-memberships/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long versionBefore = data(before.getResponse().getContentAsString()).get("version").asLong();

        var res = mvc.perform(put("/api/student-memberships/3")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM003", "2026-02-15", "2026-09-30", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value("2026-09-30"))
                .andExpect(jsonPath("$.data.autoRenew").value(true))
                .andReturn();

        // @Version is mapped, so a write moves the version on. The write is
        // flushed before the response is built, so the value a client would use
        // for its next optimistic update is the current one, not a stale one.
        long versionAfter = data(res.getResponse().getContentAsString()).get("version").asLong();
        assertThat(versionAfter).isGreaterThan(versionBefore);

        // And that is genuinely what was persisted, not just what was returned.
        var reread = mvc.perform(get("/api/student-memberships/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(reread.getResponse().getContentAsString()).get("version").asLong())
                .isEqualTo(versionAfter);

        // Restore the seeded period.
        mvc.perform(put("/api/student-memberships/3")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM003", "2026-02-15", "2026-08-31", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value("2026-08-31"));
    }

    @Test
    public void updateCannotTakeANumberUsedByAnotherMembershipInTheLibrary() throws Exception {
        // MEM001 belongs to membership 1 in library 1.
        mvc.perform(put("/api/student-memberships/3")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM001", "2026-02-15", "2026-08-31", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_NUMBER_ALREADY_EXISTS"));
    }

    /** Keeping its own number must not read as a clash with itself. */
    @Test
    public void updateMayKeepItsOwnMembershipNumber() throws Exception {
        mvc.perform(put("/api/student-memberships/2")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM002", "2026-02-01", "2026-12-31", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM002"));
    }

    @Test
    public void updateRejectsAnInvalidPeriod() throws Exception {
        mvc.perform(put("/api/student-memberships/3")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM003", "2026-08-31", "2026-02-15", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_PERIOD"));
    }

    @Test
    public void updateOfAnUnknownMembershipIs404() throws Exception {
        mvc.perform(put("/api/student-memberships/999999")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-NOPE", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_MEMBERSHIP_NOT_FOUND"));
    }

    /* ---------------------------------------------------------------- status */

    @Test
    public void cancelsAndReactivatesAMembershipWithoutLosingTheRow() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/student-memberships/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                // Cancelling keeps the dates, unlike a delete.
                .andExpect(jsonPath("$.data.startDate").value("2026-02-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-12-31"));

        mvc.perform(put("/api/student-memberships/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    public void settingTheStatusItAlreadyHoldsChangesNothing() throws Exception {
        mvc.perform(put("/api/student-memberships/1/status")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    public void unknownStatusIsABusinessErrorNotAnInternalError() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/student-memberships/1/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("SUSPENDED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_STATUS"));

        mvc.perform(put("/api/student-memberships/1/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.status").exists());
    }

    /**
     * Status must not become a way around the overlap rule: reactivating a
     * cancelled membership has to re-check the period.
     */
    @Test
    public void reactivatingCannotProduceTwoOverlappingActiveMemberships() throws Exception {
        String token = ownerToken();

        // Cancel student 2's seeded membership, then take its period with a new one.
        mvc.perform(put("/api/student-memberships/2/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isOk());
        try {
            var created = mvc.perform(post("/api/libraries/1/student-memberships")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(2, "MEM-REACT-1", "2026-02-01", "2026-12-31", false)))
                    .andExpect(status().isCreated())
                    .andReturn();
            long replacementId = data(created.getResponse().getContentAsString())
                    .get("membershipId").asLong();

            // Reactivating the original would now double-book the same period.
            mvc.perform(put("/api/student-memberships/2/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody("ACTIVE")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("STUDENT_MEMBERSHIP_OVERLAP"));

            // Stand the replacement down so the seeded membership can be restored.
            mvc.perform(put("/api/student-memberships/" + replacementId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody("CANCELLED")))
                    .andExpect(status().isOk());
        } finally {
            mvc.perform(put("/api/student-memberships/2/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody("ACTIVE")))
                    .andExpect(status().isOk());
        }
    }

    /* ----------------------------------------------------------------- renew */

    @Test
    public void renewCreatesASuccessorAndClosesThePrevious() throws Exception {
        String token = ownerToken();

        var created = mvc.perform(post("/api/libraries/1/student-memberships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM-RENEW-1", "2030-07-01", "2030-09-30", false)))
                .andExpect(status().isCreated())
                .andReturn();
        long previousId = data(created.getResponse().getContentAsString()).get("membershipId").asLong();

        var renewed = mvc.perform(post("/api/student-memberships/" + previousId + "/renew")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-RENEW-2", "2030-10-01", "2030-12-31", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM-RENEW-2"))
                .andExpect(jsonPath("$.data.startDate").value("2030-10-01"))
                .andExpect(jsonPath("$.data.autoRenew").value(true))
                .andReturn();

        JsonNode successor = data(renewed.getResponse().getContentAsString());
        long successorId = successor.get("membershipId").asLong();

        // A successor, not a rewrite of the old row.
        assertThat(successorId).isNotEqualTo(previousId);
        // The student and the library are inherited, never taken from the body.
        assertThat(successor.get("studentId").asLong()).isEqualTo(3L);
        assertThat(successor.get("libraryId").asLong()).isEqualTo(1L);

        // History is preserved: the previous period keeps its dates and is closed.
        mvc.perform(get("/api/student-memberships/" + previousId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.startDate").value("2030-07-01"))
                .andExpect(jsonPath("$.data.endDate").value("2030-09-30"))
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM-RENEW-1"));

        // Tidy up so the far-future window stays free for other tests.
        mvc.perform(put("/api/student-memberships/" + successorId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isOk());
    }

    @Test
    public void renewRejectsADuplicateNumberAndAnInvalidPeriod() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/student-memberships/1/renew")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM002", "2027-01-01", "2027-12-31", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_NUMBER_ALREADY_EXISTS"));

        mvc.perform(post("/api/student-memberships/1/renew")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-RENEW-BAD", "2027-12-31", "2027-01-01", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_PERIOD"));

        // Neither attempt closed the membership being renewed.
        mvc.perform(get("/api/student-memberships/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    public void renewOfAnUnknownMembershipIs404() throws Exception {
        mvc.perform(post("/api/student-memberships/999999/renew")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-NOPE", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_MEMBERSHIP_NOT_FOUND"));
    }

    /* --------------------------------------------------------- authorisation */

    @Test
    public void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/libraries/1/student-memberships"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mvc.perform(get("/api/student-memberships/1"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/libraries/1/student-memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM-ANON", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isUnauthorized());
    }

    /** manager1 belongs to library 1 only. Library 2 is another tenant to them. */
    @Test
    public void crossTenantReadsAreForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/2/student-memberships")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Membership 4 belongs to library 2.
        mvc.perform(get("/api/student-memberships/4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Student 4 belongs to library 2.
        mvc.perform(get("/api/students/4/memberships")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void crossTenantWritesAreForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(post("/api/libraries/2/student-memberships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(4, "MEM-XT-1", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(put("/api/student-memberships/4")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-XT-2", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(put("/api/student-memberships/4/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("CANCELLED")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(post("/api/student-memberships/4/renew")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-XT-3", "2030-01-01", "2030-12-31", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Membership 4 is untouched.
        mvc.perform(get("/api/student-memberships/4")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.membershipNumber").value("MEM001"));
    }

    /**
     * An unreachable tenant and a missing one must not be distinguishable by a
     * different error shape.
     */
    @Test
    public void unreachableAndMissingResourcesStayNonInformative() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/2/student-memberships")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/libraries/999999/student-memberships")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /* ------------------------------------------------- error-shape guarantee */

    /**
     * Every expected failure has to arrive as its own business error. A 500 with
     * INTERNAL_ERROR here would mean a rule is throwing something the handler
     * does not recognise.
     */
    @Test
    public void noExpectedErrorEverBecomesAnInternalError() throws Exception {
        String token = ownerToken();
        String bearer = "Bearer " + token;

        var responses = new java.util.ArrayList<String>();

        responses.add(mvc.perform(get("/api/libraries/999999/student-memberships").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/student-memberships/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/students/999999/memberships").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/student-memberships")
                        .param("status", "NONSENSE").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-memberships").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-memberships").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-memberships").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(4, "MEM-IE-1", "2030-01-01", "2030-12-31", false)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-memberships").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(1, "MEM-IE-2", "2026-06-01", "2026-07-01", false)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-memberships").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(3, "MEM001", "2030-01-01", "2030-12-31", false)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(put("/api/student-memberships/1/status").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("NONSENSE")))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/student-memberships/1/renew").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodBody("MEM-IE-3", "2027-12-31", "2027-01-01", false)))
                .andReturn().getResponse().getContentAsString());

        for (String body : responses) {
            assertThat(body).doesNotContain("INTERNAL_ERROR");
            assertThat(body).doesNotContain("Unable to process the request");
        }
    }
}
