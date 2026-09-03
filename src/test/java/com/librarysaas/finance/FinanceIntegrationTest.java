package com.librarysaas.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2F fees and payments, exercised end to end against the seeded
 * Testcontainers MySQL with real JWTs.
 *
 * Seed data these tests rely on (V1__initial_schema.sql):
 *   fee_plan     1 MONTHLY STANDARD 1500.00 (lib 1), 2 MONTHLY PREMIUM 2000.00 (lib 1),
 *                3 (lib 2), 4 (lib 3)
 *   student_fee  1 INV001 student 1 total 1500.00 PAID
 *                2 INV002 student 2 total 1400.00 PARTIALLY_PAID (700.00 settled)
 *                3 INV003 student 3 total 1500.00 PENDING
 *   payment      1 REC001 1500.00 against fee 1, 2 REC002 700.00 against fee 2
 *
 *   user 1 superadmin  -> libraries 1, 2, 3 ; every financial permission
 *   user 2 owner1      -> libraries 1, 2    ; every financial permission
 *   user 3 manager1    -> library 1 only    ; FEE_PLAN and PAYMENT, no refund
 *   user 4 reception1  -> libraries 1, 2    ; PAYMENT only, no FEE_PLAN permission
 *
 * Money is compared by value with BigDecimal.compareTo throughout. No assertion
 * here goes through a double, so 1500.00 and 1500.0 are treated as equal amounts
 * and no rounding can creep into a test.
 *
 * Tests that need to mutate an invoice raise their own rather than touching the
 * seeded three, so the class is independent of the order it runs in.
 */
