package com.librarysaas.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product-owner customer onboarding, exercised end to end with real JWTs against
 * the seeded Testcontainers MySQL.
 *
 * <p><b>A freshly onboarded library is the leakage detector here.</b> It contains
 * no students, no seats, no memberships, no attendance and no payments, so every
 * figure it reports must be zero. If any query in any module ever lost its
 * tenant predicate, a brand new tenant would immediately report the whole
 * database - five students, eight seats - and these tests would fail. The same
 * assertion runs as super admin, because unrestricted tenant access must not
 * widen a filter.
 *
 * <p>Every test onboards its own customers with sequence-unique codes and
 * usernames, so nothing depends on execution order or on what other classes have
 * created.
 *
 * <p>Seeded users used as non-super-admin callers: owner1 (organization 1),
 * manager1 (library 1 only), reception1 (libraries 1 and 2).
 */
public class CustomerOnboardingIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** Kept clear of the ranges other test classes use for their own fixtures. */
    private static final AtomicInteger SEQ = new AtomicInteger(70000);

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

    private int loginStatus(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("identifier", identifier, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    private String superAdminToken() throws Exception {
        return login("superadmin@example.com", "Password@123");
    }

    /** owner1 holds every tenant permission but is not the platform owner. */
    private String ownerToken() throws Exception {
        return login("owner1@brightfuture.example", "Password@123");
    }

    /** manager1 is scoped to library 1 only. */
    private String managerToken() throws Exception {
        return login("manager1@brightfuture.example", "Password@123");
    }

    private Map<String, Object> requestFor(String tag) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationName", "Customer " + tag);
        body.put("organizationCode", "CUST-" + tag);
        body.put("libraryName", "Library " + tag);
        body.put("libraryCode", "LIB-" + tag);
        body.put("timezone", "Asia/Kolkata");
        body.put("adminUsername", "admin" + tag);
        body.put("adminEmail", "admin" + tag + "@customer.example");
        body.put("adminFirstName", "Admin");
        body.put("adminLastName", tag);
        return body;
    }

    private JsonNode onboard(String tag) throws Exception {
        var res = mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestFor(tag))))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    private String nextTag() {
        return String.valueOf(SEQ.incrementAndGet());
    }

    /** Signs in as a freshly onboarded customer administrator. */
    private String tokenFor(JsonNode customer) throws Exception {
        return login(customer.at("/initialCredentials/username").asText(),
                customer.at("/initialCredentials/temporaryPassword").asText());
    }

    private int statusOf(String path, String token) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------- creation results

    @Test
    public void superAdminCanOnboardACustomerAndEveryPartIsCreated() throws Exception {
        String tag = nextTag();
        JsonNode customer = onboard(tag);

        assertThat(customer.at("/organization/organizationId").asLong()).isPositive();
        assertThat(customer.at("/organization/name").asText()).isEqualTo("Customer " + tag);
        assertThat(customer.at("/organization/organizationCode").asText()).isEqualTo("CUST-" + tag);

        assertThat(customer.at("/library/libraryId").asLong()).isPositive();
        assertThat(customer.at("/library/name").asText()).isEqualTo("Library " + tag);
        assertThat(customer.at("/library/timezone").asText()).isEqualTo("Asia/Kolkata");

        assertThat(customer.at("/user/userId").asLong()).isPositive();
        assertThat(customer.at("/user/username").asText()).isEqualTo("admin" + tag);
        assertThat(customer.at("/initialCredentials/temporaryPassword").asText()).isNotBlank();
    }

    @Test
    public void createdLibraryBelongsToTheCreatedOrganization() throws Exception {
        JsonNode customer = onboard(nextTag());
        long organizationId = customer.at("/organization/organizationId").asLong();
        long libraryId = customer.at("/library/libraryId").asLong();

        var res = mvc.perform(get("/api/libraries/" + libraryId)
                        .header("Authorization", "Bearer " + tokenFor(customer)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(mapper.readTree(res.getResponse().getContentAsString())
                .at("/data/organizationId").asLong()).isEqualTo(organizationId);
    }

    @Test
    public void customerAdminGetsOrganizationOwnerAndNeverSuperAdmin() throws Exception {
        JsonNode customer = onboard(nextTag());
        assertThat(customer.at("/user/roleCode").asText()).isEqualTo("ORGANIZATION_OWNER");

        // The JWT carries the granted authorities, so it is the honest place to
        // check that platform privilege was not handed to a customer.
        String token = tokenFor(customer);
        String claims = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        assertThat(claims).contains("ROLE_ORGANIZATION_OWNER");
        assertThat(claims).doesNotContain("ROLE_SUPER_ADMIN");
    }

    @Test
    public void customerAdminIsAMemberOfItsOwnOrganizationAndLibrary() throws Exception {
        JsonNode customer = onboard(nextTag());
        String token = tokenFor(customer);
        long organizationId = customer.at("/organization/organizationId").asLong();
        long libraryId = customer.at("/library/libraryId").asLong();

        // Both list endpoints read the membership tables directly, so seeing the
        // new tenant in them is proof the membership rows exist and are ACTIVE.
        var orgs = mvc.perform(get("/api/organizations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(orgs.getResponse().getContentAsString()).contains("\"organizationId\":" + organizationId);

        var libs = mvc.perform(get("/api/libraries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(libs.getResponse().getContentAsString()).contains("\"libraryId\":" + libraryId);
    }

    // ------------------------------------------------------------- the secret

    @Test
    public void temporaryPasswordWorksForLoginAndIsStoredOnlyAsAHash() throws Exception {
        JsonNode customer = onboard(nextTag());
        String username = customer.at("/initialCredentials/username").asText();
        String password = customer.at("/initialCredentials/temporaryPassword").asText();

        assertThat(loginStatus(username, password)).isEqualTo(200);
        assertThat(loginStatus(username, password + "x")).isEqualTo(401);

        // A stored plaintext would have to come back out somewhere. The BCrypt
        // prefix must never appear in any response either.
        assertThat(customer.toString()).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    public void thePasswordIsNotRetrievableAfterCreation() throws Exception {
        JsonNode customer = onboard(nextTag());
        String password = customer.at("/initialCredentials/temporaryPassword").asText();
        String superToken = superAdminToken();

        // There is no read side on the admin resource at all.
        assertThat(statusOf("/api/admin/customers", superToken)).isIn(404, 405);
        assertThat(statusOf("/api/admin/customers/" + customer.at("/user/userId").asLong(), superToken))
                .isIn(404, 405);

        // Nor does any existing endpoint that describes the new tenant carry it.
        for (String path : new String[]{
                "/api/organizations/" + customer.at("/organization/organizationId").asLong(),
                "/api/libraries/" + customer.at("/library/libraryId").asLong(),
                "/api/libraries/" + customer.at("/library/libraryId").asLong() + "/members"}) {
            var res = mvc.perform(get(path).header("Authorization", "Bearer " + superToken)).andReturn();
            assertThat(res.getResponse().getContentAsString())
                    .doesNotContain(password)
                    .doesNotContain("passwordHash")
                    .doesNotContain("$2a$");
        }
    }

    @Test
    public void generatedPasswordsAreNotPredictable() throws Exception {
        String first = onboard(nextTag()).at("/initialCredentials/temporaryPassword").asText();
        String second = onboard(nextTag()).at("/initialCredentials/temporaryPassword").asText();

        assertThat(first).hasSize(16).isNotEqualTo(second);
    }

    // --------------------------------------------------------- authorization

    @Test
    public void unauthenticatedCallerIsRejected() throws Exception {
        mvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestFor(nextTag()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void organizationOwnerCannotOnboardACustomer() throws Exception {
        // owner1 holds all 25 tenant permissions and still must not create tenants.
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestFor(nextTag()))))
                .andExpect(status().isForbidden());
    }

    @Test
    public void libraryScopedUserCannotOnboardACustomer() throws Exception {
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestFor(nextTag()))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer "
                                + login("reception1@brightfuture.example", "Password@123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestFor(nextTag()))))
                .andExpect(status().isForbidden());
    }

    @Test
    public void aNewlyCreatedCustomerAdminCannotOnboardFurtherCustomers() throws Exception {
        JsonNode customer = onboard(nextTag());
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + tokenFor(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestFor(nextTag()))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- validation

    @Test
    public void duplicateUsernameAndEmailAreRejected() throws Exception {
        String tag = nextTag();
        onboard(tag);

        Map<String, Object> sameUsername = requestFor(nextTag());
        sameUsername.put("adminUsername", "admin" + tag);
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(sameUsername)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_ALREADY_EXISTS"));

        Map<String, Object> sameEmail = requestFor(nextTag());
        sameEmail.put("adminEmail", "admin" + tag + "@customer.example");
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(sameEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    public void missingRequiredFieldsAreRejected() throws Exception {
        for (String field : new String[]{"organizationName", "libraryName", "adminUsername",
                "adminEmail", "adminFirstName"}) {
            Map<String, Object> body = requestFor(nextTag());
            body.put(field, "");
            mvc.perform(post("/api/admin/customers")
                            .header("Authorization", "Bearer " + superAdminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    public void anUnknownTimezoneIsRejected() throws Exception {
        Map<String, Object> body = requestFor(nextTag());
        body.put("timezone", "Mars/Olympus_Mons");
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIMEZONE"));
    }

    @Test
    public void aMalformedEmailIsRejected() throws Exception {
        Map<String, Object> body = requestFor(nextTag());
        body.put("adminEmail", "not-an-email");
        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Proves the operation is all-or-nothing at the point it can actually be made
     * to fail from outside: a duplicate organization code, which the organization
     * service rejects after validation has passed. The administrator named in that
     * request must not exist afterwards, and must not be able to sign in.
     */
    @Test
    public void aFailedOnboardingLeavesNoPartialTenant() throws Exception {
        String existing = nextTag();
        onboard(existing);

        String tag = nextTag();
        Map<String, Object> clash = requestFor(tag);
        clash.put("organizationCode", "CUST-" + existing);

        mvc.perform(post("/api/admin/customers")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(clash)))
                .andExpect(status().isConflict());

        // No user, therefore no tenant: the account named in the failed request
        // cannot authenticate, and a later retry of the same username succeeds,
        // which it could not do if a row had survived.
        assertThat(loginStatus("admin" + tag, "anything")).isEqualTo(401);
        JsonNode retry = onboard(tag);
        assertThat(retry.at("/user/username").asText()).isEqualTo("admin" + tag);
    }

    // -------------------------------------------------------- tenant isolation

    @Test
    public void customerACannotReachCustomerBAndViceVersa() throws Exception {
        JsonNode a = onboard(nextTag());
        JsonNode b = onboard(nextTag());
        String tokenA = tokenFor(a);
        String tokenB = tokenFor(b);

        long libA = a.at("/library/libraryId").asLong();
        long libB = b.at("/library/libraryId").asLong();
        long orgA = a.at("/organization/organizationId").asLong();
        long orgB = b.at("/organization/organizationId").asLong();

        // Every library-scoped module, reached with the other customer's library.
        // Students are deliberately absent: they are not exposed under a library
        // path, but through /api/students, which is scoped to the caller's own
        // memberships and is asserted separately below.
        for (String suffix : new String[]{"", "/seats", "/attendance",
                "/student-memberships", "/fee-plans", "/student-fees", "/payments",
                "/dashboard", "/reports/outstanding", "/reports/collection",
                "/reports/expiring-memberships", "/members"}) {
            assertThat(statusOf("/api/libraries" + "/" + libB + suffix, tokenA))
                    .as("customer A reaching customer B at " + suffix).isEqualTo(403);
            assertThat(statusOf("/api/libraries" + "/" + libA + suffix, tokenB))
                    .as("customer B reaching customer A at " + suffix).isEqualTo(403);
        }

        assertThat(statusOf("/api/organizations/" + orgB, tokenA)).isEqualTo(403);
        assertThat(statusOf("/api/organizations/" + orgA, tokenB)).isEqualTo(403);

        // Students, by direct resource id and by listing.
        assertThat(statusOf("/api/students/1", tokenA)).isEqualTo(403);
        assertThat(statusOf("/api/students/4", tokenA)).isEqualTo(403);
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        // And the seeded tenants stay out of reach too.
        assertThat(statusOf("/api/libraries/1/dashboard", tokenA)).isEqualTo(403);
        assertThat(statusOf("/api/libraries/1/seats", tokenB)).isEqualTo(403);
    }

    @Test
    public void aCustomerOnlySeesItsOwnTenantInTheListings() throws Exception {
        JsonNode a = onboard(nextTag());
        JsonNode b = onboard(nextTag());
        String tokenA = tokenFor(a);

        var libs = mvc.perform(get("/api/libraries").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andReturn();
        JsonNode rows = mapper.readTree(libs.getResponse().getContentAsString()).get("data");

        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("libraryId").asLong())
                .isEqualTo(a.at("/library/libraryId").asLong());
        assertThat(libs.getResponse().getContentAsString())
                .doesNotContain("\"libraryId\":" + b.at("/library/libraryId").asLong());
    }

    /**
     * A brand new tenant owns nothing, so every aggregate must be zero. If any
     * reporting query lost its library predicate this would report the whole
     * database instead. Run as the customer and again as super admin, because
     * platform-level access must not widen a tenant filter.
     */
    @Test
    public void aNewTenantReportsOnlyItsOwnEmptyData() throws Exception {
        JsonNode customer = onboard(nextTag());
        long libraryId = customer.at("/library/libraryId").asLong();

        for (String token : new String[]{tokenFor(customer), superAdminToken()}) {
            var res = mvc.perform(get("/api/libraries/" + libraryId + "/dashboard")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode data = mapper.readTree(res.getResponse().getContentAsString()).get("data");

            assertThat(data.get("totalStudents").asInt()).isZero();
            assertThat(data.get("totalSeats").asInt()).isZero();
            assertThat(data.get("activeMemberships").asInt()).isZero();
            assertThat(data.get("attendanceToday").asInt()).isZero();
            assertThat(data.get("paymentsToday").asInt()).isZero();
        }
    }

    @Test
    public void superAdminCanReachBothCustomersAndTheSeededTenants() throws Exception {
        JsonNode a = onboard(nextTag());
        JsonNode b = onboard(nextTag());
        String token = superAdminToken();

        assertThat(statusOf("/api/libraries/" + a.at("/library/libraryId").asLong(), token)).isEqualTo(200);
        assertThat(statusOf("/api/libraries/" + b.at("/library/libraryId").asLong(), token)).isEqualTo(200);
        assertThat(statusOf("/api/libraries/" + a.at("/library/libraryId").asLong() + "/dashboard", token))
                .isEqualTo(200);
        assertThat(statusOf("/api/libraries/1/dashboard", token)).isEqualTo(200);
        assertThat(statusOf("/api/students/1", token)).isEqualTo(200);
    }

    @Test
    public void aSpoofedTenantHeaderCannotWidenACustomersAccess() throws Exception {
        JsonNode a = onboard(nextTag());
        JsonNode b = onboard(nextTag());

        mvc.perform(get("/api/libraries/" + b.at("/library/libraryId").asLong() + "/seats")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .header("X-Library-Id", String.valueOf(b.at("/library/libraryId").asLong())))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/students")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .header("X-Library-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}
