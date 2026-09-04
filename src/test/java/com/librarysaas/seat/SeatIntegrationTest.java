package com.librarysaas.seat;

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
 * Phase 2B seat management, exercised end to end against the seeded
 * Testcontainers MySQL with real JWTs.
 *
 * Seed data these tests rely on (V1__initial_schema.sql):
 *   library 1 -> seats 1 (A001, OCCUPIED), 2 (A002, OCCUPIED), 3 (A003, AVAILABLE),
 *                4 (B001, AVAILABLE), 5 (B002, MAINTENANCE)
 *   library 2 -> seats 6, 7;  library 3 -> seat 8
 *   active assignments: student 1 -> seat 1, student 2 -> seat 2
 *   students 1-3 in library 1, student 4 in library 2
 *   manager1  -> library 1, has SEAT_VIEW/CREATE/UPDATE/ASSIGN
 *   reception1-> library 1, has SEAT_VIEW and SEAT_ASSIGN but NOT SEAT_CREATE/UPDATE
 */
public class SeatIntegrationTest extends com.librarysaas.IntegrationTestBase {

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

    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    private String receptionToken() throws Exception {
        return login("reception1@brightfuture.example", "Password@123");
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private Map<String, Object> seat(String number, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("seatNumber", number);
        if (status != null) body.put("status", status);
        return body;
    }

    /** Creates a seat and returns its id, so a test starts from known ground. */
    private long createSeat(String token, String number) throws Exception {
        var res = mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat(number, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).at("/data/seatId").asLong();
    }

    private void deleteSeatRow(String token, long seatId) throws Exception {
        // No hard delete exists; taking it out of service is the cleanup path.
        mvc.perform(delete("/api/libraries/1/seats/" + seatId)
                .header("Authorization", "Bearer " + token));
    }

    /* --------------------------------------------------------- seed and list */

    @Test
    public void listsSeededSeatsWithTheirAllocations() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode seats = mapper.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(seats.size()).isGreaterThanOrEqualTo(5);

        JsonNode a001 = null;
        JsonNode a003 = null;
        for (JsonNode node : seats) {
            if ("A001".equals(node.get("seatNumber").asText())) a001 = node;
            if ("A003".equals(node.get("seatNumber").asText())) a003 = node;
        }

        // A001 is seeded OCCUPIED by student 1, and the allocation is folded in.
        assertThat(a001).isNotNull();
        assertThat(a001.get("status").asText()).isEqualTo("OCCUPIED");
        assertThat(a001.at("/currentAllocation/studentId").asLong()).isEqualTo(1L);
        assertThat(a001.at("/currentAllocation/status").asText()).isEqualTo("ACTIVE");
        assertThat(a001.get("zoneName").asText()).isEqualTo("GROUND");

        // A003 is free, so it carries no allocation.
        assertThat(a003).isNotNull();
        assertThat(a003.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(a003.get("currentAllocation").isNull()).isTrue();
    }

    @Test
    public void filtersSeatsByStatus() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/seats")
                        .param("status", "maintenance")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode seats = mapper.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(seats.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode node : seats) {
            assertThat(node.get("status").asText()).isEqualTo("MAINTENANCE");
        }
    }