public class FinanceIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** Keeps invoice and receipt numbers unique across the whole class. */
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

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

    /** reception1 may take payments but holds no fee-plan permission. */
    private String receptionToken() throws Exception {
        return login("reception1@brightfuture.example", "Password@123");
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).at("/data");
    }

    /** Exact decimal comparison; never a double. */
    private void assertMoney(JsonNode node, String expected) {
        assertThat(node.isNull()).isFalse();
        assertThat(new BigDecimal(node.asText()))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal(expected));
    }

    private String planBody(String name, String amount, int durationValue, String unit)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("amount", amount);
        body.put("durationValue", durationValue);
        body.put("durationUnit", unit);
        return mapper.writeValueAsString(body);
    }

    private String feeBody(Long studentId, Long planId, Long membershipId, String invoiceNumber,
                           String amount, String discount, String tax, String dueDate)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (studentId != null) body.put("studentId", studentId);
        if (planId != null) body.put("feePlanId", planId);
        if (membershipId != null) body.put("membershipId", membershipId);
        if (invoiceNumber != null) body.put("invoiceNumber", invoiceNumber);
        if (amount != null) body.put("amount", amount);
        if (discount != null) body.put("discountAmount", discount);
        if (tax != null) body.put("taxAmount", tax);
        if (dueDate != null) body.put("dueDate", dueDate);
        return mapper.writeValueAsString(body);
    }

    private String paymentBody(String receipt, String amount, String method, String reference)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (receipt != null) body.put("receiptNumber", receipt);
        if (amount != null) body.put("amount", amount);
        if (method != null) body.put("paymentMethod", method);
        if (reference != null) body.put("transactionReference", reference);
        return mapper.writeValueAsString(body);
    }

    /** Raises a fresh invoice for student 3 in library 1 and returns its id. */
    private long newInvoice(String token, String total) throws Exception {
        String number = "INV-T" + SEQ.incrementAndGet();
        var res = mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, number, total, null, null, "2026-12-31")))
                .andExpect(status().isCreated())
                .andReturn();
        return data(res.getResponse().getContentAsString()).get("studentFeeId").asLong();
    }

    /* ============================================================ fee plans */

    @Test
    public void listsLibraryFeePlansWithTheirPrices() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode plans = data(res.getResponse().getContentAsString());
        assertThat(plans.size()).isGreaterThanOrEqualTo(2);

        boolean sawStandard = false;
        for (JsonNode plan : plans) {
            assertThat(plan.get("libraryId").asLong()).isEqualTo(1L);
            if ("MONTHLY STANDARD".equals(plan.get("name").asText())) {
                sawStandard = true;
                assertMoney(plan.get("amount"), "1500.00");
                assertThat(plan.get("durationUnit").asText()).isEqualTo("MONTH");
                assertThat(plan.get("status").asText()).isEqualTo("ACTIVE");
            }
        }
        assertThat(sawStandard).isTrue();
    }

    @Test
    public void filtersFeePlansByStatus() throws Exception {
        String token = ownerToken();

        var active = mvc.perform(get("/api/libraries/1/fee-plans")
                        .param("status", "active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode plan : data(active.getResponse().getContentAsString())) {
            assertThat(plan.get("status").asText()).isEqualTo("ACTIVE");
        }

        mvc.perform(get("/api/libraries/1/fee-plans")
                        .param("status", "ARCHIVED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FEE_PLAN_STATUS"));
    }

    @Test
    public void getsOneFeePlanAndRejectsAnUnknownOne() throws Exception {
        String token = ownerToken();

        mvc.perform(get("/api/fee-plans/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feePlanId").value(1))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andExpect(jsonPath("$.data.name").value("MONTHLY STANDARD"));

        mvc.perform(get("/api/fee-plans/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FEE_PLAN_NOT_FOUND"));
    }

    @Test
    public void createsUpdatesAndRetiresAFeePlan() throws Exception {
        String token = ownerToken();
        String name = "TEST PLAN " + SEQ.incrementAndGet();

        var created = mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name, "1234.50", 3, "MONTH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.libraryId").value(1))
                .andReturn();

        JsonNode plan = data(created.getResponse().getContentAsString());
        long id = plan.get("feePlanId").asLong();
        // The price survives the round trip exactly, to the paisa.
        assertMoney(plan.get("amount"), "1234.50");

        mvc.perform(put("/api/fee-plans/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name, "1300.75", 3, "MONTH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feePlanId").value(id));

        var reread = mvc.perform(get("/api/fee-plans/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertMoney(data(reread.getResponse().getContentAsString()).get("amount"), "1300.75");

        // Retiring keeps the row, so invoices raised from it stay valid.
        mvc.perform(put("/api/fee-plans/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mvc.perform(put("/api/fee-plans/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    public void duplicateFeePlanNameInTheSameLibraryIsAConflict() throws Exception {
        mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("MONTHLY STANDARD", "1500.00", 1, "MONTH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FEE_PLAN_NAME_ALREADY_EXISTS"));
    }

    /** The name is unique per library, so the same one is free in another. */
    @Test
    public void theSameFeePlanNameIsAllowedInADifferentLibrary() throws Exception {
        String name = "SHARED NAME " + SEQ.incrementAndGet();
        String token = ownerToken();

        mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name, "100.00", 1, "MONTH")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/libraries/2/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name, "100.00", 1, "MONTH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.libraryId").value(2));
    }

    @Test
    public void aFeePlanCannotBePricedNegatively() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("NEGATIVE " + SEQ.incrementAndGet(), "-1.00", 1, "MONTH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.amount").exists());

        mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.amount").exists());
    }

    /* ========================================================= student fees */

    @Test
    public void listsInvoicesWithPaidAndOutstandingAmounts() throws Exception {
        var res = mvc.perform(get("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fees = data(res.getResponse().getContentAsString());
        assertThat(fees.size()).isGreaterThanOrEqualTo(3);

        for (JsonNode fee : fees) {
            assertThat(fee.get("libraryId").asLong()).isEqualTo(1L);
            String invoice = fee.get("invoiceNumber").asText();

            if ("INV001".equals(invoice)) {
                // Fully settled by the seeded 1500.00 payment.
                assertMoney(fee.get("totalAmount"), "1500.00");
                assertMoney(fee.get("paidAmount"), "1500.00");
                assertMoney(fee.get("balanceAmount"), "0.00");
                assertThat(fee.get("status").asText()).isEqualTo("PAID");
            } else if ("INV002".equals(invoice)) {
                // 1500.00 less a 100.00 discount, of which 700.00 is settled.
                assertMoney(fee.get("totalAmount"), "1400.00");
                assertMoney(fee.get("paidAmount"), "700.00");
                assertMoney(fee.get("balanceAmount"), "700.00");
                assertThat(fee.get("status").asText()).isEqualTo("PARTIALLY_PAID");
            } else if ("INV003".equals(invoice)) {
                assertMoney(fee.get("totalAmount"), "1500.00");
                assertMoney(fee.get("paidAmount"), "0.00");
                assertMoney(fee.get("balanceAmount"), "1500.00");
                assertThat(fee.get("status").asText()).isEqualTo("PENDING");
            }
        }
    }

    @Test
    public void filtersInvoicesByStatusAndRejectsAnUnknownOne() throws Exception {
        String token = ownerToken();

        var paid = mvc.perform(get("/api/libraries/1/student-fees")
                        .param("status", "paid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = data(paid.getResponse().getContentAsString());
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode fee : rows) {
            assertThat(fee.get("status").asText()).isEqualTo("PAID");
        }

        mvc.perform(get("/api/libraries/1/student-fees")
                        .param("status", "WRITTEN_OFF")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STUDENT_FEE_STATUS"));
    }

    @Test
    public void computesTheTotalFromItsPartsAndIgnoresAnyTotalSent() throws Exception {
        String token = ownerToken();
        String number = "INV-CALC" + SEQ.incrementAndGet();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", 3);
        body.put("invoiceNumber", number);
        body.put("amount", "1000.00");
        body.put("discountAmount", "150.50");
        body.put("taxAmount", "50.25");
        body.put("dueDate", "2026-12-31");
        // A caller-supplied total must not be able to contradict the parts.
        body.put("totalAmount", "999999.00");

        var res = mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        JsonNode fee = data(res.getResponse().getContentAsString());
        // 1000.00 - 150.50 + 50.25 = 899.75, exactly.
        assertMoney(fee.get("totalAmount"), "899.75");
        assertMoney(fee.get("paidAmount"), "0.00");
        assertMoney(fee.get("balanceAmount"), "899.75");
    }

    @Test
    public void takesTheAmountFromTheFeePlanWhenNoneIsGiven() throws Exception {
        String token = ownerToken();
        String number = "INV-PLAN" + SEQ.incrementAndGet();

        var res = mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, 1L, null, number, null, null, null, "2026-12-31")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.feePlanId").value(1))
                .andExpect(jsonPath("$.data.feePlanName").value("MONTHLY STANDARD"))
                .andReturn();

        JsonNode fee = data(res.getResponse().getContentAsString());
        assertMoney(fee.get("amount"), "1500.00");
        assertMoney(fee.get("totalAmount"), "1500.00");
    }

    @Test
    public void anInvoiceNeedsAnAmountWhenNoPlanIsChosen() throws Exception {
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, "INV-NOAMT" + SEQ.incrementAndGet(),
                                null, null, null, "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FEE_AMOUNT"));
    }

    @Test
    public void aDiscountCannotDriveAnInvoiceNegative() throws Exception {
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, "INV-NEG" + SEQ.incrementAndGet(),
                                "100.00", "500.00", "0.00", "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FEE_AMOUNT"));
    }

    @Test
    public void negativeMonetaryPartsAreRejected() throws Exception {
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, "INV-NEG2" + SEQ.incrementAndGet(),
                                "100.00", "-5.00", "0.00", "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    public void duplicateInvoiceNumberInTheSameLibraryIsAConflict() throws Exception {
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, "INV001", "100.00", null, null, "2026-12-31")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVOICE_NUMBER_ALREADY_EXISTS"));
    }

    @Test
    public void aRetiredFeePlanCannotBeBilledAgainst() throws Exception {
        String token = ownerToken();
        String name = "RETIRED " + SEQ.incrementAndGet();

        var created = mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(name, "500.00", 1, "MONTH")))
                .andExpect(status().isCreated())
                .andReturn();
        long planId = data(created.getResponse().getContentAsString()).get("feePlanId").asLong();

        mvc.perform(put("/api/fee-plans/" + planId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, planId, null, "INV-RET" + SEQ.incrementAndGet(),
                                null, null, null, "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FEE_PLAN_INACTIVE"));
    }

    @Test
    public void listsOneStudentsInvoices() throws Exception {
        var res = mvc.perform(get("/api/students/1/fees")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fees = data(res.getResponse().getContentAsString());
        assertThat(fees.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode fee : fees) {
            assertThat(fee.get("studentId").asLong()).isEqualTo(1L);
        }
    }

    @Test
    public void unknownInvoiceStudentOrLibraryIs404() throws Exception {
        String token = superAdminToken();

        mvc.perform(get("/api/student-fees/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_FEE_NOT_FOUND"));

        mvc.perform(get("/api/students/999999/fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));

        mvc.perform(get("/api/libraries/999999/student-fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LIBRARY_NOT_FOUND"));
    }

    /* ====================================================== tenant boundary */

    /**
     * The rule that matters most here: an invoice may never bill a student of
     * another library, and money may never cross a tenant.
     *
     * The caller is the super admin deliberately. A super admin's tenant access
     * is unrestricted, so this is exactly the case where a check written against
     * the caller's privileges rather than the target row would wrongly pass.
     * That was the Phase 2C defect and it must not recur in a financial module.
     */
    @Test
    public void anInvoiceCannotBillAStudentFromAnotherLibrary() throws Exception {
        String admin = superAdminToken();

        // Student 4 belongs to library 2.
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(4L, null, null, "INV-X1" + SEQ.incrementAndGet(),
                                "100.00", null, null, "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));

        // Student 5 is in library 3, under a different organization entirely.
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(5L, null, null, "INV-X2" + SEQ.incrementAndGet(),
                                "100.00", null, null, "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_IN_LIBRARY"));

        // Nothing was written for either student in library 1.
        var res = mvc.perform(get("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode fee : data(res.getResponse().getContentAsString())) {
            assertThat(fee.get("studentId").asLong()).isNotEqualTo(4L);
            assertThat(fee.get("studentId").asLong()).isNotEqualTo(5L);
        }
    }

    @Test
    public void anInvoiceCannotUseAFeePlanFromAnotherLibrary() throws Exception {
        // Fee plan 3 belongs to library 2, plan 4 to library 3.
        String admin = superAdminToken();

        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, 3L, null, "INV-XP1" + SEQ.incrementAndGet(),
                                null, null, null, "2026-12-31")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FEE_PLAN_NOT_FOUND"));

        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, 4L, null, "INV-XP2" + SEQ.incrementAndGet(),
                                null, null, null, "2026-12-31")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FEE_PLAN_NOT_FOUND"));
    }

    @Test
    public void anInvoiceCannotBillAnotherStudentsMembership() throws Exception {
        // Membership 1 belongs to student 1; this invoice is for student 3.
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, 1L, "INV-XM" + SEQ.incrementAndGet(),
                                "100.00", null, null, "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_NOT_FOR_STUDENT"));
    }

    /* ============================================================= payments */

    @Test
    public void listsPaymentsForALibraryAnInvoiceAndAStudent() throws Exception {
        String token = ownerToken();

        var library = mvc.perform(get("/api/libraries/1/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(library.getResponse().getContentAsString()).size()).isGreaterThanOrEqualTo(2);

        var invoice = mvc.perform(get("/api/student-fees/1/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = data(invoice.getResponse().getContentAsString());
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        assertMoney(rows.get(0).get("amount"), "1500.00");
        assertThat(rows.get(0).get("receiptNumber").asText()).isEqualTo("REC001");

        var student = mvc.perform(get("/api/students/2/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode payment : data(student.getResponse().getContentAsString())) {
            assertThat(payment.get("studentId").asLong()).isEqualTo(2L);
        }
    }

    @Test
    public void getsOnePaymentAndRejectsAnUnknownOne() throws Exception {
        String token = ownerToken();

        mvc.perform(get("/api/payments/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptNumber").value("REC001"))
                .andExpect(jsonPath("$.data.paymentMethod").value("UPI"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mvc.perform(get("/api/payments/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    public void recordsAPartialPaymentThenSettlesTheInvoice() throws Exception {
        String token = ownerToken();
        long feeId = newInvoice(token, "1000.00");

        var first = mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-T" + SEQ.incrementAndGet(), "400.00", "cash", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"))
                .andReturn();

        JsonNode payment = data(first.getResponse().getContentAsString());
        assertMoney(payment.get("amount"), "400.00");
        // The student and library are inherited from the invoice, not the body.
        assertThat(payment.get("studentId").asLong()).isEqualTo(3L);
        assertThat(payment.get("libraryId").asLong()).isEqualTo(1L);
        assertThat(payment.get("studentFeeId").asLong()).isEqualTo(feeId);

        var afterFirst = mvc.perform(get("/api/student-fees/" + feeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"))
                .andReturn();
        JsonNode fee = data(afterFirst.getResponse().getContentAsString());
        assertMoney(fee.get("paidAmount"), "400.00");
        assertMoney(fee.get("balanceAmount"), "600.00");

        // Settling the exact remaining balance closes the invoice.
        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-T" + SEQ.incrementAndGet(), "600.00", "UPI", "TXN-1")))
                .andExpect(status().isCreated());

        var settled = mvc.perform(get("/api/student-fees/" + feeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andReturn();
        JsonNode closed = data(settled.getResponse().getContentAsString());
        assertMoney(closed.get("paidAmount"), "1000.00");
        assertMoney(closed.get("balanceAmount"), "0.00");

        // A settled invoice takes no more money.
        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-T" + SEQ.incrementAndGet(), "1.00", "CASH", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_FEE_ALREADY_PAID"));
    }

    @Test
    public void aPaymentCannotExceedTheOutstandingBalance() throws Exception {
        String token = ownerToken();
        long feeId = newInvoice(token, "500.00");

        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-OVER" + SEQ.incrementAndGet(), "500.01", "CASH", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_EXCEEDS_BALANCE"));

        // The rejected payment left nothing behind.
        var fee = mvc.perform(get("/api/student-fees/" + feeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        assertMoney(data(fee.getResponse().getContentAsString()).get("paidAmount"), "0.00");
    }

    /** The seeded partly-paid invoice must not be overpaid either. */
    @Test
    public void overpaymentIsRefusedAgainstAPartlyPaidInvoice() throws Exception {
        String token = ownerToken();

        // Invoice 2 has 700.00 outstanding of 1400.00.
        mvc.perform(post("/api/student-fees/2/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-OVER2" + SEQ.incrementAndGet(), "800.00", "CASH", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_EXCEEDS_BALANCE"));

        var fee = mvc.perform(get("/api/student-fees/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode row = data(fee.getResponse().getContentAsString());
        assertMoney(row.get("paidAmount"), "700.00");
        assertMoney(row.get("balanceAmount"), "700.00");
    }

    @Test
    public void aDuplicateReceiptNumberIsRefused() throws Exception {
        String token = ownerToken();
        long feeId = newInvoice(token, "100.00");

        // REC001 is seeded in library 1.
        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC001", "10.00", "CASH", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_NUMBER_ALREADY_EXISTS"));
    }

    @Test
    public void aPaymentMustBeGreaterThanZero() throws Exception {
        String token = ownerToken();
        long feeId = newInvoice(token, "100.00");

        for (String amount : new String[] {"0.00", "-5.00"}) {
            mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(paymentBody("REC-BAD" + SEQ.incrementAndGet(), amount, "CASH", null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.amount").exists());
        }

        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.receiptNumber").exists())
                .andExpect(jsonPath("$.data.amount").exists());
    }

    @Test
    public void paymentAgainstAnUnknownInvoiceIs404() throws Exception {
        mvc.perform(post("/api/student-fees/999999/payments")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-NF" + SEQ.incrementAndGet(), "10.00", "CASH", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_FEE_NOT_FOUND"));
    }

    /** Payments are append-only: the schema has no updated_at to record a change. */
    @Test
    public void paymentsCannotBeEditedOrDeleted() throws Exception {
        String token = ownerToken();

        mvc.perform(put("/api/payments/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-EDIT", "1.00", "CASH", null)))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(delete("/api/payments/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
    }

    /* ======================================================== authorisation */

    @Test
    public void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/libraries/1/fee-plans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mvc.perform(get("/api/libraries/1/student-fees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/libraries/1/payments")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/student-fees/3/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-ANON", "10.00", "CASH", null)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * reception1 may take money but holds no fee-plan permission, so the billing
     * side of the module is closed to them while payments stay open.
     */
    @Test
    public void aReceptionistCanTakePaymentsButNotManageBilling() throws Exception {
        String token = receptionToken();

        mvc.perform(get("/api/libraries/1/fee-plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/libraries/1/student-fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/libraries/1/fee-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("RECEPTION " + SEQ.incrementAndGet(), "10.00", 1, "MONTH")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/libraries/1/student-fees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, "INV-REC" + SEQ.incrementAndGet(),
                                "10.00", null, null, "2026-12-31")))
                .andExpect(status().isForbidden());

        // Payments remain available to them.
        mvc.perform(get("/api/libraries/1/payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long feeId = newInvoice(ownerToken(), "50.00");
        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-RCPT" + SEQ.incrementAndGet(), "50.00", "CASH", null)))
                .andExpect(status().isCreated());
    }

    /** manager1 belongs to library 1 only. Library 2 is another tenant to them. */
    @Test
    public void crossTenantFinancialReadsAreForbidden() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/2/fee-plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(get("/api/libraries/2/student-fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/libraries/2/payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Fee plan 3 belongs to library 2.
        mvc.perform(get("/api/fee-plans/3").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Student 4 belongs to library 2.
        mvc.perform(get("/api/students/4/fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/students/4/payments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    public void crossTenantFinancialWritesAreForbidden() throws Exception {
        String manager = managerToken();
        String owner = ownerToken();

        mvc.perform(post("/api/libraries/2/fee-plans")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("XT " + SEQ.incrementAndGet(), "10.00", 1, "MONTH")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/libraries/2/student-fees")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(4L, null, null, "INV-XT" + SEQ.incrementAndGet(),
                                "10.00", null, null, "2026-12-31")))
                .andExpect(status().isForbidden());

        // Raise an invoice in library 2 as someone who may, then prove manager1
        // can neither read it nor pay against it.
        String number = "INV-XT2" + SEQ.incrementAndGet();
        var created = mvc.perform(post("/api/libraries/2/student-fees")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(4L, null, null, number, "300.00", null, null, "2026-12-31")))
                .andExpect(status().isCreated())
                .andReturn();
        long feeId = data(created.getResponse().getContentAsString()).get("studentFeeId").asLong();

        mvc.perform(get("/api/student-fees/" + feeId).header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(post("/api/student-fees/" + feeId + "/payments")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-XT" + SEQ.incrementAndGet(), "10.00", "CASH", null)))
                .andExpect(status().isForbidden());

        // The refused payment changed nothing.
        var fee = mvc.perform(get("/api/student-fees/" + feeId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        assertMoney(data(fee.getResponse().getContentAsString()).get("paidAmount"), "0.00");
    }

    /**
     * An unreachable tenant and a missing one must not be distinguishable by a
     * different error shape.
     */
    @Test
    public void unreachableAndMissingResourcesStayNonInformative() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/libraries/2/fee-plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/libraries/999999/fee-plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /* ================================================ error-shape guarantee */

    /**
     * Every expected failure has to arrive as its own business error. A 500 with
     * INTERNAL_ERROR here would mean a financial rule is throwing something the
     * handler does not recognise.
     */
    @Test
    public void noExpectedErrorEverBecomesAnInternalError() throws Exception {
        String bearer = "Bearer " + ownerToken();
        long feeId = newInvoice(ownerToken(), "100.00");
        var responses = new java.util.ArrayList<String>();

        responses.add(mvc.perform(get("/api/libraries/999999/fee-plans").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/fee-plans/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/student-fees/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/payments/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/fee-plans")
                        .param("status", "NONSENSE").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/libraries/1/student-fees")
                        .param("status", "NONSENSE").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/fee-plans").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/fee-plans").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-fees").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(4L, null, null, "INV-IE" + SEQ.incrementAndGet(),
                                "10.00", null, null, "2026-12-31")))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/libraries/1/student-fees").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feeBody(3L, null, null, "INV001", "10.00", null, null, "2026-12-31")))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/student-fees/" + feeId + "/payments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-IE" + SEQ.incrementAndGet(), "9999.00", "CASH", null)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/student-fees/" + feeId + "/payments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC001", "1.00", "CASH", null)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/student-fees/1/payments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("REC-IE2" + SEQ.incrementAndGet(), "1.00", "CASH", null)))
                .andReturn().getResponse().getContentAsString());

        for (String body : responses) {
            assertThat(body).doesNotContain("INTERNAL_ERROR");
            assertThat(body).doesNotContain("Unable to process the request");
        }
    }
}
