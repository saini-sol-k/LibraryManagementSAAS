package com.librarysaas.student;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2G student profile completion: documents and emergency contacts,
 * exercised end to end against the seeded Testcontainers MySQL with real JWTs.
 *
 * Seed data these tests rely on (V1__initial_schema.sql):
 *   students 1, 2, 3 -> library 1 ; student 4 -> library 2 ; student 5 -> library 3
 *   student_document          1 AADHAAR (student 1), 2 AADHAAR (student 2), 3 PAN (student 3)
 *   student_emergency_contact 1 Ramesh (student 1, address 4), 2 Sunita (student 2, address 5),
 *                             3 Mahesh (student 3, address 4), 4 Raj (student 4),
 *                             5 Vijay (student 5)
 *
 *   Address 4 is shared: it is the home address of students 1 and 3 through
 *   student_address, and the address of emergency contacts 1 and 3. Several
 *   tests below depend on that sharing, because it is what makes editing an
 *   address row in place unsafe.
 *
 *   user 1 superadmin -> libraries 1, 2, 3
 *   user 2 owner1     -> libraries 1, 2
 *   user 3 manager1   -> library 1 only, the cross-tenant subject
 */
public class StudentProfileIntegrationTest extends com.librarysaas.IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static final AtomicInteger SEQ = new AtomicInteger(4000);

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

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).at("/data");
    }

    private String documentBody(String type, String number, String url) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (type != null) body.put("documentType", type);
        if (number != null) body.put("documentNumber", number);
        if (url != null) body.put("documentUrl", url);
        return mapper.writeValueAsString(body);
    }

    private Map<String, Object> addressMap(String line1, String city, String state, String postal) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("addressLine1", line1);
        address.put("city", city);
        address.put("state", state);
        address.put("postalCode", postal);
        return address;
    }

    private String contactBody(String firstName, String relationship, Boolean isPrimary,
                               Map<String, Object> address) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (firstName != null) body.put("firstName", firstName);
        if (relationship != null) body.put("relationship", relationship);
        if (isPrimary != null) body.put("isPrimary", isPrimary);
        if (address != null) body.put("address", address);
        return mapper.writeValueAsString(body);
    }

    /* ========================================================== documents */

    @Test
    public void listsAStudentsDocuments() throws Exception {
        var res = mvc.perform(get("/api/students/1/documents")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode documents = data(res.getResponse().getContentAsString());
        assertThat(documents.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode document : documents) {
            assertThat(document.get("studentId").asLong()).isEqualTo(1L);
            assertThat(document.get("status").asText()).isEqualTo("ACTIVE");
        }
    }

    /** Who filed a document is internal audit data the UI has no use for. */
    @Test
    public void documentsNeverExposeInternalAuditColumns() throws Exception {
        var res = mvc.perform(get("/api/students/1/documents")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).doesNotContain("createdBy");
        assertThat(body).doesNotContain("updatedBy");
        assertThat(body).doesNotContain("passwordHash");
    }

    @Test
    public void getsOneDocumentAndRejectsAnUnknownOne() throws Exception {
        String token = ownerToken();

        mvc.perform(get("/api/student-documents/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(1))
                .andExpect(jsonPath("$.data.studentId").value(1))
                .andExpect(jsonPath("$.data.documentType").value("AADHAAR"));

        mvc.perform(get("/api/student-documents/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_DOCUMENT_NOT_FOUND"));
    }

    @Test
    public void documentsForAnUnknownStudentIs404() throws Exception {
        mvc.perform(get("/api/students/999999/documents")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    @Test
    public void recordsAndUpdatesADocument() throws Exception {
        String token = ownerToken();
        String number = "DOC" + SEQ.incrementAndGet();

        var created = mvc.perform(post("/api/students/3/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody("passport", number, "students/3/passport.pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentId").value(3))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // The type is normalised the way every other module normalises one.
                .andExpect(jsonPath("$.data.documentType").value("PASSPORT"))
                .andExpect(jsonPath("$.data.documentUrl").value("students/3/passport.pdf"))
                .andReturn();

        long id = data(created.getResponse().getContentAsString()).get("documentId").asLong();

        mvc.perform(put("/api/student-documents/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody("PASSPORT", number, "students/3/passport-v2.pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentUrl").value("students/3/passport-v2.pdf"))
                // The document stays on the file it was recorded against.
                .andExpect(jsonPath("$.data.studentId").value(3));
    }

    /** The schema places no uniqueness on type or number, so none is invented. */
    @Test
    public void aStudentMayHoldSeveralDocumentsOfTheSameType() throws Exception {
        String token = ownerToken();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/students/2/documents")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(documentBody("AADHAAR", "DUP" + SEQ.incrementAndGet(), null)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    public void documentRequiresAType() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/students/1/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.documentType").exists());

        mvc.perform(post("/api/students/1/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody("AADHAAR", null, "x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.documentUrl").exists());
    }

    /* ================================================== emergency contacts */

    @Test
    public void listsAStudentsEmergencyContactsPrimaryFirst() throws Exception {
        var res = mvc.perform(get("/api/students/1/emergency-contacts")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode contacts = data(res.getResponse().getContentAsString());
        assertThat(contacts.size()).isGreaterThanOrEqualTo(1);
        assertThat(contacts.get(0).get("isPrimary").asBoolean()).isTrue();

        JsonNode first = contacts.get(0);
        assertThat(first.get("studentId").asLong()).isEqualTo(1L);
        assertThat(first.get("firstName").asText()).isEqualTo("Ramesh");
        assertThat(first.get("relationship").asText()).isEqualTo("FATHER");
        // The address is inlined, and its id is deliberately never published.
        assertThat(first.get("address").isNull()).isFalse();
        assertThat(first.get("address").has("addressId")).isFalse();
        assertThat(first.get("address").get("city").asText()).isNotBlank();
    }

    @Test
    public void getsOneContactAndRejectsAnUnknownOne() throws Exception {
        String token = ownerToken();

        mvc.perform(get("/api/student-emergency-contacts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emergencyContactId").value(1))
                .andExpect(jsonPath("$.data.studentId").value(1));

        mvc.perform(get("/api/student-emergency-contacts/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EMERGENCY_CONTACT_NOT_FOUND"));
    }

    @Test
    public void addsAContactWithAnInlineAddress() throws Exception {
        String token = ownerToken();

        var created = mvc.perform(post("/api/students/2/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Anita", "SISTER", false,
                                addressMap("12 Park Road", "Dehradun", "Uttarakhand", "248001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentId").value(2))
                .andExpect(jsonPath("$.data.firstName").value("Anita"))
                .andExpect(jsonPath("$.data.isPrimary").value(false))
                .andExpect(jsonPath("$.data.address.addressLine1").value("12 Park Road"))
                .andExpect(jsonPath("$.data.address.city").value("Dehradun"))
                .andReturn();

        assertThat(data(created.getResponse().getContentAsString())
                .get("emergencyContactId").asLong()).isPositive();
    }

    @Test
    public void aContactMayBeAddedWithoutAnAddress() throws Exception {
        mvc.perform(post("/api/students/2/emergency-contacts")
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Deepak", "UNCLE", false, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    @Test
    public void contactRequiresAFirstNameAndAValidAddressWhenGiven() throws Exception {
        String token = ownerToken();

        mvc.perform(post("/api/students/1/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.firstName").exists());

        // An inline address is validated as thoroughly as a Phase 2A address.
        Map<String, Object> badAddress = addressMap(null, null, null, "!!");
        mvc.perform(post("/api/students/1/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Test", "FATHER", false, badAddress)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/students/1/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "firstName", "Test", "mobile", "not-a-number"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.mobile").exists());
    }

    /**
     * The address table is global, so an id supplied by a caller must have no
     * effect whatsoever. There is no addressId field to bind, and this proves a
     * request carrying one still gets a freshly created address.
     */
    @Test
    public void anArbitraryAddressIdCannotBeInjected() throws Exception {
        String token = superAdminToken();

        Map<String, Object> address = addressMap("9 New Street", "Meerut", "Uttar Pradesh", "250001");
        // Address 4 belongs to other students; address 6 to a library-3 student.
        address.put("addressId", 4);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Injected");
        body.put("isPrimary", false);
        body.put("address", address);
        body.put("addressId", 6);

        var created = mvc.perform(post("/api/students/1/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                // The address that came back is the one supplied inline, not
                // whatever address 4 or 6 happens to hold.
                .andExpect(jsonPath("$.data.address.addressLine1").value("9 New Street"))
                .andExpect(jsonPath("$.data.address.city").value("Meerut"))
                .andReturn();

        assertThat(created.getResponse().getContentAsString()).doesNotContain("addressId");

        // The seeded contact that genuinely uses address 4 is untouched.
        mvc.perform(get("/api/student-emergency-contacts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Ramesh"));
    }

    /**
     * Address rows are shared. Address 4 is the home address of students 1 and 3
     * and the address of emergency contacts 1 and 3, so editing a contact must
     * not rewrite it in place.
     */
    @Test
    public void editingAContactAddressDoesNotDisturbOtherHoldersOfThatAddress() throws Exception {
        String token = ownerToken();

        var before = mvc.perform(get("/api/student-emergency-contacts/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode otherHolderAddress = data(before.getResponse().getContentAsString()).get("address");
        String otherLine1 = otherHolderAddress.get("addressLine1").asText();

        // Contact 1 shares address 4 with contact 3. Change contact 1's address.
        mvc.perform(put("/api/student-emergency-contacts/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Ramesh", "FATHER", true,
                                addressMap("77 Changed Lane", "Saharanpur", "Uttar Pradesh", "247001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address.addressLine1").value("77 Changed Lane"));

        // Contact 3 still sees the address it always had.
        mvc.perform(get("/api/student-emergency-contacts/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address.addressLine1").value(otherLine1));

        // And so does student 3's home address, which uses the same row.
        var home = mvc.perform(get("/api/students/3/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        boolean intact = false;
        for (JsonNode address : data(home.getResponse().getContentAsString())) {
            if (otherLine1.equals(address.get("addressLine1").asText())) {
                intact = true;
            }
        }
        assertThat(intact)
                .as("a student's home address must survive an edit to an emergency contact")
                .isTrue();
    }

    /** Omitting the address on update means unchanged, never cleared. */
    @Test
    public void omittingTheAddressOnUpdateLeavesItInPlace() throws Exception {
        String token = ownerToken();

        var created = mvc.perform(post("/api/students/2/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Keep", "COUSIN", false,
                                addressMap("5 Keep Road", "Dehradun", "Uttarakhand", "248002"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = data(created.getResponse().getContentAsString()).get("emergencyContactId").asLong();

        mvc.perform(put("/api/student-emergency-contacts/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Kept", "COUSIN", false, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Kept"))
                .andExpect(jsonPath("$.data.address.addressLine1").value("5 Keep Road"));
    }

    @Test
    public void promotingAContactDemotesThePreviousPrimary() throws Exception {
        String token = ownerToken();

        var created = mvc.perform(post("/api/students/3/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("NewPrimary", "MOTHER", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isPrimary").value(true))
                .andReturn();
        long newId = data(created.getResponse().getContentAsString())
                .get("emergencyContactId").asLong();

        // Exactly one primary remains, and it is the new contact.
        var list = mvc.perform(get("/api/students/3/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long primaries = 0;
        for (JsonNode contact : data(list.getResponse().getContentAsString())) {
            if (contact.get("isPrimary").asBoolean()) {
                primaries++;
                assertThat(contact.get("emergencyContactId").asLong()).isEqualTo(newId);
            }
        }
        assertThat(primaries).as("a student has at most one primary contact").isEqualTo(1L);

        // Promoting the original back demotes the new one in turn.
        mvc.perform(put("/api/student-emergency-contacts/3")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Mahesh", "FATHER", true, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(true));

        mvc.perform(get("/api/student-emergency-contacts/" + newId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(false));
    }

    @Test
    public void deletingAContactKeepsTheAddressRow() throws Exception {
        String token = ownerToken();

        var created = mvc.perform(post("/api/students/2/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Temporary", "FRIEND", false,
                                addressMap("1 Temp Way", "Dehradun", "Uttarakhand", "248003"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = data(created.getResponse().getContentAsString()).get("emergencyContactId").asLong();

        mvc.perform(delete("/api/student-emergency-contacts/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/student-emergency-contacts/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EMERGENCY_CONTACT_NOT_FOUND"));

        // Student 2's own addresses are unaffected by the removal.
        mvc.perform(get("/api/students/2/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    public void deletingAnUnknownContactIs404() throws Exception {
        mvc.perform(delete("/api/student-emergency-contacts/999999")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EMERGENCY_CONTACT_NOT_FOUND"));
    }

    /* ====================================================== tenant boundary */

    /**
     * Neither table carries a library column, so the tenant is reached through
     * the student. A caller outside that student's library must be refused for
     * every operation, and the refusal must come from the stored relationship
     * rather than from anything the request supplied.
     *
     * manager1 belongs to library 1 only; student 4 is in library 2 and student
     * 5 in library 3.
     */
    @Test
    public void profileDataOfAStudentInAnotherLibraryIsUnreachable() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/students/4/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(get("/api/students/5/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Document 4 and contact 4 belong to student 4, in library 2.
        mvc.perform(post("/api/students/4/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody("AADHAAR", "XT" + SEQ.incrementAndGet(), null)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/students/4/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Intruder", "FATHER", false, null)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/student-emergency-contacts/4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/student-emergency-contacts/4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Nothing was written or removed: the contact is still there.
        mvc.perform(get("/api/student-emergency-contacts/4")
                        .header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Raj"));
    }

    /**
     * A super admin reaches every library legitimately, so the boundary that
     * matters for them is a different one: the library must still be read from
     * the student's own stored row, and no caller-supplied value may stand in
     * for it. These calls succeed for the super admin and are refused for a
     * library-scoped caller, which is only possible if the resolution is
     * row-based rather than privilege-based.
     */
    @Test
    public void theOwningLibraryIsAlwaysReadFromTheStoredRow() throws Exception {
        String admin = superAdminToken();
        String manager = managerToken();

        // Document 3 belongs to student 3 in library 1: both callers may read it.
        mvc.perform(get("/api/student-documents/3").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/student-documents/3").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());

        // Contact 5 belongs to student 5 in library 3: only the super admin may.
        mvc.perform(get("/api/student-emergency-contacts/5")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(5));
        mvc.perform(get("/api/student-emergency-contacts/5")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());

        // A super admin still cannot invent a student that does not exist.
        mvc.perform(post("/api/students/999999/documents")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody("AADHAAR", "SA" + SEQ.incrementAndGet(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
    }

    /* ========================================================= authorisation */

    @Test
    public void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/students/1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        mvc.perform(get("/api/students/1/emergency-contacts"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/students/1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentBody("AADHAAR", "ANON", null)))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/student-emergency-contacts/1"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * An unreachable student and a missing one must not be distinguishable by a
     * different error shape.
     */
    @Test
    public void unreachableAndMissingStudentsStayNonInformative() throws Exception {
        String token = managerToken();

        mvc.perform(get("/api/students/4/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/students/999999/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /* ================================================= error-shape guarantee */

    /**
     * Every expected failure has to arrive as its own business error. A 500 with
     * INTERNAL_ERROR here would mean a rule is throwing something the handler
     * does not recognise.
     */
    @Test
    public void noExpectedErrorEverBecomesAnInternalError() throws Exception {
        String bearer = "Bearer " + ownerToken();
        var responses = new java.util.ArrayList<String>();

        responses.add(mvc.perform(get("/api/students/999999/documents").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/student-documents/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/students/999999/emergency-contacts").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/student-emergency-contacts/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/students/1/documents").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/students/1/documents").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/students/1/emergency-contacts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(post("/api/students/1/emergency-contacts").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("Bad", "FATHER", false,
                                addressMap(null, null, null, "!!"))))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(put("/api/student-documents/999999").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(documentBody("AADHAAR", null, null)))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(delete("/api/student-emergency-contacts/999999").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString());
        responses.add(mvc.perform(get("/api/students/4/documents").header("Authorization", "Bearer " + managerToken()))
                .andReturn().getResponse().getContentAsString());

        for (String body : responses) {
            assertThat(body).doesNotContain("INTERNAL_ERROR");
            assertThat(body).doesNotContain("Unable to process the request");
        }
    }
}
