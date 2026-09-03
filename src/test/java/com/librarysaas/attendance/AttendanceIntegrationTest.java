package com.librarysaas.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2E attendance, exercised end to end against the seeded Testcontainers
 * MySQL with real JWTs.
 *
 * Seed data these tests rely on (V1__initial_schema.sql):
 *   library 1 -> students 1, 2, 3 ; seats 1-5
 *   library 2 -> student 4        ; seats 6, 7
 *   library 3 -> student 5        ; seat 8
 *   seat allocations: student 1 -> seat 1, student 2 -> seat 2. Student 3 has none.
 *   attendance 1: student 1, seat 1, today, checked out, COMPLETED
 *   attendance 2: student 2, seat 2, today, still open, PRESENT
 *
 *   user 1 superadmin -> libraries 1, 2, 3
 *   user 2 owner1     -> libraries 1, 2
 *   user 3 manager1   -> library 1 only
 *
 * The seeded rows use CURRENT_DATE, so they are always "today" for the run.
 *
 * Library 1 seeds only three students and several tests need one who is free to
 * be checked in, so each of those calls ensureCheckedOut first. That makes them
 * independent of the order the rest ran in, rather than depending on another
 * test having cleaned up after itself.
 */
public class AttendanceIntegrationTest extends com.librarysaas.IntegrationTestBase {

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

