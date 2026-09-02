# Student GET API Exception Handling - Investigation Summary


> ## ⚠️ SUPERSEDED — corrected 2026-09-02
>
> **The conclusion below ("architecture is correct, no changes needed") was wrong.**
> It was reached from code reading and mock-based tests, without reproducing the request
> against a running server. Two real defects were subsequently found and fixed:
>
> 1. `GlobalExceptionHandler.handleAll(Exception.class)` swallowed Spring MVC's own
>    client-error exceptions, so **any URL Spring resolved as not-found returned
>    500 `INTERNAL_ERROR` instead of 404** (likewise 405 and 415).
> 2. `StudentServiceImpl.getStudent` caught `Exception` around the membership check and
>    turned database failures into **403 instead of 500**.
>
> The listed causes of `INTERNAL_ERROR` below (expired JWT, missing `X-Library-Id`,
> missing `STUDENT_VIEW`) are **all incorrect** — verified live, those produce 401, a
> primary-library fallback, and 403 respectively.
>
> Also incorrect below: the wrong-library case is documented as returning
> `RESOURCE_ACCESS_DENIED`. It actually returns `FORBIDDEN`, because
> `requireLibraryAccess` fails first and the `RESOURCE_ACCESS_DENIED` branch is not
> reached on that path.
>
> **See `EXECUTIVE_SUMMARY.md` for the corrected root cause, the fix, and the verified
> live behaviour.** The per-file architecture notes below remain accurate and are kept
> for reference; the test counts (88) are superseded by 104.

---

## Executive Summary

**FINDING (corrected 2026-09-02): the 404 mapping for a missing student was already
correct, but TWO OTHER DEFECTS WERE FOUND AND FIXED.**

The API returns HTTP 404 with error code `STUDENT_NOT_FOUND` when a student does not
exist — verified live against the real dev database. However, `INTERNAL_ERROR` was
genuinely being returned for other not-found requests. See the corrected root cause
below and `EXECUTIVE_SUMMARY.md`.

---

## Root Cause Analysis

### What Was Reported
```
GET /api/students/1001111
Authorization: Bearer <token>
X-Library-Id: 1

Response (HTTP 500):
{
    "success": false,
    "message": "Internal server error",
    "data": null,
    "errorCode": "INTERNAL_ERROR"
}
```

### Actual Root Cause (corrected 2026-09-02)

Two real defects, both verified by calling a running server rather than by reading code:

1. **`GlobalExceptionHandler.handleAll(Exception.class)` swallowed Spring MVC's own
   client-error exceptions**, which carry their own HTTP status. Before the fix:
   `GET /api/students/1/none` -> 500 `INTERNAL_ERROR` (should be 404);
   `POST /api/students/1` -> 500 (should be 405); wrong `Content-Type` -> 500
   (should be 415). Fixed by adding `handleUnsupportedRequest`.
2. **`StudentServiceImpl.getStudent` caught `Exception`** around the library-membership
   check and converted everything into `ForbiddenException`, so a database failure
   returned 403 instead of 500. Fixed by narrowing the catch to `AccessDeniedException`.

Also relevant: `localhost:8080` is bound by a **different** application
(`aklibrary_app`, image `librarymanagementsystem-app`, database `library`). The reported
message `"Internal server error"` appears nowhere in this codebase.

~~The exception likely occurred in the authentication layer, the authorization layer,
tenant resolution, or the database.~~ **None of those produces `INTERNAL_ERROR`** —
tested live, they yield 401, 403, and a primary-library fallback respectively.

The `/api/students/{id}` "student not found" path itself was already correct and is
unchanged.

---

## Files/Classes Changed

### Files ADDED (for testing):
1. ✅ `src/test/java/com/librarysaas/student/StudentControllerNotFoundTest.java` - **NEW**
   - Contains 2 new unit tests for 404 scenarios
   - Tests verify ResourceNotFoundException is properly caught

### Files VERIFIED at the time (see correction above: two of these DID need changes):
1. ✅ `src/main/java/com/librarysaas/student/controller/StudentController.java` (Line 47-53)
   - GET endpoint correctly passes to service
   
2. ✅ `src/main/java/com/librarysaas/student/service/impl/StudentServiceImpl.java` (Line 89-137)
   - getStudent() correctly throws ResourceNotFoundException when student not found
   
3. ✅ `src/main/java/com/librarysaas/common/exception/ResourceNotFoundException.java`
   - Correct exception class with errorCode field
   
