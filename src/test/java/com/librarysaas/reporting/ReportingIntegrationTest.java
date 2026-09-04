package com.librarysaas.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2H reporting, exercised end to end against the seeded Testcontainers
 * MySQL with real JWTs.
 *
 * Two things shape how these tests assert.
 *
 * <p><b>Library 3 is the leakage detector.</b> Every write other test classes
 * make to library 3 is a cross-tenant case expecting 403, so library 3 stays
 * exactly as seeded: one student, one seat, one active membership, no attendance
 * and no payments. Those exact numbers are asserted below. If a reporting query
 * ever lost its {@code library_id} predicate, library 3 would report the whole
 * database instead, five students and eight seats, and these tests would fail
 * immediately. The same check runs as super admin, because unrestricted tenant
 * access must not widen the filter.
 *
 * <p><b>Library 1 is asserted by invariant and by delta.</b> Other classes
 * create students, seats, memberships, attendance and payments in library 1, so
 * absolute totals there would depend on execution order. Instead the tests check
 * internal consistency, seeded minimums, and exact before-and-after differences
 * around an action, which hold whatever else has run.
 *
 * <p>Seeded users: owner1 and manager1 hold REPORT_VIEW; reception1 does not.
 */
public class ReportingIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    /**
     * Seats each seeded library holds: the ones V1 gave it by name, plus the 100
     * that V2__add_library_seat_count created when it set every existing
     * library to a capacity of 100.
     *
     * Library 1 is deliberately absent: other test classes add and retire seats
     * there, so it is only ever compared, never pinned to a number.
     */
    private static final long V2_BACKFILLED_SEATS = 100L;
    private static final long LIBRARY_2_SEATS = 2L + V2_BACKFILLED_SEATS;
    private static final long LIBRARY_3_SEATS = 1L + V2_BACKFILLED_SEATS;

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

    /** manager1 belongs to library 1 only and holds REPORT_VIEW. */
    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    /** reception1 holds no REPORT_VIEW, which is what makes the 403 test genuine. */
    private String receptionToken() throws Exception {
        return login("reception1@brightfuture.example", "Password@123");
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).at("/data");
    }

    private JsonNode dashboard(long libraryId, String token) throws Exception {
        var res = mvc.perform(get("/api/libraries/" + libraryId + "/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return data(res.getResponse().getContentAsString());
    }

    /** Exact decimal comparison; never a double. */
    private void assertMoney(JsonNode node, String expected) {
        assertThat(node.isNull()).isFalse();
        assertThat(new BigDecimal(node.asText()))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal(expected));
    }

    private BigDecimal money(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    private void setLibraryTimezone(long libraryId, String timezone) throws Exception {
        mvc.perform(put("/api/libraries/" + libraryId)
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("timezone", timezone))))
                .andExpect(status().isOk());
    }

    /* ============================================ leakage: the critical test */

    /**
     * Library 3 is seeded with one student, one lettered seat and one active
     * membership, and nothing mutates it. V2__add_library_seat_count gave every
     * library that existed at the time a capacity of 100 and the seats to match,
     * so library 3 holds 101 seats in total. The whole database holds at least
     * five students and three libraries worth of seats.
     *
     * A reporting query that lost its library_id predicate would report those
     * global figures here. These exact assertions are what make that impossible
     * to ship unnoticed.
     */
    @Test
    public void aLibraryDashboardReportsOnlyThatLibrary() throws Exception {
        JsonNode summary = dashboard(3L, superAdminToken());

        assertThat(summary.get("libraryId").asLong()).isEqualTo(3L);
        assertThat(summary.get("totalStudents").asLong())
                .as("library 3 has one student; the database has five")
                .isEqualTo(1L);
        assertThat(summary.get("totalSeats").asLong())
                .as("library 3 has its own 101 seats, not every seat in the database")
                .isEqualTo(LIBRARY_3_SEATS);
        assertThat(summary.get("availableSeats").asLong()).isEqualTo(LIBRARY_3_SEATS);
        assertThat(summary.get("occupiedSeats").asLong()).isZero();
        assertThat(summary.get("activeMemberships").asLong())
                .as("library 3 has one active membership; the database has at least five")
                .isEqualTo(1L);
        assertThat(summary.get("attendanceToday").asLong()).isZero();
        assertThat(summary.get("studentsCurrentlyInside").asLong()).isZero();
        assertMoney(summary.get("collectionToday"), "0.00");
    }

    /**
     * Super admin reaches every library, and that must change nothing about which
     * rows are aggregated. The same numbers must come back for both callers.
     */
    @Test
    public void superAdminAccessDoesNotWidenTheTenantFilter() throws Exception {
        // Library 3 is reachable only by the super admin, and even then it must
        // report its own single-row totals rather than the whole database.
        JsonNode restricted = dashboard(3L, superAdminToken());
        assertThat(restricted.get("totalStudents").asLong()).isEqualTo(1L);
        assertThat(restricted.get("totalSeats").asLong()).isEqualTo(LIBRARY_3_SEATS);
        assertThat(restricted.get("activeMemberships").asLong()).isEqualTo(1L);

        // On a library both callers may read, unrestricted access must make no
        // difference at all: privilege decides who may ask, never what is counted.
        JsonNode asAdmin = dashboard(1L, superAdminToken());
        JsonNode asOwner = dashboard(1L, ownerToken());

        assertThat(asAdmin.get("totalStudents").asLong())
                .isEqualTo(asOwner.get("totalStudents").asLong());
        assertThat(asAdmin.get("totalSeats").asLong()).isEqualTo(asOwner.get("totalSeats").asLong());
        assertThat(asAdmin.get("activeMemberships").asLong())
                .isEqualTo(asOwner.get("activeMemberships").asLong());
        assertThat(money(asAdmin.get("collectionToday")))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(money(asOwner.get("collectionToday")));

        // And a super admin still sees library 1 only, never the global picture.
        // Expressed against the sum of the three seeded libraries rather than a
        // fixed number, so the assertion keeps its meaning as seats are added.
        long everySeededLibrary = asAdmin.get("totalSeats").asLong()
                + dashboard(2L, superAdminToken()).get("totalSeats").asLong()
                + dashboard(3L, superAdminToken()).get("totalSeats").asLong();
        assertThat(asAdmin.get("totalSeats").asLong())
                .as("library 1 reports its own seats, not every library's")
                .isLessThan(everySeededLibrary);
    }

    /**
     * Each library reports a different picture. If the filter were dropped, all
     * three would return the same global numbers.
     */
    @Test
    public void differentLibrariesReportDifferentTotals() throws Exception {
        String admin = superAdminToken();
        long one = dashboard(1L, admin).get("totalSeats").asLong();
        long two = dashboard(2L, admin).get("totalSeats").asLong();
        long three = dashboard(3L, admin).get("totalSeats").asLong();

        assertThat(three).isEqualTo(LIBRARY_3_SEATS);
        assertThat(two).isEqualTo(LIBRARY_2_SEATS);
        assertThat(one).isGreaterThan(three);
        assertThat(one == two && two == three)
                .as("identical totals across libraries would mean the tenant filter is gone")
                .isFalse();
    }

    /** Library 1 and library 3 sit in different organizations. */
    @Test
    public void anotherOrganizationsDataNeverAppears() throws Exception {
        String admin = superAdminToken();
        JsonNode libraryOne = dashboard(1L, admin);
        JsonNode libraryThree = dashboard(3L, admin);

        // Student 5 and its membership belong to organization 2 through library 3.
        assertThat(libraryThree.get("totalStudents").asLong()).isEqualTo(1L);
        assertThat(libraryOne.get("totalStudents").asLong()).isGreaterThanOrEqualTo(3L);
        assertThat(libraryOne.get("totalStudents").asLong())
                .isNotEqualTo(libraryOne.get("totalStudents").asLong()
                        + libraryThree.get("totalStudents").asLong());
    }

    /* ==================================================== dashboard contents */

    @Test
    public void theDashboardIsInternallyConsistent() throws Exception {
        JsonNode summary = dashboard(1L, ownerToken());

        long seatTotal = 0;
        for (JsonNode count : summary.get("seatsByStatus")) {
            seatTotal += count.asLong();
        }
        assertThat(seatTotal)
                .as("the status breakdown must add up to the seat total")
                .isEqualTo(summary.get("totalSeats").asLong());

        long studentTotal = 0;
        for (JsonNode count : summary.get("studentsByStatus")) {
            studentTotal += count.asLong();
        }
        assertThat(studentTotal).isEqualTo(summary.get("totalStudents").asLong());

        // Occupied and available are drawn from the seat module's own statuses,
        // and a seat under maintenance is neither.
        assertThat(summary.get("occupiedSeats").asLong() + summary.get("availableSeats").asLong())
                .isLessThanOrEqualTo(summary.get("totalSeats").asLong());

        // Seeded minimums; other test classes may add to library 1 but never remove.
        assertThat(summary.get("totalStudents").asLong()).isGreaterThanOrEqualTo(3L);
        assertThat(summary.get("totalSeats").asLong()).isGreaterThanOrEqualTo(5L);
        assertThat(summary.get("activeMemberships").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.get("studentsByStatus").has("ACTIVE")).isTrue();
    }

    /**
     * The dashboard's attendance figure must agree with the attendance module's
     * own list for the same day, which is the only cross-check that does not
     * simply restate the reporting query.
     */
    @Test
    public void attendanceTodayAgreesWithTheAttendanceModule() throws Exception {
        String token = ownerToken();
        JsonNode summary = dashboard(1L, token);
        String reportingDate = summary.get("reportingDate").asText();

        var res = mvc.perform(get("/api/libraries/1/attendance")
                        .param("date", reportingDate)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = data(res.getResponse().getContentAsString());
        long open = 0;
        for (JsonNode row : rows) {
            if (row.get("open").asBoolean()) {
                open++;
            }
        }

        assertThat(summary.get("attendanceToday").asLong()).isEqualTo(rows.size());
        assertThat(summary.get("studentsCurrentlyInside").asLong()).isEqualTo(open);
    }

    /** Checking a student in moves both attendance figures by exactly one. */
    @Test
    public void checkingInAndOutMovesTheDashboardFigures() throws Exception {
        String token = ownerToken();

        JsonNode before = dashboard(1L, token);
        long attendanceBefore = before.get("attendanceToday").asLong();
        long insideBefore = before.get("studentsCurrentlyInside").asLong();

        // Student 3 belongs to library 1. Close any visit it already has open so
        // the test does not depend on what other classes left behind.
        var history = mvc.perform(get("/api/students/3/attendance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode row : data(history.getResponse().getContentAsString())) {
            if (row.get("open").asBoolean()) {
                mvc.perform(post("/api/attendance/" + row.get("attendanceId").asLong() + "/check-out")
                        .header("Authorization", "Bearer " + token));
            }
        }

        JsonNode settled = dashboard(1L, token);
        long attendanceSettled = settled.get("attendanceToday").asLong();
        long insideSettled = settled.get("studentsCurrentlyInside").asLong();

        var opened = mvc.perform(post("/api/libraries/1/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 3))))
                .andExpect(status().isCreated())
                .andReturn();
        long visitId = data(opened.getResponse().getContentAsString()).get("attendanceId").asLong();

        try {
            JsonNode afterCheckIn = dashboard(1L, token);
            assertThat(afterCheckIn.get("attendanceToday").asLong()).isEqualTo(attendanceSettled + 1);
            assertThat(afterCheckIn.get("studentsCurrentlyInside").asLong()).isEqualTo(insideSettled + 1);
        } finally {
            mvc.perform(post("/api/attendance/" + visitId + "/check-out")
                    .header("Authorization", "Bearer " + token));
        }

        JsonNode afterCheckOut = dashboard(1L, token);
        // The visit still counts for the day, but nobody is left inside from it.
        assertThat(afterCheckOut.get("attendanceToday").asLong()).isEqualTo(attendanceSettled + 1);
        assertThat(afterCheckOut.get("studentsCurrentlyInside").asLong()).isEqualTo(insideSettled);
        assertThat(attendanceBefore).isGreaterThanOrEqualTo(0L);
    }

    /**
     * Recording a payment moves today's collection by exactly that amount, in
     * exact decimal. Asserting the difference rather than an absolute keeps the
     * test independent of what the finance tests banked earlier.
     */
    @Test
    public void todaysCollectionMovesByExactlyThePaymentAmount() throws Exception {
        String token = ownerToken();

        BigDecimal before = money(dashboard(1L, token).get("collectionToday"));

        String invoiceNumber = "INV-RPT" + SEQ.incrementAndGet();
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("studentId", 3);
        invoice.put("invoiceNumber", invoiceNumber);
        invoice.put("amount", "750.00");
        invoice.put("dueDate", "2026-12-31");

        var created = mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invoice)))
                .andExpect(status().isCreated())
                .andReturn();
        long feeId = data(created.getResponse().getContentAsString()).get("studentFeeId").asLong();

        // Raising an invoice is not a receipt, so nothing has been collected yet.
        assertThat(money(dashboard(1L, token).get("collectionToday")))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(before);

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("receiptNumber", "REC-RPT" + SEQ.incrementAndGet());
        payment.put("amount", "250.50");
        payment.put("paymentMethod", "CASH");

        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payment)))
                .andExpect(status().isCreated());

        assertThat(money(dashboard(1L, token).get("collectionToday")))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(before.add(new BigDecimal("250.50")));

        // The money landed in library 1 only.
        assertMoney(dashboard(3L, superAdminToken()).get("collectionToday"), "0.00");
    }

    /* ============================================================ timezone */

    /** The reporting date is the library's own day, not the server's. */
    @Test
    public void theReportingDateComesFromTheLibraryTimezone() throws Exception {
        JsonNode summary = dashboard(1L, ownerToken());

        String timezone = summary.get("timezone").asText();
        assertThat(timezone).isEqualTo("Asia/Kolkata");
        assertThat(summary.get("reportingDate").asText())
                .isEqualTo(LocalDate.now(ZoneId.of(timezone)).toString());
    }

    /**
     * Two zones 26 hours apart are never on the same calendar date, so this
     * asserts real behaviour rather than that ZoneId parses.
     */
    @Test
    public void changingTheTimezoneChangesTheReportingDay() throws Exception {
        String admin = superAdminToken();
        try {
            setLibraryTimezone(2L, "Pacific/Kiritimati");
            JsonNode ahead = dashboard(2L, admin);
            assertThat(ahead.get("timezone").asText()).isEqualTo("Pacific/Kiritimati");
            assertThat(ahead.get("reportingDate").asText())
                    .isEqualTo(LocalDate.now(ZoneId.of("Pacific/Kiritimati")).toString());

            setLibraryTimezone(2L, "Etc/GMT+12");
            JsonNode behind = dashboard(2L, admin);
            assertThat(behind.get("reportingDate").asText())
                    .isEqualTo(LocalDate.now(ZoneId.of("Etc/GMT+12")).toString());

            assertThat(ahead.get("reportingDate").asText())
                    .as("UTC+14 and UTC-12 are 26 hours apart and never share a date")
                    .isNotEqualTo(behind.get("reportingDate").asText());
        } finally {
            setLibraryTimezone(2L, "Asia/Kolkata");
        }
    }

    /**
     * The midnight boundary, tested through behaviour.
     *
     * A visit is recorded in library 2, which stamps attendance_date from the
     * server clock. The library is then moved to whichever extreme zone is on a
     * different calendar date from the server right now. That library's "today"
     * no longer contains the visit, so the dashboard must report zero. Only a
     * query that takes the day from library.timezone can produce that.
     */
    @Test
    public void aLibraryOnAnotherCalendarDayReportsNoAttendanceForToday() throws Exception {
        String owner = ownerToken();
        String admin = superAdminToken();

        // Student 4 belongs to library 2. Close anything already open for them.
        var history = mvc.perform(get("/api/students/4/attendance")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode row : data(history.getResponse().getContentAsString())) {
            if (row.get("open").asBoolean()) {
                mvc.perform(post("/api/attendance/" + row.get("attendanceId").asLong() + "/check-out")
                        .header("Authorization", "Bearer " + owner));
            }
        }

        var opened = mvc.perform(post("/api/libraries/2/attendance/check-in")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("studentId", 4))))
                .andExpect(status().isCreated())
                .andReturn();
        long visitId = data(opened.getResponse().getContentAsString()).get("attendanceId").asLong();

        try {
            // With the library on the server's own day, the visit is counted.
            assertThat(dashboard(2L, admin).get("attendanceToday").asLong())
                    .isGreaterThanOrEqualTo(1L);

            // Pick whichever extreme zone is on a different date from the server.
            LocalDate serverDay = LocalDate.now();
            String otherDayZone = LocalDate.now(ZoneId.of("Pacific/Kiritimati")).equals(serverDay)
                    ? "Etc/GMT+12"
                    : "Pacific/Kiritimati";

            setLibraryTimezone(2L, otherDayZone);
            JsonNode shifted = dashboard(2L, admin);

            assertThat(shifted.get("reportingDate").asText())
                    .as("the chosen zone must genuinely be on another calendar day")
                    .isNotEqualTo(serverDay.toString());
            assertThat(shifted.get("attendanceToday").asLong())
                    .as("a visit stamped on the server's day is not in another day's window")
                    .isZero();
            assertThat(shifted.get("studentsCurrentlyInside").asLong()).isZero();
        } finally {
            setLibraryTimezone(2L, "Asia/Kolkata");
            mvc.perform(post("/api/attendance/" + visitId + "/check-out")
                    .header("Authorization", "Bearer " + owner));
        }
    }

    /* ================================================ expiring memberships */

    @Test
    public void expiringMembershipsDefaultToFifteenDays() throws Exception {
        String token = ownerToken();

        var res = mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        for (JsonNode row : data(res.getResponse().getContentAsString())) {
            assertThat(row.get("status").asText()).isEqualTo("ACTIVE");
            LocalDate end = LocalDate.parse(row.get("endDate").asText());
            assertThat(end).isBetween(today, today.plusDays(15));
            assertThat(row.get("daysRemaining").asLong()).isBetween(0L, 15L);
        }
    }

    @Test
    public void aCustomWindowWidensTheResult() throws Exception {
        String token = ownerToken();

        var narrow = mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .param("days", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var wide = mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .param("days", "365")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(wide.getResponse().getContentAsString()).size())
                .isGreaterThanOrEqualTo(data(narrow.getResponse().getContentAsString()).size());

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        for (JsonNode row : data(wide.getResponse().getContentAsString())) {
            assertThat(LocalDate.parse(row.get("endDate").asText()))
                    .isBetween(today, today.plusDays(365));
        }
    }

    @Test
    public void anInvalidWindowIsABusinessErrorNotAnInternalError() throws Exception {
        String token = ownerToken();

        for (String days : new String[] {"0", "-5", "9999"}) {
            mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                            .param("days", days)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REPORT_WINDOW"));
        }

        mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .param("days", "not-a-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    /** The report observes memberships; it never changes one. */
    @Test
    public void theExpiringReportNeverChangesAMembership() throws Exception {
        String token = ownerToken();

        var before = mvc.perform(get("/api/student-memberships/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode original = data(before.getResponse().getContentAsString());

        mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .param("days", "365")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/student-memberships/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(original.get("status").asText()))
                .andExpect(jsonPath("$.data.endDate").value(original.get("endDate").asText()))
                .andExpect(jsonPath("$.data.version").value(original.get("version").asInt()));
    }

    @Test
    public void expiringMembershipsAreLibraryScoped() throws Exception {
        var res = mvc.perform(get("/api/libraries/3/reports/expiring-memberships")
                        .param("days", "365")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode row : data(res.getResponse().getContentAsString())) {
            // Library 3 holds only student 5.
            assertThat(row.get("studentId").asLong()).isEqualTo(5L);
        }
    }

    /* ==================================================== collection report */

    @Test
    public void theCollectionReportGroupsByDayAndMethod() throws Exception {
        String token = ownerToken();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        var res = mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", today.minusDays(30).toString())
                        .param("to", today.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"))
                .andReturn();

        JsonNode report = data(res.getResponse().getContentAsString());

        // The two breakdowns must reconcile with the headline total, exactly.
        BigDecimal byDay = BigDecimal.ZERO;
        for (JsonNode row : report.get("byDay")) {
            byDay = byDay.add(money(row.get("amount")));
            assertThat(LocalDate.parse(row.get("date").asText()))
                    .isBetween(today.minusDays(30), today);
        }
        BigDecimal byMethod = BigDecimal.ZERO;
        for (JsonNode row : report.get("byMethod")) {
            byMethod = byMethod.add(money(row.get("amount")));
        }

        BigDecimal total = money(report.get("totalCollected"));
        assertThat(byDay).usingComparator(BigDecimal::compareTo).isEqualTo(total);
        assertThat(byMethod).usingComparator(BigDecimal::compareTo).isEqualTo(total);
    }

    /** Only successful payments count, matching the finance module's balance rule. */
    @Test
    public void theCollectionReportCountsOnlySuccessfulPayments() throws Exception {
        String token = ownerToken();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        var res = mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", today.minusDays(30).toString())
                        .param("to", today.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = data(res.getResponse().getContentAsString());
        long counted = 0;
        for (JsonNode row : report.get("byMethod")) {
            counted += row.get("paymentCount").asLong();
        }
        assertThat(counted).isEqualTo(report.get("paymentCount").asLong());

        // The seeded 1500.00 and 700.00 receipts fall inside a 30-day window.
        assertThat(money(report.get("totalCollected")))
                .usingComparator(BigDecimal::compareTo)
                .isGreaterThanOrEqualTo(new BigDecimal("2200.00"));
    }

    @Test
    public void theCollectionReportIsLibraryScoped() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        var res = mvc.perform(get("/api/libraries/3/reports/collection")
                        .param("from", today.minusDays(365).toString())
                        .param("to", today.toString())
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = data(res.getResponse().getContentAsString());
        assertMoney(report.get("totalCollected"), "0.00");
        assertThat(report.get("paymentCount").asLong()).isZero();
        assertThat(report.get("byDay").size()).isZero();
    }

    @Test
    public void anInvalidCollectionRangeIsRejected() throws Exception {
        String token = ownerToken();

        mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", "2026-06-01").param("to", "2026-05-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REPORT_RANGE"));

        mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", "2000-01-01").param("to", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REPORT_RANGE"));

        mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", "not-a-date")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    /* ================================================= outstanding summary */

    @Test
    public void theOutstandingSummaryFollowsTheFinanceRule() throws Exception {
        String token = ownerToken();

        var res = mvc.perform(get("/api/libraries/1/reports/outstanding")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andReturn();

        JsonNode summary = data(res.getResponse().getContentAsString());

        BigDecimal invoiced = money(summary.get("totalInvoiced"));
        BigDecimal settled = money(summary.get("totalSettled"));
        BigDecimal outstanding = money(summary.get("totalOutstanding"));

        // Outstanding is invoiced less settled, exactly, in decimal.
        assertThat(outstanding)
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(invoiced.subtract(settled));

        assertThat(summary.get("invoiceCount").asLong()).isGreaterThanOrEqualTo(3L);
        assertThat(money(summary.get("overdueAmount")).signum()).isGreaterThanOrEqualTo(0);
        assertThat(summary.get("overdueInvoiceCount").asLong())
                .isLessThanOrEqualTo(summary.get("invoiceCount").asLong());
    }

    @Test
    public void theOutstandingSummaryIsLibraryScoped() throws Exception {
        var res = mvc.perform(get("/api/libraries/3/reports/outstanding")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode summary = data(res.getResponse().getContentAsString());
        // Library 3 has no invoices at all; the whole database has at least three.
        assertThat(summary.get("invoiceCount").asLong()).isZero();
        assertMoney(summary.get("totalInvoiced"), "0.00");
        assertMoney(summary.get("totalOutstanding"), "0.00");
        assertMoney(summary.get("overdueAmount"), "0.00");
    }

    /* ========================================================= permissions */

    /**
     * reception1 genuinely lacks REPORT_VIEW in the seeded role grants, so this
     * is a real permission test rather than a contrived one.
     */
    @Test
    public void aUserWithoutReportViewIsRefused() throws Exception {
        String token = receptionToken();

        for (String path : new String[] {
                "/api/libraries/1/dashboard",
                "/api/libraries/1/reports/expiring-memberships",
                "/api/libraries/1/reports/collection",
                "/api/libraries/1/reports/outstanding"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    public void usersWithReportViewSucceed() throws Exception {
        // owner1 and manager1 both hold REPORT_VIEW and belong to library 1.
        for (String token : new String[] {ownerToken(), managerToken()}) {
            mvc.perform(get("/api/libraries/1/dashboard")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.libraryId").value(1));
        }
    }

    @Test
    public void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/libraries/1/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mvc.perform(get("/api/libraries/1/reports/outstanding"))
                .andExpect(status().isUnauthorized());
    }

    /** manager1 holds REPORT_VIEW but belongs to library 1 only. */
    @Test
    public void reportingOnAnInaccessibleLibraryIsForbidden() throws Exception {
        String token = managerToken();

        for (String path : new String[] {
                "/api/libraries/2/dashboard",
                "/api/libraries/2/reports/expiring-memberships",
                "/api/libraries/2/reports/collection",
                "/api/libraries/2/reports/outstanding"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
    }

    @Test
    public void anUnknownLibraryIsNotFound() throws Exception {
        String token = superAdminToken();

        for (String path : new String[] {
                "/api/libraries/999999/dashboard",
                "/api/libraries/999999/reports/expiring-memberships",
                "/api/libraries/999999/reports/collection",
                "/api/libraries/999999/reports/outstanding"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
        }
    }

    /** Reporting is read-only: nothing but GET is offered. */
    @Test
    public void reportingEndpointsAcceptOnlyReads() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/libraries/1/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/api/libraries/1/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/api/libraries/1/reports/outstanding")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * A dashboard read must leave the data exactly as it found it.
     */
    @Test
    public void readingTheDashboardChangesNothing() throws Exception {
        String admin = superAdminToken();

        JsonNode before = dashboard(3L, admin);
        for (int i = 0; i < 3; i++) {
            dashboard(3L, admin);
        }
        JsonNode after = dashboard(3L, admin);

        assertThat(after.get("totalStudents").asLong()).isEqualTo(before.get("totalStudents").asLong());
        assertThat(after.get("totalSeats").asLong()).isEqualTo(before.get("totalSeats").asLong());
        assertThat(after.get("activeMemberships").asLong())
                .isEqualTo(before.get("activeMemberships").asLong());
        assertMoney(after.get("collectionToday"), "0.00");
    }

    /* ================================================ error-shape guarantee */

    @Test
    public void noExpectedErrorEverBecomesAnInternalError() throws Exception {
        String bearer = "Bearer " + ownerToken();
        var responses = new java.util.ArrayList<String>();

        responses.add(mvc.perform(get("/api/libraries/999999/dashboard").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/3/dashboard").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .param("days", "0").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/reports/expiring-memberships")
                        .param("days", "abc").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", "2026-12-31").param("to", "2026-01-01")
                        .header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/reports/collection")
                        .param("from", "nope").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/dashboard")
                        .header("Authorization", "Bearer " + receptionToken()))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/2/dashboard")
                        .header("Authorization", "Bearer " + managerToken()))
                .andReturn().getResponse().getContentAsString());

        for (String body : responses) {
            assertThat(body).doesNotContain("INTERNAL_ERROR");
            assertThat(body).doesNotContain("Unable to process the request");
        }
    }
}