    private String checkInBody(Long studentId, Long seatId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (studentId != null) {
            body.put("studentId", studentId);
        }
        if (seatId != null) {
            body.put("seatId", seatId);
        }
        return mapper.writeValueAsString(body);
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).at("/data");
    }

    /** Closes a visit if it is still open, so a failure cannot strand a student. */
    private void closeQuietly(long attendanceId, String token) {
        try {
            mvc.perform(post("/api/attendance/" + attendanceId + "/check-out")
                    .header("Authorization", "Bearer " + token));
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    /**
     * Closes any visit this student still has open.
     *
     * Several tests need a student who is free to be checked in, and the class
     * shares the three students library 1 seeds. Establishing that as a
     * precondition here keeps each test independent of the order the others ran
     * in, rather than relying on their cleanup having succeeded.
     */
    private void ensureCheckedOut(long studentId, String token) throws Exception {
        var res = mvc.perform(get("/api/students/" + studentId + "/attendance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode row : data(res.getResponse().getContentAsString())) {
            if (row.get("open").asBoolean()) {
                closeQuietly(row.get("attendanceId").asLong(), token);
            }
        }
    }

    /* ------------------------------------------------------------------ list */

    @Test
    public void listsTodaysAttendanceForALibrary() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/attendance")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode rows = data(res.getResponse().getContentAsString());
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);

        for (JsonNode row : rows) {
            assertThat(row.get("libraryId").asLong()).isEqualTo(1L);
            assertThat(row.get("studentName").asText()).isNotBlank();
            assertThat(row.get("attendanceDate").asText()).isEqualTo(LocalDate.now().toString());
        }
    }

    @Test
    public void attendanceListNeverExposesTheStudentRecord() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/attendance")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).doesNotContain("dateOfBirth");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");
    }

    @Test
    public void filtersByStatusAndDistinguishesOpenFromClosed() throws Exception {
        String token = ownerToken();

        var present = mvc.perform(get("/api/libraries/1/attendance")
                        .param("status", "present")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode open = data(present.getResponse().getContentAsString());
        assertThat(open.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode row : open) {
            assertThat(row.get("status").asText()).isEqualTo("PRESENT");
            // An open visit has no check-out time and no duration yet.
            assertThat(row.get("open").asBoolean()).isTrue();
            assertThat(row.get("checkOutTime").isNull()).isTrue();
        }

        var completed = mvc.perform(get("/api/libraries/1/attendance")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode closed = data(completed.getResponse().getContentAsString());
        assertThat(closed.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode row : closed) {
            assertThat(row.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(row.get("open").asBoolean()).isFalse();
            assertThat(row.get("checkOutTime").isNull()).isFalse();
        }
    }

    @Test
    public void aDayWithNoAttendanceIsAnEmptyListNotAnError() throws Exception {
        mvc.perform(get("/api/libraries/1/attendance")
                        .param("date", "2020-01-01")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    public void unknownStatusFilterIsABusinessErrorNotAnInternalError() throws Exception {
        mvc.perform(get("/api/libraries/1/attendance")
                        .param("status", "ABSENT")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ATTENDANCE_STATUS"));
    }

    @Test
    public void aMalformedDateIsARejectedRequestNotAnInternalError() throws Exception {
        mvc.perform(get("/api/libraries/1/attendance")
                        .param("date", "not-a-date")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    public void listsOneStudentsVisitHistory() throws Exception {
        var res = mvc.perform(get("/api/students/1/attendance")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = data(res.getResponse().getContentAsString());
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode row : rows) {
            assertThat(row.get("studentId").asLong()).isEqualTo(1L);
        }
    }

    @Test
    public void historyForAnUnknownStudentIs404() throws Exception {
        mvc.perform(get("/api/students/999999/attendance")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void getsOneAttendanceRecord() throws Exception {
        mvc.perform(get("/api/attendance/1")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendanceId").value(1))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andExpect(jsonPath("$.data.studentId").value(1))
                .andExpect(jsonPath("$.data.seatId").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.durationMinutes").value(180));
    }

    @Test
    public void unknownAttendanceIs404() throws Exception {
        mvc.perform(get("/api/attendance/999999")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ATTENDANCE_NOT_FOUND"));
    }

    @Test
    public void unknownLibraryIs404() throws Exception {
        mvc.perform(get("/api/libraries/999999/attendance")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    /* --------------------------------------------------- check in and out */

    @Test
    public void checksAStudentInAndBackOutRecordingTheDuration() throws Exception {
        String token = ownerToken();
        ensureCheckedOut(3L, token);

        // Student 3 belongs to library 1 and holds no seat allocation.
        var opened = mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(3L, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentId").value(3))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.checkOutTime").doesNotExist())
                .andExpect(jsonPath("$.data.durationMinutes").doesNotExist())
                // No allocation, so no seat is recorded.
                .andExpect(jsonPath("$.data.seatId").doesNotExist())
                .andReturn();

        long id = data(opened.getResponse().getContentAsString()).get("attendanceId").asLong();
        try {
            // The open visit shows up in today's list.
            var today = mvc.perform(get("/api/libraries/1/attendance")
                            .param("status", "PRESENT")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            boolean found = false;
            for (JsonNode row : data(today.getResponse().getContentAsString())) {
                if (row.get("attendanceId").asLong() == id) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        } finally {
            mvc.perform(post("/api/attendance/" + id + "/check-out")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.open").value(false))
                    .andExpect(jsonPath("$.data.checkOutTime").exists())
                    .andExpect(jsonPath("$.data.durationMinutes").exists());
        }

        // Duration is derived, never negative, and the row survives as history.
        var closed = mvc.perform(get("/api/attendance/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode row = data(closed.getResponse().getContentAsString());
        assertThat(row.get("durationMinutes").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(row.get("checkInTime").asText()).isNotBlank();
    }

    @Test
    public void checkInRecordsAnExplicitSeatOfThisLibrary() throws Exception {
        String token = ownerToken();
        ensureCheckedOut(3L, token);

        // Seat 3 belongs to library 1.
        var opened = mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(3L, 3L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.seatId").value(3))
                .andExpect(jsonPath("$.data.seatNumber").value("A003"))
                .andReturn();

        closeQuietly(data(opened.getResponse().getContentAsString()).get("attendanceId").asLong(), token);
    }

    @Test
    public void aStudentCannotBeCheckedInTwice() throws Exception {
        // Student 2's seeded visit is still open.
        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(2L, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_ALREADY_CHECKED_IN"));
    }

    @Test
    public void aClosedVisitCannotBeCheckedOutAgain() throws Exception {
        // Attendance 1 is seeded COMPLETED.
        mvc.perform(post("/api/attendance/1/check-out")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTENDANCE_ALREADY_CLOSED"));
    }

    @Test
    public void checkOutOfAnUnknownVisitIs404() throws Exception {
        mvc.perform(post("/api/attendance/999999/check-out")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ATTENDANCE_NOT_FOUND"));
    }

    @Test
    public void checkInForAnUnknownStudentIs404() throws Exception {
        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(999999L, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void checkInIntoAnUnknownLibraryIs404() throws Exception {
        mvc.perform(post("/api/libraries/999999/attendance/check-in")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(3L, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    @Test
    public void checkInRequiresAPositiveStudentId() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.studentId").exists());

        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(0L, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.studentId").exists());
    }

    /* ------------------------------------------------------ tenant boundary */

    /**
     * The rule that matters most here: a visit may never record a student in a
     * library the student does not belong to.
     *
     * The caller is the super admin deliberately. A super admin's tenant access
     * is unrestricted, so this is exactly the case where a check written against
     * the caller's privileges rather than the student's own row would wrongly
     * pass. That was the Phase 2C defect, and it must not recur here.
     */
    @Test
    public void aVisitCannotRecordAStudentFromAnotherLibrary() throws Exception {
        String admin = superAdminToken();

        // Student 4 belongs to library 2.
        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(4L, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));

        // Student 5 is in library 3, under a different organization entirely.
        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(5L, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));

        // Nothing was written for either student in library 1 today.
        var res = mvc.perform(get("/api/libraries/1/attendance")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode row : data(res.getResponse().getContentAsString())) {
            assertThat(row.get("studentId").asLong()).isNotEqualTo(4L);
            assertThat(row.get("studentId").asLong()).isNotEqualTo(5L);
        }
    }

    /** A seat is tenant-scoped too, so one from another library cannot be recorded. */
    @Test
    public void aVisitCannotRecordASeatFromAnotherLibrary() throws Exception {
        ensureCheckedOut(3L, superAdminToken());

        // Seat 6 belongs to library 2; seat 8 to library 3.
        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(3L, 6L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NOT_FOUND"));

        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(3L, 8L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NOT_FOUND"));

        // The rejected check-ins left the student free to be checked in properly.
        var res = mvc.perform(get("/api/students/3/attendance")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode row : data(res.getResponse().getContentAsString())) {
            assertThat(row.get("open").asBoolean()).isFalse();
        }
    }

    /* --------------------------------------------------------- authorisation */

    @Test
    public void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/libraries/1/attendance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mvc.perform(get("/api/attendance/1"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(3L, null)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/attendance/2/check-out"))
                .andExpect(status().isUnauthorized());
    }

    /** manager1 belongs to library 1 only. Library 2 is another tenant to them. */
    @Test
    public void crossTenantReadsAreForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/2/attendance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Student 4 belongs to library 2.
        mvc.perform(get("/api/students/4/attendance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void crossTenantWritesAreForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(post("/api/libraries/2/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(4L, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Library 2 holds no attendance today, so open a visit there as a user
        // who may, then prove manager1 cannot close it.
        String owner = ownerToken();
        var opened = mvc.perform(post("/api/libraries/2/attendance/check-in")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody(4L, null)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = data(opened.getResponse().getContentAsString()).get("attendanceId").asLong();
        try {
            mvc.perform(get("/api/attendance/" + id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

            mvc.perform(post("/api/attendance/" + id + "/check-out")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

            // The refused check-out left the visit open.
            mvc.perform(get("/api/attendance/" + id)
                            .header("Authorization", "Bearer " + owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.open").value(true));
        } finally {
            closeQuietly(id, owner);
        }
    }

    /**
     * An unreachable tenant and a missing one must not be distinguishable by a
     * different error shape.
     */
    @Test
    public void unreachableAndMissingResourcesStayNonInformative() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/2/attendance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/libraries/999999/attendance")
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
        String bearer = "Bearer " + ownerToken();
        var responses = new java.util.ArrayList<String>();

        responses.add(mvc.perform(get("/api/libraries/999999/attendance").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/attendance/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/students/999999/attendance").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/attendance")
                        .param("status", "ABSENT").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/attendance")
                        .param("date", "31-12-2026").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/attendance/check-in").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/attendance/check-in").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/attendance/check-in").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(4L, null)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/attendance/check-in").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(2L, null)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/attendance/check-in").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(3L, 6L)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/attendance/1/check-out").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/attendance/999999/check-out").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());

        for (String body : responses) {
            assertThat(body).doesNotContain("INTERNAL_ERROR");
            assertThat(body).doesNotContain("Unable to process the request");
        }
    }
}