4. ✅ `src/main/java/com/librarysaas/common/exception/GlobalExceptionHandler.java` (Line 118-135)
   - handleNotFound() correctly catches ResourceNotFoundException and returns HTTP 404
   
5. ✅ `src/main/java/com/librarysaas/common/response/ApiResponse.java`
   - Correct response structure with success, message, data, errorCode

---

## Exception Flow Analysis

### BEFORE (Reported Issue - NOT the student not found path):
```
API Request
  ↓
StudentController.get(id, libraryId)
  ↓
[EXCEPTION OCCURS HERE - NOT in student lookup]
  ↓
GlobalExceptionHandler.handleAll(Exception)  ← Catch-all handler
  ↓
HTTP 500 INTERNAL_ERROR  ← What you received
```

**The exception occurred BEFORE reaching the student lookup logic.**

### CORRECT PATH (Student Not Found - Already Working):
```
API Request (with valid auth, correct library)
  ↓
StudentController.get(1001111, 1)
  ↓
StudentServiceImpl.getStudent(1001111, 1)
  ├─ ✓ validateAuthentication()
  ├─ ✓ resolveLibraryId()
  ├─ ✓ checkLibraryAccess()
  └─ studentRepository.findById(1001111)
     ↓
     Optional.empty()
     ↓
     .orElseThrow(() -> ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"))
     ↓
     EXCEPTION THROWN
  ↓
GlobalExceptionHandler.handleNotFound(ResourceNotFoundException)
  ↓
HTTP 404 NOT FOUND with:
{
    "success": false,
    "message": "Student not found",
    "data": null,
    "errorCode": "STUDENT_NOT_FOUND"
}  ← CORRECT RESPONSE
```

---

## Tests Added/Updated

### NEW TESTS ADDED:
File: `src/test/java/com/librarysaas/student/StudentControllerNotFoundTest.java`

```java
@Test
public void testGetNonExistentStudent_ShouldReturn404() throws Exception {
    // Arrange: Service throws ResourceNotFoundException
    when(studentService.getStudent(anyLong(), anyLong()))
            .thenThrow(new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

    // Act & Assert: Should return HTTP 404 with proper error response
    mvc.perform(get("/api/students/1001111")
            .header("X-Library-Id", "1")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())  // HTTP 404 ✓
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Student not found"))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
}

@Test
public void testGetNonExistentStudent_ShouldNotReturn500() throws Exception {
    // Arrange: Service throws ResourceNotFoundException
    when(studentService.getStudent(anyLong(), anyLong()))
            .thenThrow(new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

    // Act & Assert: Should return 404, NOT 500 INTERNAL_ERROR
    mvc.perform(get("/api/students/9999999")
            .header("X-Library-Id", "1")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_FOUND"));
}
```

### EXISTING TESTS - All Still Pass:
- ✅ StudentControllerTest: 3/3 tests pass
- ✅ StudentServiceSecurityTest: 4/4 tests pass (including authorization tests)
- ✅ StudentTenantIntegrationTest: 5/5 tests pass

### SECURITY TESTS - Verified Still Pass:
- ✅ userWithPermissionAndMembershipCanViewStudent() - HTTP 200
- ✅ userWithoutPermissionIsDenied() - HTTP 403 AccessDeniedException
- ✅ userInWrongLibraryIsDenied() - HTTP 403 ForbiddenException
- ✅ headerSpoofingCannotBypassMembership() - HTTP 403 ForbiddenException

---

## Maven Test Results

### Full Test Suite Run:
```
[INFO] Tests run: 104, Failures: 0, Errors: 0, Skipped: 0   # was 88 before the fix
[INFO] BUILD SUCCESS
```

### Breakdown:
- StudentControllerNotFoundTest: 5/5 (2 original + 3 added by the fix)
- StudentControllerTest: 3/3 ✅
- StudentServiceSecurityTest: 4/4 ✅
- StudentTenantIntegrationTest: 5/5 ✅
- Other tests: 74/74 ✅

---

## Expected API Responses

### Test Case 1: Non-Existent Student (Should Return 404)

**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1001111' \
  --header 'Authorization: Bearer <REDACTED_JWT>' \
  --header 'X-Library-Id: 1' \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json'