    @Test
    public void exposesSeatTypesAndZonesForTheLibrary() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/1/seat-types").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").exists());

        mvc.perform(get("/api/libraries/1/seat-zones").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").exists());
    }

    /* ---------------------------------------------------------------- create */

    @Test
    public void createsUpdatesAndDeactivatesASeat() throws Exception {
        String token = managerToken();

        var created = mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("T-100", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.seatNumber").value("T-100"))
                // Status defaults to AVAILABLE.
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andReturn();

        long seatId = mapper.readTree(created.getResponse().getContentAsString())
                .at("/data/seatId").asLong();

        // An update carries the seat number back unchanged. A seat keeps the
        // number it was created with - SeatCountIntegrationTest covers the
        // refusal - so only the status moves here.
        mvc.perform(put("/api/libraries/1/seats/" + seatId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("T-100", "MAINTENANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatNumber").value("T-100"))
                .andExpect(jsonPath("$.data.status").value("MAINTENANCE"));

        mvc.perform(delete("/api/libraries/1/seats/" + seatId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // Deactivation is idempotent-guarded, not silently repeatable.
        mvc.perform(delete("/api/libraries/1/seats/" + seatId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_ALREADY_INACTIVE"));
    }

    @Test
    public void duplicateSeatNumberInSameLibraryIsRejected() throws Exception {
        mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("A001", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NUMBER_ALREADY_EXISTS"));
    }

    @Test
    public void sameSeatNumberIsAllowedInADifferentLibrary() throws Exception {
        // uk_seat_library_number is per library: A001 exists in libraries 1, 2 and 3.
        String token = superAdminToken();

        var res = mvc.perform(get("/api/libraries/2/seats")
                        .param("search", "A001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode seats = mapper.readTree(res.getResponse().getContentAsString()).at("/data");
        assertThat(seats.size()).isEqualTo(1);
        assertThat(seats.get(0).get("libraryId").asLong()).isEqualTo(2L);
    }

    @Test
    public void occupiedCannotBeSetDirectlyOnASeat() throws Exception {
        mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("T-200", "OCCUPIED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SEAT_STATUS"));
    }

    @Test
    public void unknownSeatStatusIsABusinessError() throws Exception {
        mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("T-201", "NONSENSE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SEAT_STATUS"));
    }

    @Test
    public void missingSeatNumberReturnsValidationError() throws Exception {
        mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.seatNumber").exists());
    }

    @Test
    public void zoneFromAnotherLibraryIsRejected() throws Exception {
        // Zone 3 belongs to library 2, so it must not attach to a library 1 seat.
        Map<String, Object> body = seat("T-300", null);
        body.put("zoneId", 3);

        mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SEAT_ZONE_NOT_FOUND"));
    }

    /* ------------------------------------------------------------- not found */

    @Test
    public void unknownSeatReturns404() throws Exception {
        mvc.perform(get("/api/libraries/1/seats/999999")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NOT_FOUND"));
    }

    @Test
    public void unknownLibraryReturns404() throws Exception {
        mvc.perform(get("/api/libraries/999999/seats")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    /* ------------------------------------------------------------ allocation */

    @Test
    public void allocatesAndReleasesASeat() throws Exception {
        String token = managerToken();
        long seatId = createSeat(token, "T-400");

        // Student 3 is in library 1 and holds no seat in the seed data.
        var allocated = mvc.perform(post("/api/libraries/1/seats/" + seatId + "/allocation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentId").value(3))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.startDate").exists())
                .andReturn();

        assertThat(mapper.readTree(allocated.getResponse().getContentAsString())
                .at("/data/assignmentId").asLong()).isPositive();

        // The seat now reports itself occupied, with the allocation attached.
        mvc.perform(get("/api/libraries/1/seats/" + seatId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OCCUPIED"))
                .andExpect(jsonPath("$.data.currentAllocation.studentId").value(3));

        // And so does the student-side view.
        mvc.perform(get("/api/students/3/seat-allocation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatId").value((int) seatId));

        mvc.perform(delete("/api/libraries/1/seats/" + seatId + "/allocation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"))
                .andExpect(jsonPath("$.data.endDate").exists());

        // Released returns the seat to the pool and clears the student's seat.
        mvc.perform(get("/api/libraries/1/seats/" + seatId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.currentAllocation").doesNotExist());

        mvc.perform(get("/api/students/3/seat-allocation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        deleteSeatRow(token, seatId);
    }

    @Test
    public void seatAlreadyAllocatedIsRejected() throws Exception {
        // Seat 1 is seeded as occupied by student 1; student 3 must not take it.
        mvc.perform(post("/api/libraries/1/seats/1/allocation")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_ALREADY_ALLOCATED"));
    }

    @Test
    public void studentAlreadyHoldingASeatCannotTakeAnother() throws Exception {
        String token = managerToken();
        long seatId = createSeat(token, "T-500");

        // Student 1 already holds seat A001 in the seed data.
        mvc.perform(post("/api/libraries/1/seats/" + seatId + "/allocation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_ALREADY_HAS_SEAT"));

        deleteSeatRow(token, seatId);
    }

    @Test
    public void seatUnderMaintenanceCannotBeAllocated() throws Exception {
        // Seat 5 (B002) is seeded MAINTENANCE.
        mvc.perform(post("/api/libraries/1/seats/5/allocation")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    public void releasingAnUnallocatedSeatIsRejected() throws Exception {
        // Seat 3 (A003) is seeded AVAILABLE.
        mvc.perform(delete("/api/libraries/1/seats/3/allocation")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NOT_ALLOCATED"));
    }

    @Test
    public void allocatingToAStudentOfAnotherLibraryReturns404() throws Exception {
        String token = managerToken();
        long seatId = createSeat(token, "T-600");

        // Student 4 belongs to library 2. It must not resolve through library 1.
        mvc.perform(post("/api/libraries/1/seats/" + seatId + "/allocation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 4))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));

        deleteSeatRow(token, seatId);
    }

    @Test
    public void allocationRequiresAStudent() throws Exception {
        mvc.perform(post("/api/libraries/1/seats/3/allocation")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.studentId").exists());
    }

    @Test
    public void anAllocatedSeatCannotBeTakenOutOfService() throws Exception {
        mvc.perform(delete("/api/libraries/1/seats/1")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_HAS_ACTIVE_ALLOCATION"));
    }

    /* --------------------------------------------------------- authorisation */

    @Test
    public void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/libraries/1/seats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    public void crossTenantSeatListIsForbidden() throws Exception {
        // manager1 belongs to library 1 only.
        mvc.perform(get("/api/libraries/3/seats")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    public void crossTenantSeatCreateIsForbidden() throws Exception {
        mvc.perform(post("/api/libraries/3/seats")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("X-1", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    public void crossTenantSeatUpdateAndDeactivateAreForbidden() throws Exception {
        String token = managerToken();

        // Seat 8 belongs to library 3.
        mvc.perform(put("/api/libraries/3/seats/8")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("X-2", null))))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/libraries/3/seats/8")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    public void crossTenantAllocateAndReleaseAreForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(post("/api/libraries/2/seats/6/allocation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 4))))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/libraries/2/seats/6/allocation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    public void seatIdFromAnotherLibraryDoesNotResolveThroughOwnLibrary() throws Exception {
        // Seat 8 exists, but not in library 1. Scoping the lookup makes this a
        // 404 rather than an accidental cross-tenant read.
        mvc.perform(get("/api/libraries/1/seats/8")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NOT_FOUND"));
    }

    @Test
    public void crossTenantStudentAllocationLookupIsForbidden() throws Exception {
        mvc.perform(get("/api/students/4/seat-allocation")
                        .header("Authorization", "Bearer " + managerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /* ------------------------------------------------- permission separation */

    @Test
    public void receptionistMayAllocateButMayNotCreateOrUpdateSeats() throws Exception {
        String reception = receptionToken();

        // SEAT_VIEW is granted.
        mvc.perform(get("/api/libraries/1/seats").header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk());

        // SEAT_CREATE and SEAT_UPDATE are not.
        mvc.perform(post("/api/libraries/1/seats")
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(seat("T-700", null))))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/libraries/1/seats/3")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isForbidden());

        // SEAT_ASSIGN is, so allocation works and can be undone.
        mvc.perform(post("/api/libraries/1/seats/3/allocation")
                        .header("Authorization", "Bearer " + reception)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 3))))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/libraries/1/seats/3/allocation")
                        .header("Authorization", "Bearer " + reception))
                .andExpect(status().isOk());
    }
}
