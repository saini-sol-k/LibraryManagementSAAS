# Exception Handling Analysis: Student GET API (/api/students/{studentId})


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

**STATUS (corrected 2026-09-02): the `ResourceNotFoundException` -> 404 mapping for a
missing student was already correct, but TWO OTHER DEFECTS WERE FOUND AND FIXED.**

The student GET endpoint returns HTTP 404 with error code `STUDENT_NOT_FOUND` when a
student does not exist. All 104 tests pass after the fix (88 at the time this was
originally written).

**ROOT CAUSE OF REPORTED INTERNAL_ERROR (corrected):**

1. **`GlobalExceptionHandler.handleAll(Exception.class)` swallowed Spring MVC's own
   client-error exceptions.** Any URL Spring resolved as not-found returned
   500 `INTERNAL_ERROR` instead of 404; a wrong HTTP method returned 500 instead of 405;
   a wrong content type returned 500 instead of 415. Fixed by `handleUnsupportedRequest`.
2. **`localhost:8080` is bound by a different application** (`aklibrary_app`, image
   `librarymanagementsystem-app`), whose responses may have been mistaken for this
   backend's. The reported message `"Internal server error"` appears nowhere in this
   codebase.

Separately, `StudentServiceImpl.getStudent` caught `Exception` around the membership
check and turned database failures into 403 instead of 500. Also fixed.

~~The INTERNAL_ERROR you experienced was likely caused by an expired JWT, a missing
X-Library-Id header, a user lacking STUDENT_VIEW, or a database issue.~~ **All of these
were tested live and none of them produces INTERNAL_ERROR:** an invalid JWT yields
401 `UNAUTHORIZED`, a missing header falls back to the user's primary library, and a
missing permission yields 403 `FORBIDDEN`.
---

## Architecture Analysis

### 1. Exception Hierarchy
```
Exception
├── BusinessException (parent for app exceptions)
│   ├── ResourceNotFoundException (HTTP 404) ✓ CORRECT
│   ├── ForbiddenException (HTTP 403) ✓ CORRECT
│   ├── ConflictException (HTTP 409) ✓ CORRECT
│   └── UnauthorizedException (HTTP 401) ✓ CORRECT
└── RuntimeException
    └── (Various Spring/Database exceptions -> HTTP 500)
```

### 2. Exception Flow for GET /api/students/{id}

**HAPPY PATH (Student Exists):**
```
StudentController.get(id, libraryId)
    ↓
StudentService.getStudent(id, libraryId)
    ↓
1. Validate authentication (getCurrentUserId)
2. Resolve library ID (TenantContext or parameter)
3. studentRepository.findById(id)
    ↓ returns Optional<Student>
4. .orElseThrow(() -> ResourceNotFoundException(...))
    ↓ Student found, no exception
5. Validate user has access to student's library
6. Return StudentResponse ✓
    ↓
GlobalExceptionHandler: NOT INVOKED
    ↓
HTTP 200 OK ✓
```

**ERROR PATH (Student Does NOT Exist):**
```
StudentController.get(id, libraryId)
    ↓
StudentService.getStudent(id, libraryId)
    ↓
1. Validate authentication (getCurrentUserId) ✓
2. Resolve library ID ✓
3. studentRepository.findById(id)
    ↓ returns Optional.empty()
4. .orElseThrow(() -> ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"))
    ↓ EXCEPTION THROWN HERE
5. Validation never reached
    ↓
GlobalExceptionHandler
    ↓
GlobalExceptionHandler.handleNotFound(ResourceNotFoundException)
    ↓
Return ApiResponse {
    success: false,
    message: "Student not found",
    data: null,
    errorCode: "STUDENT_NOT_FOUND"
}
    ↓
HTTP 404 NOT FOUND ✓
```

**AUTHORIZATION FAILURE PATH (User not authorized):**
```
StudentController.get(id, libraryId)
    ↓
StudentService.getStudent(id, libraryId)
    ↓
1. tenantAuthorizationService.getCurrentUserId() ✓
2. (if null) throw ForbiddenException ✓
    OR
   tenantAuthorizationService.requireLibraryAccess(userId, libraryId) 
    ↓ throws ForbiddenException if user not member
    ↓
GlobalExceptionHandler.handleForbidden(ForbiddenException)
    ↓
Return ApiResponse {
    success: false,
    message: "You do not have permission...",
    data: null,
    errorCode: "FORBIDDEN" or "RESOURCE_ACCESS_DENIED"
}
    ↓
HTTP 403 FORBIDDEN ✓
```