```

**Expected Response (HTTP 404):**
```json
{
    "success": false,
    "message": "Student not found",
    "data": null,
    "errorCode": "STUDENT_NOT_FOUND"
}
```

**Status Code:** 404 NOT FOUND ✓

---

### Test Case 2: Existing Student (Should Return 200)

**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1' \
  --header 'Authorization: Bearer <valid-jwt>' \
  --header 'X-Library-Id: 1' \
  --header 'Accept: application/json'
```

**Expected Response (HTTP 200):**
```json
{
    "success": true,
    "message": "Student retrieved",
    "data": {
        "id": 1,
        "libraryId": 1,
        "studentCode": "STU001",
        "firstName": "John",
        "lastName": "Doe",
        "email": "john@example.com",
        "mobile": "1234567890",
        "status": "ACTIVE",
        "createdAt": "2026-09-02T10:00:00"
    },
    "errorCode": null
}
```

**Status Code:** 200 OK ✓

---

### Test Case 3: Wrong Library Access (Should Return 403)

**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1' \
  --header 'Authorization: Bearer <valid-jwt>' \
  --header 'X-Library-Id: 999' \
  --header 'Accept: application/json'
```

**Expected Response (HTTP 403):**
```json
{
    "success": false,
    "message": "You do not have permission to perform this operation",
    "data": null,
    "errorCode": "FORBIDDEN"
}
```

> Corrected 2026-09-02: this case returns `FORBIDDEN`, not `RESOURCE_ACCESS_DENIED`.
> `requireLibraryAccess` fails first on the student's library, so the
> `RESOURCE_ACCESS_DENIED` branch is not reached on this path. Verified live.

**Status Code:** 403 FORBIDDEN ✓

---

### Test Case 4: Missing STUDENT_VIEW Permission (Should Return 403)

**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1' \
  --header 'Authorization: Bearer <token-without-STUDENT_VIEW>' \
  --header 'X-Library-Id: 1' \
  --header 'Accept: application/json'
```

**Expected Response (HTTP 403):**
```json
{
    "success": false,
    "message": "You do not have permission to perform this operation",
    "data": null,
    "errorCode": "FORBIDDEN"
}
```

**Status Code:** 403 FORBIDDEN ✓

---

### Test Case 5: Invalid JWT Token (Returns 401)

**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1' \
  --header 'Authorization: Bearer invalid-or-expired-token' \
  --header 'X-Library-Id: 1'
```

**Expected Response (HTTP 401):**
```json
{
    "success": false,
    "message": "Authentication is required",
    "data": null,
    "errorCode": "UNAUTHORIZED"
}
```
Written by `JwtAuthenticationEntryPoint`.

**Status Code:** 401 UNAUTHORIZED (verified live)

---

## Why You Got INTERNAL_ERROR (corrected 2026-09-02)

The three scenarios previously listed here were **speculation and all of them are
wrong**. Each was tested live against a running server:

| Previously claimed cause | Actual observed result |
|---|---|
| Expired / invalid JWT | **401 `UNAUTHORIZED`** — `JwtAuthenticationFilter` swallows the parse failure, the request stays anonymous, and `JwtAuthenticationEntryPoint` writes the 401 envelope. Never 500. |
| Missing `X-Library-Id` header | **Works normally.** `JwtAuthenticationFilter` resolves the tenant from the user's server-side primary-library association, so the header is not required. Never 500. |
| Missing `STUDENT_VIEW` permission | **403 `FORBIDDEN`** from `@PreAuthorize` via `handleAccessDenied`. Never 500. |

### The two real causes

**1. Spring MVC's own client errors were flattened to 500.**
`GlobalExceptionHandler.handleAll(Exception.class)` caught `NoResourceFoundException`,
`HttpRequestMethodNotSupportedException` and the media-type exceptions, all of which
carry their own HTTP status. Observed before the fix:

```
GET  /api/students/1/none   -> 500 INTERNAL_ERROR   (should be 404)
POST /api/students/1        -> 500 INTERNAL_ERROR   (should be 405)
POST /api/students          -> 500 INTERNAL_ERROR   (should be 415, wrong Content-Type)
```

So a URL that did not exist really was reported as `INTERNAL_ERROR` — just not the
`/api/students/{id}` path, which mapped correctly all along.

**2. You may not have been calling this application.**
`localhost:8080` is bound by a different project — container `aklibrary_app`, image
`librarymanagementsystem-app`, database `library`, table `students` with ids
10001-10010. The reported body used the message `"Internal server error"`, which does
not appear anywhere in this codebase; this application's catch-all returns
`"Unable to process the request. Please try again later."`.

Both defects are fixed. See `EXECUTIVE_SUMMARY.md`.

---

## How to Verify the Fix Works

### Step 1: Deploy the application
```bash
cd C:\LIbrarySAAS\library-saas-backend
mvn clean package -DskipTests
java -jar target/library-saas-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=test
```

### Step 2: Run all tests
```bash
mvn clean test
# Should see: Tests run: 104, Failures: 0, Errors: 0
```

### Step 3: Test the API with proper headers
```bash
# First, get a valid JWT token from auth endpoint
# Then test with:

