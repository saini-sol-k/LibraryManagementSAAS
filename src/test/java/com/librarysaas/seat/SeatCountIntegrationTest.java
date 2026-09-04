package com.librarysaas.seat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seat count: generation at onboarding, later increases and reductions, and
 * who is allowed to ask for them.
 *
 * <p>Every test onboards its own customer, so a library here starts with exactly
 * the seats it was created with and nothing another test did can change what
 * these assertions see. That also makes each test a tenant-isolation check in
 * passing: a library asked for five seats must report five, never the whole
 * seat table.
 *
 * <p>Seeded callers: superadmin (platform), owner1 (ORGANIZATION_OWNER of
 * organization 1), manager1 (LIBRARY_MANAGER, library 1), reception1
 * (RECEPTIONIST, libraries 1 and 2). Of these only superadmin and the
 * organization owners hold LIBRARY_UPDATE, which is what gates the seat count.
 */
public class SeatCountIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** Kept clear of the ranges the other test classes use for their fixtures. */
    private static final AtomicInteger SEQ = new AtomicInteger(81000);

    // ---------------------------------------------------------------- helpers

    private String login(String identifier, String password) throws Exception {
        var res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString())
                .at("/data/accessToken").asText(null);
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    private String receptionToken() throws Exception {
        return login("reception1@brightfuture.example", "Password@123");
    }

    private String nextTag() {
        return String.valueOf(SEQ.incrementAndGet());
    }

    private Map<String, Object> onboardingRequest(String tag, Object seatCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationName", "Seats " + tag);
        body.put("organizationCode", "CAP-" + tag);
        body.put("libraryName", "Seats Library " + tag);
        body.put("libraryCode", "CAPLIB-" + tag);
        body.put("timezone", "Asia/Kolkata");
        body.put("adminUsername", "cap" + tag);
        body.put("adminEmail", "cap" + tag + "@customer.example");
        body.put("adminFirstName", "Cap");
        body.put("adminLastName", tag);
        if (seatCount != null) {
            body.put("seatCount", seatCount);
        }
        return body;
    }

    /** Onboards a customer and returns the response payload. */
    private JsonNode onboard(int seatCount) throws Exception {
        var res = mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                onboardingRequest(nextTag(), seatCount))))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    /** Status of an onboarding attempt, for the rejection cases. */
    private int onboardStatus(Object seatCount) throws Exception {
        return mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                onboardingRequest(nextTag(), seatCount))))
                .andReturn().getResponse().getStatus();
    }

    private String tokenFor(JsonNode customer) throws Exception {
        return login(customer.at("/initialCredentials/username").asText(),
                customer.at("/initialCredentials/temporaryPassword").asText());
    }

    private JsonNode seatsOf(long libraryId, String token) throws Exception {
        var res = mvc.perform(get("/api/libraries/" + libraryId + "/seats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    /** Seat numbers of a library, as integers, sorted ascending. */
    private List<Integer> seatNumbersOf(long libraryId, String token) throws Exception {
        List<Integer> numbers = new ArrayList<>();
        for (JsonNode seat : seatsOf(libraryId, token)) {
            numbers.add(Integer.parseInt(seat.get("seatNumber").asText()));
        }
        numbers.sort(Integer::compareTo);
        return numbers;
    }

    private List<Integer> range(int from, int to) {
        List<Integer> numbers = new ArrayList<>();
        for (int n = from; n <= to; n++) {
            numbers.add(n);
        }
        return numbers;
    }

    private long seatIdOf(long libraryId, String seatNumber, String token) throws Exception {
        for (JsonNode seat : seatsOf(libraryId, token)) {
            if (seatNumber.equals(seat.get("seatNumber").asText())) {
                return seat.get("seatId").asLong();
            }
        }
        throw new AssertionError("Library " + libraryId + " has no seat " + seatNumber);
    }

    /**
     * Creates a student in the caller's own library. Used only to make a seat
     * genuinely occupied, so a reduction has something real to refuse.
     */
    private long createStudent(String token) throws Exception {
        String tag = nextTag();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentCode", "CAPSTU-" + tag);
        body.put("firstName", "Cap");
        body.put("lastName", tag);
        body.put("joiningDate", "2026-01-01");

        var res = mvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private void allocate(long libraryId, long seatId, long studentId, String token) throws Exception {
        mvc.perform(post("/api/libraries/" + libraryId + "/seats/" + seatId + "/allocation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", studentId))))
                .andExpect(status().isCreated());
    }

    private void release(long libraryId, long seatId, String token) throws Exception {
        mvc.perform(delete("/api/libraries/" + libraryId + "/seats/" + seatId + "/allocation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** Message a rejected seat-count change comes back with. */
    private String messageForSeatCount(long libraryId, Object seatCount, String token) throws Exception {
        var res = mvc.perform(patch("/api/libraries/" + libraryId + "/seat-count")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("seatCount", seatCount))))
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        // Bean Validation reports per field; the deserializer's own rejection is
        // relayed into the same shape, so both read from data.seatCount.
        JsonNode field = body.at("/data/seatCount");
        return field.isMissingNode() || field.isNull() ? body.at("/message").asText() : field.asText();
    }

    /** Message a rejected onboarding comes back with. */
    private String onboardMessage(Object seatCount) throws Exception {
        var res = mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(onboardingRequest(nextTag(), seatCount))))
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        JsonNode field = body.at("/data/seatCount");
        return field.isMissingNode() || field.isNull() ? body.at("/message").asText() : field.asText();
    }

    /** total, available, occupied - as the dashboard reports them. */
    private List<Long> dashboardSeats(long libraryId, String token) throws Exception {
        var res = mvc.perform(get("/api/libraries/" + libraryId + "/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = mapper.readTree(res.getResponse().getContentAsString()).get("data");
        return List.of(data.get("totalSeats").asLong(),
                data.get("availableSeats").asLong(),
                data.get("occupiedSeats").asLong());
    }

    private int setSeatCount(long libraryId, Object seatCount, String token) throws Exception {
        return mvc.perform(patch("/api/libraries/" + libraryId + "/seat-count")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                seatCount == null
                                        ? Map.of()
                                        : Map.of("seatCount", seatCount))))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode setSeatCountOk(long libraryId, int seatCount, String token) throws Exception {
        var res = mvc.perform(patch("/api/libraries/" + libraryId + "/seat-count")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("seatCount", seatCount))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    // ------------------------------------------------------ onboarding: seats

    @Test
    public void onboardingCreatesExactlyTheRequestedSeatsNumberedFromOne() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        assertThat(customer.at("/library/seatCount").asInt()).isEqualTo(5);
        assertThat(customer.at("/library/seatsCreated").asInt()).isEqualTo(5);
        assertThat(customer.at("/library/seatRange").asText()).isEqualTo("1 - 5");

        // Read back through the API as the customer, so the assertion covers what
        // the tenant can actually see rather than what was written.
        assertThat(seatNumbersOf(libraryId, tokenFor(customer)))
                .containsExactlyElementsOf(range(1, 5));
    }

    @Test
    public void generatedSeatsBelongToTheNewLibraryAndAreAvailable() throws Exception {
        JsonNode customer = onboard(4);
        long libraryId = customer.at("/library/libraryId").asLong();

        JsonNode seats = seatsOf(libraryId, tokenFor(customer));
        assertThat(seats).hasSize(4);
        for (JsonNode seat : seats) {
            assertThat(seat.get("libraryId").asLong()).isEqualTo(libraryId);
            assertThat(seat.get("status").asText()).isEqualTo("AVAILABLE");
            assertThat(seat.get("currentAllocation").isNull()).isTrue();
        }
    }

    @Test
    public void aLargeSeatCountIsGeneratedInFull() throws Exception {
        JsonNode customer = onboard(120);
        long libraryId = customer.at("/library/libraryId").asLong();

        // 100 is where a lexicographic MAX(seat_number) would break: it sorts
        // below "99", so a numbering scheme read off the column would produce a
        // duplicate here rather than 101.
        assertThat(seatNumbersOf(libraryId, tokenFor(customer)))
                .containsExactlyElementsOf(range(1, 120));
    }

    // ------------------------------------------------- onboarding: rejections

    @Test
    public void onboardingRejectsAMissingSeatCount() throws Exception {
        assertThat(onboardStatus(null)).isEqualTo(400);
    }

    @Test
    public void onboardingRejectsZeroSeats() throws Exception {
        assertThat(onboardStatus(0)).isEqualTo(400);
    }

    @Test
    public void onboardingRejectsNegativeSeats() throws Exception {
        assertThat(onboardStatus(-5)).isEqualTo(400);
    }

    @Test
    public void onboardingRejectsDecimalSeats() throws Exception {
        // Sent as a JSON number with a fractional part, which is what a client
        // typing "5.5" would produce.
        assertThat(onboardStatus(5.5)).isEqualTo(400);
    }

    @Test
    public void onboardingRejectsExcessiveSeats() throws Exception {
        assertThat(onboardStatus(10001)).isEqualTo(400);
    }

    /**
     * A rejected request must leave nothing behind. The organization, the library
     * and the administrator are all written before the seats, so a validation
     * failure that did not roll back would strand a half-built tenant and the
     * username would then be taken.
     */
    @Test
    public void aRejectedSeatCountCreatesNoCustomerAtAll() throws Exception {
        String tag = nextTag();
        Map<String, Object> bad = onboardingRequest(tag, 0);

        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        // The same username is still free, so nothing was persisted.
        Map<String, Object> good = onboardingRequest(tag, 3);
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(good)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.library.seatsCreated").value(3));
    }

    // ---------------------------------------------------- seat-count increases

    @Test
    public void increasingTheSeatCountCreatesOnlyTheNewSeats() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        JsonNode result = setSeatCountOk(libraryId, 8, superAdminToken());

        assertThat(result.get("previousSeatCount").asInt()).isEqualTo(5);
        assertThat(result.get("seatCount").asInt()).isEqualTo(8);
        assertThat(result.get("seatsCreated").asInt()).isEqualTo(3);
        assertThat(result.get("seatsWithdrawn").asInt()).isZero();
        assertThat(result.get("seatRange").asText()).isEqualTo("6 - 8");

        assertThat(seatNumbersOf(libraryId, tokenFor(customer)))
                .containsExactlyElementsOf(range(1, 8));
    }

    /**
     * The data-integrity case: the statuses the library had set on seats 1-5 must
     * survive an increase untouched, and the seats must not be recreated.
     */
    @Test
    public void existingSeatsKeepTheirIdentityAndStatusAcrossAnIncrease() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        // Put seat 3 into MAINTENANCE, so at least one seat differs from default.
        JsonNode seat3 = null;
        for (JsonNode seat : seatsOf(libraryId, token)) {
            if ("3".equals(seat.get("seatNumber").asText())) {
                seat3 = seat;
            }
        }
        assertThat(seat3).isNotNull();
        long seat3Id = seat3.get("seatId").asLong();

        mvc.perform(put("/api/libraries/" + libraryId + "/seats/" + seat3Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("seatNumber", "3", "status", "MAINTENANCE"))))
                .andExpect(status().isOk());

        setSeatCountOk(libraryId, 8, token);

        Map<String, String> statusByNumber = new LinkedHashMap<>();
        Map<String, Long> idByNumber = new LinkedHashMap<>();
        for (JsonNode seat : seatsOf(libraryId, token)) {
            statusByNumber.put(seat.get("seatNumber").asText(), seat.get("status").asText());
            idByNumber.put(seat.get("seatNumber").asText(), seat.get("seatId").asLong());
        }

        // Same row, not a replacement, and the status the library chose is intact.
        assertThat(idByNumber.get("3")).isEqualTo(seat3Id);
        assertThat(statusByNumber.get("3")).isEqualTo("MAINTENANCE");
        assertThat(statusByNumber).containsEntry("1", "AVAILABLE")
                .containsEntry("5", "AVAILABLE")
                .containsEntry("6", "AVAILABLE")
                .containsEntry("8", "AVAILABLE");
    }

    @Test
    public void settingTheSameSeatCountCreatesNothing() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        JsonNode result = setSeatCountOk(libraryId, 5, superAdminToken());

        assertThat(result.get("seatsCreated").asInt()).isZero();
        assertThat(result.get("seatsWithdrawn").asInt()).isZero();
        assertThat(result.get("seatRange").isNull()).isTrue();
        assertThat(seatNumbersOf(libraryId, tokenFor(customer)))
                .containsExactlyElementsOf(range(1, 5));
    }

    @Test
    public void seatCountChangesAreBoundedTheSameWayOnboardingIs() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = superAdminToken();

        assertThat(setSeatCount(libraryId, 0, token)).isEqualTo(400);
        assertThat(setSeatCount(libraryId, -1, token)).isEqualTo(400);
        assertThat(setSeatCount(libraryId, 5.5, token)).isEqualTo(400);
        assertThat(setSeatCount(libraryId, 10001, token)).isEqualTo(400);
        assertThat(setSeatCount(libraryId, null, token)).isEqualTo(400);

        // None of the rejections changed anything.
        assertThat(seatNumbersOf(libraryId, tokenFor(customer)))
                .containsExactlyElementsOf(range(1, 5));
    }

    // --------------------------------------------------- seat-count reductions

    /**
     * A seat nothing has ever referenced is deleted outright. Retiring it would
     * leave a dead INACTIVE row behind for a library that was simply
     * over-provisioned and never used the seats.
     */
    @Test
    public void reducingTheSeatCountRemovesUnusedSurplusSeats() throws Exception {
        JsonNode customer = onboard(8);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        JsonNode result = setSeatCountOk(libraryId, 5, token);

        assertThat(result.get("seatCount").asInt()).isEqualTo(5);
        assertThat(result.get("seatsRemoved").asInt()).isEqualTo(3);
        assertThat(result.get("seatsRetired").asInt()).isZero();
        assertThat(result.get("seatsWithdrawn").asInt()).isEqualTo(3);
        assertThat(result.get("seatsCreated").asInt()).isZero();

        // The rows are gone, so the library is exactly its new size.
        assertThat(seatNumbersOf(libraryId, token)).containsExactlyElementsOf(range(1, 5));
    }

    /**
     * A seat with history is kept instead. attendance.seat_id and
     * seat_assignment.seat_id are real foreign keys, so deleting the row would
     * either fail or strand the record that points at it.
     */
    @Test
    public void reducingTheSeatCountRetiresSurplusSeatsThatCarryHistory() throws Exception {
        JsonNode customer = onboard(8);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        // Seat 7 gets an allocation which is then released: history, but not in use.
        long studentId = createStudent(token);
        long seat7Id = seatIdOf(libraryId, "7", token);
        allocate(libraryId, seat7Id, studentId, token);
        release(libraryId, seat7Id, token);

        JsonNode result = setSeatCountOk(libraryId, 5, token);

        assertThat(result.get("seatsRetired").asInt()).isEqualTo(1);
        assertThat(result.get("seatsRemoved").asInt()).isEqualTo(2);
        assertThat(result.get("seatsWithdrawn").asInt()).isEqualTo(3);

        Map<String, String> statusByNumber = new LinkedHashMap<>();
        for (JsonNode seat : seatsOf(libraryId, token)) {
            statusByNumber.put(seat.get("seatNumber").asText(), seat.get("status").asText());
        }
        // 6 and 8 were never referenced and are gone; 7 survives as INACTIVE.
        assertThat(statusByNumber).hasSize(6).containsEntry("7", "INACTIVE");
        assertThat(statusByNumber).doesNotContainKeys("6", "8");
    }

    /**
     * Going back up must reuse a retired seat rather than create a second seat 7,
     * which uk_seat_library_number would refuse, and must recreate the ones that
     * were genuinely deleted.
     */
    @Test
    public void increasingAfterAReductionReactivatesTheRetainedSeats() throws Exception {
        JsonNode customer = onboard(8);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        long studentId = createStudent(token);
        long seat7Id = seatIdOf(libraryId, "7", token);
        allocate(libraryId, seat7Id, studentId, token);
        release(libraryId, seat7Id, token);

        setSeatCountOk(libraryId, 5, token);
        JsonNode result = setSeatCountOk(libraryId, 8, token);

        // Seat 7 was kept and comes back; 6 and 8 had been deleted and are new.
        assertThat(result.get("seatsReactivated").asInt()).isEqualTo(1);
        assertThat(result.get("seatsCreated").asInt()).isEqualTo(2);

        Map<String, String> statusByNumber = new LinkedHashMap<>();
        Map<String, Long> idsAfter = new LinkedHashMap<>();
        for (JsonNode seat : seatsOf(libraryId, token)) {
            statusByNumber.put(seat.get("seatNumber").asText(), seat.get("status").asText());
            idsAfter.put(seat.get("seatNumber").asText(), seat.get("seatId").asLong());
        }

        assertThat(seatNumbersOf(libraryId, token)).containsExactlyElementsOf(range(1, 8));
        // Historical identity preserved: seat 7 is the same row it always was.
        assertThat(idsAfter.get("7")).isEqualTo(seat7Id);
        assertThat(statusByNumber).containsEntry("7", "AVAILABLE")
                .containsEntry("6", "AVAILABLE")
                .containsEntry("8", "AVAILABLE");
    }

    /**
     * The safety rule: the seat count is never lowered by discarding
     * a seat somebody is sitting in. The refusal is all-or-nothing, so the seats
     * that could have been retired are left alone too.
     */
    @Test
    public void reducingTheSeatCountIsRefusedWhenASurplusSeatIsInUse() throws Exception {
        JsonNode customer = onboard(8);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        long studentId = createStudent(token);
        long seat7Id = seatIdOf(libraryId, "7", token);
        allocate(libraryId, seat7Id, studentId, token);

        mvc.perform(patch("/api/libraries/" + libraryId + "/seat-count")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("seatCount", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_COUNT_REDUCTION_BLOCKED"))
                // The message names the seat in the way, as the requirement asks.
                .andExpect(jsonPath("$.message")
                        .value("Seat count cannot be reduced to 5 because seat 7 is currently in use."));

        // Nothing moved: seat 6 and seat 8 could have gone, but the whole reduction
        // was refused, and the seat count is still what it was.
        Map<String, String> statusByNumber = new LinkedHashMap<>();
        for (JsonNode seat : seatsOf(libraryId, token)) {
            statusByNumber.put(seat.get("seatNumber").asText(), seat.get("status").asText());
        }
        assertThat(statusByNumber).hasSize(8)
                .containsEntry("6", "AVAILABLE")
                .containsEntry("7", "OCCUPIED")
                .containsEntry("8", "AVAILABLE");

        // Releasing the seat clears the obstacle and the same reduction succeeds.
        // Seat 7 now carries history, so it is retired rather than deleted.
        release(libraryId, seat7Id, token);

        JsonNode result = setSeatCountOk(libraryId, 5, token);
        assertThat(result.get("seatsWithdrawn").asInt()).isEqualTo(3);
        assertThat(result.get("seatsRetired").asInt()).isEqualTo(1);
        assertThat(result.get("seatsRemoved").asInt()).isEqualTo(2);
    }

    // -------------------------------------------------------- seat numbers

    /**
     * The security case. A read-only input is not a boundary, so the API itself
     * must refuse a renumber, and the seat must still carry its original number
     * afterwards.
     */
    @Test
    public void aSeatNumberCannotBeChangedThroughTheApi() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        JsonNode seat5 = null;
        for (JsonNode seat : seatsOf(libraryId, token)) {
            if ("5".equals(seat.get("seatNumber").asText())) {
                seat5 = seat;
            }
        }
        assertThat(seat5).isNotNull();
        long seat5Id = seat5.get("seatId").asLong();

        mvc.perform(put("/api/libraries/" + libraryId + "/seats/" + seat5Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("seatNumber", "99", "status", "AVAILABLE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SEAT_NUMBER_NOT_EDITABLE"));

        // Read it back: the seat still has the number it was generated with, and
        // no seat 99 was created as a side effect.
        assertThat(seatNumbersOf(libraryId, token)).containsExactlyElementsOf(range(1, 5));
    }

    @Test
    public void aSeatCanStillBeUpdatedWhenItsNumberIsUnchanged() throws Exception {
        JsonNode customer = onboard(3);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        JsonNode seat = seatsOf(libraryId, token).get(0);
        long seatId = seat.get("seatId").asLong();
        String seatNumber = seat.get("seatNumber").asText();

        mvc.perform(put("/api/libraries/" + libraryId + "/seats/" + seatId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("seatNumber", seatNumber, "status", "MAINTENANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.data.seatNumber").value(seatNumber));
    }

    // ------------------------------------------------------- authorization

    @Test
    public void anOrganizationOwnerCanChangeTheirOwnLibrarysSeatCount() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        assertThat(setSeatCount(libraryId, 7, tokenFor(customer))).isEqualTo(200);
    }

    @Test
    public void superAdminCanChangeAnyLibrarysSeatCount() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        assertThat(setSeatCount(libraryId, 9, superAdminToken())).isEqualTo(200);
    }

    /**
     * The cross-tenant case from the brief: customer A's owner must not be able to
     * resize customer B's library.
     */
    @Test
    public void anOwnerCannotChangeAnotherCustomersSeatCount() throws Exception {
        JsonNode a = onboard(5);
        JsonNode b = onboard(5);
        long libraryB = b.at("/library/libraryId").asLong();

        assertThat(setSeatCount(libraryB, 20, tokenFor(a))).isEqualTo(403);

        // B is untouched.
        assertThat(seatNumbersOf(libraryB, tokenFor(b))).containsExactlyElementsOf(range(1, 5));
    }

    @Test
    public void aReceptionistCannotChangeTheSeatCount() throws Exception {
        // Library 1 is one reception1 belongs to, so this fails on the permission
        // rather than on membership.
        assertThat(setSeatCount(1L, 200, receptionToken())).isEqualTo(403);
    }

    /**
     * A library manager holds SEAT_CREATE but not LIBRARY_UPDATE. Gating the count
     * on the seat permission would have let them resize the library, which is why
     * it is gated on LIBRARY_UPDATE instead.
     */
    @Test
    public void aLibraryManagerCannotChangeTheSeatCount() throws Exception {
        assertThat(setSeatCount(1L, 200, managerToken())).isEqualTo(403);
    }

    @Test
    public void anUnknownLibraryIsNotFoundRatherThanForbidden() throws Exception {
        assertThat(setSeatCount(999_999L, 10, superAdminToken())).isEqualTo(404);
    }

    // ------------------------------------------------------ tenant isolation

    @Test
    public void twoCustomersNumberTheirSeatsIndependently() throws Exception {
        JsonNode a = onboard(5);
        JsonNode b = onboard(3);
        long libA = a.at("/library/libraryId").asLong();
        long libB = b.at("/library/libraryId").asLong();

        assertThat(seatNumbersOf(libA, tokenFor(a))).containsExactlyElementsOf(range(1, 5));
        assertThat(seatNumbersOf(libB, tokenFor(b))).containsExactlyElementsOf(range(1, 3));

        // Raising A leaves B exactly as it was, numbers and all.
        setSeatCountOk(libA, 8, superAdminToken());

        assertThat(seatNumbersOf(libA, tokenFor(a))).containsExactlyElementsOf(range(1, 8));
        assertThat(seatNumbersOf(libB, tokenFor(b))).containsExactlyElementsOf(range(1, 3));
    }
    // ------------------------------------------------- validation messages

    /**
     * The wording is part of the requirement, not an implementation detail, so
     * each rejection is asserted on the message the user actually sees.
     */
    @Test
    public void rejectionsCarryTheirUserFacingMessage() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = superAdminToken();

        assertThat(messageForSeatCount(libraryId, 0, token))
                .isEqualTo("Number of seats must be greater than 0.");
        assertThat(messageForSeatCount(libraryId, -5, token))
                .isEqualTo("Number of seats must be greater than 0.");
        assertThat(messageForSeatCount(libraryId, 1.5, token))
                .isEqualTo("Number of seats must be a whole number.");
        assertThat(messageForSeatCount(libraryId, 100000, token))
                .isEqualTo("Number of seats cannot exceed 10000.");
    }

    @Test
    public void onboardingRejectionsCarryTheirUserFacingMessage() throws Exception {
        assertThat(onboardMessage(0)).isEqualTo("Number of seats must be greater than 0.");
        assertThat(onboardMessage(1.5)).isEqualTo("Number of seats must be a whole number.");
        assertThat(onboardMessage(100000)).isEqualTo("Number of seats cannot exceed 10000.");
    }

    /** The documented maximum is accepted; one more than it is not. */
    @Test
    public void theDocumentedMaximumIsTenThousand() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        assertThat(setSeatCount(libraryId, 10001, superAdminToken())).isEqualTo(400);
    }

    // ----------------------------------------------------- tenant isolation

    /**
     * Seat numbers are unique per library, not globally: uk_seat_library_number
     * is (library_id, seat_number). Two customers both holding a seat "1" is the
     * expected state, and neither can see the other's.
     */
    @Test
    public void twoLibrariesHoldTheSameSeatNumbersIndependently() throws Exception {
        JsonNode a = onboard(5);
        JsonNode b = onboard(5);
        long libA = a.at("/library/libraryId").asLong();
        long libB = b.at("/library/libraryId").asLong();

        List<Long> idsA = new ArrayList<>();
        for (JsonNode seat : seatsOf(libA, tokenFor(a))) {
            idsA.add(seat.get("seatId").asLong());
            assertThat(seat.get("libraryId").asLong()).isEqualTo(libA);
        }
        List<Long> idsB = new ArrayList<>();
        for (JsonNode seat : seatsOf(libB, tokenFor(b))) {
            idsB.add(seat.get("seatId").asLong());
            assertThat(seat.get("libraryId").asLong()).isEqualTo(libB);
        }

        // Same numbers, different rows, and no overlap between the two tenants.
        assertThat(seatNumbersOf(libA, tokenFor(a))).isEqualTo(seatNumbersOf(libB, tokenFor(b)));
        assertThat(idsA).doesNotContainAnyElementsOf(idsB);
    }

    @Test
    public void aCustomerCannotReadAnotherCustomersSeats() throws Exception {
        JsonNode a = onboard(5);
        JsonNode b = onboard(3);
        long libB = b.at("/library/libraryId").asLong();

        mvc.perform(get("/api/libraries/" + libB + "/seats")
                        .header("Authorization", "Bearer " + tokenFor(a)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void anUnauthenticatedCallerCannotChangeTheSeatCount() throws Exception {
        JsonNode customer = onboard(5);
        long libraryId = customer.at("/library/libraryId").asLong();

        mvc.perform(patch("/api/libraries/" + libraryId + "/seat-count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("seatCount", 9))))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------- reporting

    /**
     * The dashboard derives its seat figures from the seat rows themselves, so
     * generated seats must appear in them without reporting being touched.
     */
    @Test
    public void theDashboardCountsGeneratedSeats() throws Exception {
        JsonNode customer = onboard(6);
        long libraryId = customer.at("/library/libraryId").asLong();
        String token = tokenFor(customer);

        assertThat(dashboardSeats(libraryId, token)).containsExactly(6L, 6L, 0L);

        // An increase is reflected without any reporting change.
        setSeatCountOk(libraryId, 9, token);
        assertThat(dashboardSeats(libraryId, token)).containsExactly(9L, 9L, 0L);

        // Allocating one moves it from available to occupied, totals unchanged.
        long studentId = createStudent(token);
        allocate(libraryId, seatIdOf(libraryId, "2", token), studentId, token);
        assertThat(dashboardSeats(libraryId, token)).containsExactly(9L, 8L, 1L);
    }
    /**
     * Generated seats must list in the order a person reads them. seat_number is
     * a VARCHAR, so a plain sort gives 1, 10, 100, 11 - correct alphabetically
     * and useless on screen once a library has more than nine seats.
     */
    @Test
    public void seatsAreListedInNumericOrder() throws Exception {
        JsonNode customer = onboard(12);
        long libraryId = customer.at("/library/libraryId").asLong();

        List<String> asListed = new ArrayList<>();
        for (JsonNode seat : seatsOf(libraryId, tokenFor(customer))) {
            asListed.add(seat.get("seatNumber").asText());
        }

        assertThat(asListed).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    }
    /**
     * Pins the wire contract. The field is annotated, so Jackson would bind it
     * even if the accessors were named something else - and then the published
     * schema and the accepted payload would quietly disagree, which is exactly
     * what happened once during development.
     */
    @Test
    public void theOnboardingFieldIsCalledSeatCount() throws Exception {
        String tag = nextTag();
        Map<String, Object> body = onboardingRequest(tag, null);
        body.put("numberOfSeats", 5);

        // The old name is not a seat count any more: nothing supplies one, so the
        // request is rejected as missing rather than silently creating 5 seats.
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.seatCount").value("Number of seats is required."));
    }
}
