# Executive Summary: Student GET API Exception Handling

**Status: FIXED** — 2026-09-02

> **This document was rewritten.** An earlier revision concluded "the architecture is
> correct, no changes needed" and blamed the reported `INTERNAL_ERROR` on an expired JWT
> or a missing header. That conclusion was wrong: it was reached by reading the code and
> running mock-based tests, without ever reproducing the request against a running
> server. Two real defects were found once the API was actually exercised. See
> [Correction](#correction-to-the-earlier-conclusion) at the end.

---

## 1. Root cause

Two defects, plus one environment finding.

### Finding: port 8080 may not be this application

A container `aklibrary_app` (image `librarymanagementsystem-app`, database `library`,
table `students`, ids 10001-10010) is bound to `localhost:8080`. It is a different
project. The reported response body used the message `"Internal server error"`, a string
that **does not exist anywhere in this codebase** — this application's catch-all handler
returns `"Unable to process the request. Please try again later."`. Confirm which server
you are calling before debugging this one.

This backend, run against the real dev database (`library_saas` on port 3307), already
returned the correct 404 for `GET /api/students/1001111` with a valid JWT and
`X-Library-Id: 1`. That specific path was never broken.

### Defect 1 — Spring MVC client errors were reported as INTERNAL_ERROR

`GlobalExceptionHandler.handleAll(Exception.class)` swallowed Spring MVC's own
client-error exceptions, which carry their own HTTP status. Verified live, before the
fix:

| Request | Before | Should be |
|---|---|---|
| `GET /api/students/1/none` (no such resource) | **500 `INTERNAL_ERROR`** | 404 |
| `POST /api/students/1` (wrong method) | **500 `INTERNAL_ERROR`** | 405 |
| `POST /api/students` with `Content-Type: text/plain` | **500 `INTERNAL_ERROR`** | 415 |

So "a resource that does not exist is reported as `INTERNAL_ERROR`" was genuinely true —
for every URL that Spring itself resolves as not-found.

### Defect 2 — system failures were masked as 403

`StudentServiceImpl.getStudent` wrapped the library-membership check in
`catch (Exception e)` and converted *anything* into `ForbiddenException`. A database
outage during the membership query returned **403 instead of 500**, hiding a real
infrastructure failure behind a permissions error.

---

## 2. Files changed

| File | Change |
|---|---|
| `src/main/java/com/librarysaas/common/exception/GlobalExceptionHandler.java` | Added `handleUnsupportedRequest` covering `NoResourceFoundException`, `NoHandlerFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, `HttpMediaTypeNotAcceptableException`. Status is derived from Spring's own `ErrorResponse` contract; the existing `ApiResponse` envelope is preserved. |
| `src/main/java/com/librarysaas/student/service/impl/StudentServiceImpl.java` | Narrowed `catch (Exception e)` to `catch (AccessDeniedException e)` in `getStudent`. |

No new exception types, no new error-code conventions, no `try/catch` in the controller.
The existing `ResourceNotFoundException` → 404 handler was already correct and is
untouched.

---

## 3. Exception flow: before vs after

**`GET /api/students/{id}`, student does not exist — unchanged, was already correct:**

```
StudentRepository.findById(id) -> Optional.empty()
    -> ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND")
    -> GlobalExceptionHandler.handleNotFound
    -> HTTP 404 {"success":false,"message":"Student not found","data":null,"errorCode":"STUDENT_NOT_FOUND"}
```

**Library-membership check:**

| Thrown | Before | After |
|---|---|---|
| `AccessDeniedException` | `ForbiddenException` -> 403 | unchanged, 403 |
| `DataAccessException` | `ForbiddenException` -> **403** | propagates -> `handleAll` -> **500 `INTERNAL_ERROR`** |

**Spring MVC client errors:**

| Case | Before | After |
|---|---|---|
| No handler / no resource | `handleAll` -> 500 `INTERNAL_ERROR` | 404 `NOT_FOUND` |
| Method not allowed | 500 `INTERNAL_ERROR` | 405 `METHOD_NOT_ALLOWED` |
| Unsupported / unacceptable media type | 500 `INTERNAL_ERROR` | 415 / 406 |

---

## 4. Tests

16 tests added, 104 total.

| Test class | Added | Covers |
|---|---|---|
| `GlobalExceptionHandlerTest` | +5 | no-resource -> 404, method -> 405, media type -> 415, `STUDENT_NOT_FOUND` keeps its code, `ForbiddenException` still 403 |
| `StudentControllerNotFoundTest` | +3 | unexpected `IllegalStateException` -> 500, `DataAccessResourceFailureException` -> 500, `ForbiddenException` -> 403 |
| `StudentServiceSecurityTest` | +2 | DB failure during membership check no longer masked as 403; missing student throws `STUDENT_NOT_FOUND` |
| `StudentTenantIntegrationTest` | +4 | real login/JWT on Testcontainers MySQL: the reported request -> 404, cross-tenant GET -> 403, header spoofing on GET -> 403, unmapped sub-path -> 404 |
| `ErrorHandlingIntegrationTest` | +2 | unmapped path -> 404, wrong method -> 405 |

```
Tests run: 104, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS  (02:58 min)
```

---

## 5. Verified live behaviour (after fix)

Run against the real dev database, real login, real JWT:

| Request | Status | Body |
|---|---|---|
| `GET /api/students/1` | 200 | `{"success":true,"message":"Student retrieved",...}` |
| `GET /api/students/1001111` | 404 | `{"success":false,"message":"Student not found","data":null,"errorCode":"STUDENT_NOT_FOUND"}` |
| `GET /api/students/4` (other library) | 403 | `errorCode: FORBIDDEN` |
| `GET /api/students/4` + `X-Library-Id: 2` (spoof) | 403 | `errorCode: FORBIDDEN` |
| `GET /api/students/abc` | 400 | `errorCode: BAD_REQUEST` |
| `GET /api/students/1001111` without token | 401 | `errorCode: UNAUTHORIZED` |
| `POST /api/students/1` | 405 | `errorCode: METHOD_NOT_ALLOWED` |
| `GET /api/students/1/none` | 404 | `errorCode: NOT_FOUND` |
| `POST /api/students` with `text/plain` | 415 | `errorCode: UNSUPPORTED_MEDIA_TYPE` |
| `PUT /api/students/1` with malformed JSON | 400 | `errorCode: BAD_REQUEST` |
| `POST /api/students` with empty body | 400 | `errorCode: VALIDATION_ERROR` |

---

## 6. Expected curl response for a non-existent student

```bash
curl -i 'http://localhost:8080/api/students/1001111' \
  --header 'Authorization: Bearer <valid-jwt>' \
  --header 'X-Library-Id: 1' \
  --header 'Accept: application/json'
```

```
HTTP/1.1 404
Content-Type: application/json

{
    "success": false,
    "message": "Student not found",
    "data": null,
    "errorCode": "STUDENT_NOT_FOUND"
}
```

---

## 7. Security guarantees (unchanged)

- `X-Library-Id` is **not** trusted as a substitute for server-side membership
  validation. `JwtAuthenticationFilter` resolves the tenant from the user's server-side
  associations; `TenantFilter` only falls back to the header when nothing has been
  resolved.
- Cross-library access -> 403. Header spoofing -> 403. Unauthenticated -> 401.
- `ForbiddenException` still maps to 403; `@PreAuthorize` denials still map to 403.
- Unexpected programming, database and system exceptions still map to
  `INTERNAL_ERROR` / 500, and now do so in one case where they previously did not.

---

## Correction to the earlier conclusion

The earlier revision of this document, and of `EXCEPTION_HANDLING_ANALYSIS.md` and
`INVESTIGATION_SUMMARY.md`, stated:

> "The architecture is CORRECT — exception handling is already properly implemented.
> The INTERNAL_ERROR you experienced was likely caused by an expired or invalid JWT
> token, a missing X-Library-Id header, or a user lacking STUDENT_VIEW permission."

That was wrong on two counts:

1. **It blamed the caller without evidence.** None of those causes produces
   `INTERNAL_ERROR`. An invalid JWT yields 401 `UNAUTHORIZED`; a missing permission
   yields 403 `FORBIDDEN`; a missing `X-Library-Id` header falls back to the user's
   primary library. All three were verified live.
2. **It missed two real defects**, because the investigation only read the code and ran
   mock-based tests. Both defects only surface when the running API is actually called.

`EXCEPTION_HANDLING_ANALYSIS.md` also documented the wrong-library case as returning
`RESOURCE_ACCESS_DENIED`. The observed behaviour is `FORBIDDEN`: `requireLibraryAccess`
fails first, so the `RESOURCE_ACCESS_DENIED` branch is not reached on that path.

**Lesson for the next investigation:** do not close an exception-mapping issue on code
review and mocked tests alone. Reproduce the request against a running server.