curl --location 'http://localhost:8080/api/students/999999' \
  --header 'Authorization: Bearer <valid-jwt>' \
  --header 'X-Library-Id: 1' \
  --header 'Accept: application/json'

# Should return HTTP 404 with STUDENT_NOT_FOUND error code
```

### Step 4: Verify the response
```
HTTP/1.1 404 Not Found
Content-Type: application/json

{
    "success": false,
    "message": "Student not found",
    "data": null,
    "errorCode": "STUDENT_NOT_FOUND"
}
```

---

## Security Guarantees Maintained

✅ Multi-tenant isolation preserved
- User can only access students in libraries they're members of
- X-Library-Id header is NOT trusted; server validates membership
- Header spoofing is prevented by TenantAuthorizationService

✅ Authorization preserved
- STUDENT_VIEW permission is required
- ForbiddenException (403) for unauthorized access
- ResourceNotFoundException (404) only for existing vs non-existing students

✅ No information leakage
- Cannot distinguish "student doesn't exist in my library" from "student doesn't exist globally"
- All non-existent students return same 404 response

---

## Summary Table

| Scenario | HTTP Status | Error Code | Message |
|----------|-------------|------------|---------|
| Non-existent student (valid auth) | 404 | STUDENT_NOT_FOUND | Student not found |
| Existing student (valid auth) | 200 | null | Student retrieved |
| Wrong library access | 403 | FORBIDDEN | You do not have permission to perform this operation |
| Missing permission | 403 | FORBIDDEN | You do not have permission... |
| Invalid or missing JWT | 401 | UNAUTHORIZED | Authentication is required |
| Header spoofing | 403 | FORBIDDEN | You do not have permission to perform this operation |
| Unmapped URL / no such resource | 404 | NOT_FOUND | The requested resource was not found |
| Wrong HTTP method | 405 | METHOD_NOT_ALLOWED | Method Not Allowed |
| Unsupported content type | 415 | UNSUPPORTED_MEDIA_TYPE | Unsupported Media Type |
| Unexpected error | 500 | INTERNAL_ERROR | Unable to process request |

---

## Conclusion (corrected 2026-09-02)

**The original conclusion — "exception handling is correct, no code changes were
necessary" — was wrong.** Two defects were found once the running API was exercised, and
both have been fixed:

1. **`GlobalExceptionHandler.handleAll(Exception.class)` swallowed Spring MVC's own
   client-error exceptions.** Any URL Spring resolved as not-found returned
   500 `INTERNAL_ERROR` instead of 404; a wrong HTTP method returned 500 instead of 405;
   a wrong content type returned 500 instead of 415. Fixed by adding
   `handleUnsupportedRequest`, which derives the status from Spring's `ErrorResponse`
   contract while keeping the existing `ApiResponse` envelope.

2. **`StudentServiceImpl.getStudent` caught `Exception` around the membership check**
   and converted everything — including database failures — into `ForbiddenException`,
   so an infrastructure outage returned 403 instead of 500. Fixed by narrowing the catch
   to `AccessDeniedException`.

The `ResourceNotFoundException` → 404 mapping for a missing student was, as documented
above, already correct: `GET /api/students/1001111` with a valid JWT returned
404 `STUDENT_NOT_FOUND` before the fix as well. That was verified against the real dev
database, not inferred.

The suggested causes of `INTERNAL_ERROR` in the original text (expired JWT, missing
`X-Library-Id`, missing `STUDENT_VIEW`) are all incorrect — verified live, they produce
401 `UNAUTHORIZED`, a primary-library fallback, and 403 `FORBIDDEN` respectively. Note
also that `localhost:8080` is bound by a **different** application (`aklibrary_app`,
image `librarymanagementsystem-app`), whose responses may have been mistaken for this
backend's.

Test suite after the fix: **104 tests, 0 failures** (was 88).

See `EXECUTIVE_SUMMARY.md` for the full corrected write-up, including the verified
before/after response table for every error path.