---

## Files Involved

### Core Implementation
1. **StudentController** 
   - File: `src/main/java/com/librarysaas/student/controller/StudentController.java`
   - Method: `get(@PathVariable Long id, @RequestParam(required = false) Long libraryIdParam)`
   - Line 47-53
   - ✓ Correctly passes to service

2. **StudentService (Interface)**
   - File: `src/main/java/com/librarysaas/student/service/StudentService.java`
   - Method signature for `getStudent(Long id, Long libraryId)`

3. **StudentServiceImpl (Implementation)**
   - File: `src/main/java/com/librarysaas/student/service/impl/StudentServiceImpl.java`
   - Method: `getStudent(Long id, Long libraryId)` (Lines 89-137)
   - **KEY LINE 111-112:**
     ```java
     Student s = studentRepository.findById(id)
             .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));
     ```
   - ✓ Correctly throws ResourceNotFoundException

4. **ResourceNotFoundException (Custom Exception)**
   - File: `src/main/java/com/librarysaas/common/exception/ResourceNotFoundException.java`
   - ✓ Extends RuntimeException
   - ✓ Has errorCode field
   - ✓ Constructors correct

5. **GlobalExceptionHandler (@ControllerAdvice)**
   - File: `src/main/java/com/librarysaas/common/exception/GlobalExceptionHandler.java`
   - **KEY HANDLER (Lines 118-135):**
     ```java
     @ExceptionHandler(ResourceNotFoundException.class)
     public ResponseEntity<ApiResponse<Void>> handleNotFound(
             ResourceNotFoundException ex, HttpServletRequest request) {
         String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "NOT_FOUND";
         log.debug("Resource not found on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
         
         ApiResponse<Void> resp = new ApiResponse<>(
             false,
             ex.getMessage(),
             null,
             errorCode
         );
         
         return ResponseEntity
             .status(HttpStatus.NOT_FOUND)  // HTTP 404 ✓
             .body(resp);
     }
     ```
   - ✓ Correctly catches ResourceNotFoundException
   - ✓ Returns HTTP 404
   - ✓ Returns proper error response

6. **ApiResponse (Response DTO)**
   - File: `src/main/java/com/librarysaas/common/response/ApiResponse.java`
   - ✓ Correct structure with success, message, data, errorCode

---

## Test Results

### All Tests Pass: 104/104 SUCCESS (88/88 at the time of the original investigation)

#### New Tests Added:
- `StudentControllerNotFoundTest.testGetNonExistentStudent_ShouldReturn404()` ✓ PASS
- `StudentControllerNotFoundTest.testGetNonExistentStudent_ShouldNotReturn500()` ✓ PASS

#### Existing Tests Still Pass:
- StudentControllerTest: 3/3 ✓
- StudentServiceSecurityTest: 4/4 ✓
- StudentTenantIntegrationTest: 5/5 ✓
- All other tests: 74/74 ✓

---

## Expected API Responses

### Case 1: Non-Existent Student (ID 1001111)
**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1001111' \
  --header 'Authorization: Bearer <valid-jwt>' \
  --header 'X-Library-Id: 1' \
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

### Case 2: Existing Student (ID 1)
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
        ...
    },
    "errorCode": null
}
```

### Case 3: Wrong Library (User not member)
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

### Case 4: Invalid/Expired JWT
**Request:**
```bash
curl --location 'http://localhost:8080/api/students/1' \
  --header 'Authorization: Bearer invalid-token' \
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
Written by `JwtAuthenticationEntryPoint`. Verified live.

---

## Security Validation

### ✓ Multi-Tenant/Library Isolation Maintained
1. User can only access students in libraries they're members of
2. X-Library-Id header is NOT trusted; server validates membership
3. TenantContext validation prevents header spoofing
4. Both authorization failures (403) and not-found (404) are properly distinguished

### ✓ Authorization Flow
1. PreAuthorize checks for STUDENT_VIEW authority
2. TenantAuthorizationService validates library membership
3. Student's library is compared with user's authorized libraries
4. No information leakage about student existence in unauthorized libraries

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
