# Library & Study Center SaaS Backend (Phase 0)

This repository contains the Phase 0 foundation for a multi-tenant Library & Study Center SaaS backend.

Prerequisites:
- Java 21
- Maven
- MySQL 8+ (for running the application, not required for tests)

Quick start (development):
1. Copy `.env.example` to `.env` and set DB_URL, DB_USERNAME, DB_PASSWORD
2. Build and test:
   mvn clean test
3. Run:
   mvn spring-boot:run

Notes:
- Flyway manages DB migrations. Replace `src/main/resources/db/migration/V1__initial_schema.sql` with the approved schema file.
- Tests run with an in-memory H2 database (see `application-test.yml`).
 - Swagger UI available at `/swagger-ui.html` when application runs (dev).

## Authentication

POST /api/auth/login accepts JSON credentials and returns an access token (JWT) and a refresh token when available. Example request body:

```json
{
  "identifier": "alice@example.com",
  "password": "secret"
}
```

The login response contains:
- accessToken: the Bearer token to include in the Authorization header for subsequent requests
- refreshToken: (optional) a rotating refresh token used to obtain a new access token via POST /api/auth/refresh

Use the access token like:
Authorization: Bearer <accessToken>

POST /api/auth/refresh accepts a JSON body { "refreshToken": "..." } and returns a new access token and rotated refresh token.

## Tenant resolution (how the server determines organization/library)

This application uses a server-side authoritative tenant resolution process combined with optional client-supplied fallbacks. The implemented behavior is:

- Primary resolution (authoritative): when a valid JWT access token is presented, the application resolves the authenticated user's primary organization and library from the database and sets those values in a thread-local `TenantContext`. This is performed by the `JwtAuthenticationFilter` at authentication time.
- Header fallback: the server supports the HTTP headers `X-Organization-Id` and `X-Library-Id` (handled by `TenantFilter`) but only as a fallback when the server has not already resolved a tenant for the request. In practice this means headers are used when no authenticated user tenant has been set. A valid JWT (and user record) will always take precedence — clients cannot override server-resolved tenants by sending headers.
- Request-parameter fallback: some controller endpoints accept an optional request parameter `libraryId` (for example `GET /api/students?libraryId=...`). This is treated as a last-resort fallback and is only used when `TenantContext` is empty. The server still verifies that the authenticated user is a member of the requested library.

Important guarantees:
- Clients must not rely on providing `libraryId` in the request body to assign a resource to a library — the server determines the target library.
- The authenticated user must be a member of the library they are operating on; membership is verified on every Student operation via `TenantAuthorizationService`.

## Student API (tenant-safe contract)

The Student endpoints are under `/api/students`. All requests require authentication and appropriate permissions (STUDENT_VIEW, STUDENT_CREATE, STUDENT_UPDATE, STUDENT_DELETE).

Key rules:
- `StudentCreateRequest` does NOT accept `libraryId`. The server resolves the library from `TenantContext` (derived from the JWT primary library) or, if missing, from the user's primary library. This prevents tenant spoofing.
- Controllers may accept an optional `libraryId` request parameter as a fallback when `TenantContext` is empty, but this parameter cannot be used to bypass membership checks.
- All operations will throw 403 Forbidden if the authenticated user lacks membership in the target library, and 404 Not Found when the target student does not exist in the expected library.

Student create/update JSON examples (fields reflect the DTOs in `src/main/java/com/librarysaas/student/dto`):

Create (POST /api/students) - request body:
```json
{
  "studentCode": "S1001",
  "firstName": "Alice",
  "lastName": "Wang",
  "mobile": "555-0100",
  "email": "alice@example.com",
  "dateOfBirth": "2006-02-10",
  "gender": "F",
  "joiningDate": "2026-08-01",
  "status": "ACTIVE"
}
```

Update (PUT /api/students/{id}) - request body:
```json
{
  "firstName": "Alice",
  "lastName": "Wang",
  "mobile": "555-0100",
  "email": "alice@example.com",
  "dateOfBirth": "2006-02-10",
  "gender": "F",
  "joiningDate": "2026-08-01",
  "status": "ACTIVE"
}
```

Response DTOs include `libraryId` in the response (server-populated) but clients must never set it for create/update.

## Curl examples

1) Login - obtain access + refresh token
```
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"identifier":"alice@example.com","password":"secret"}'
```

2) Create student (server resolves library from JWT or primary membership)
```
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{"studentCode":"S1001","firstName":"Alice","lastName":"Wang","joiningDate":"2026-08-01"}'
```

3) Get student
```
curl -X GET http://localhost:8080/api/students/5 \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

4) List students (supports pagination and optional search/status)
```
curl -X GET 'http://localhost:8080/api/students?page=0&size=20&search=Alice' \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

5) Update student
```
curl -X PUT http://localhost:8080/api/students/5 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{"firstName":"Alice","lastName":"Wang","mobile":"555-0100"}'
```

6) Delete student
```
curl -X DELETE http://localhost:8080/api/students/5 \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Notes about `X-Library-Id` header:
- The application accepts `X-Library-Id` (and `X-Organization-Id`) as a fallback when the server has not already resolved a tenant for the request. If you are authenticated with a JWT that includes a resolved primary library, that server-resolved value will be authoritative and headers will not override it. Use headers only for unauthenticated flows or special tooling where no JWT is present.

## OpenAPI / Swagger

This project exposes OpenAPI metadata via `OpenApiConfig`. The Student endpoints use DTO classes (`StudentCreateRequest`, `StudentUpdateRequest`, `StudentResponse`) and **do not** expose a `libraryId` in the create request schema. No code changes to OpenAPI were required — the DTOs already reflect the intended contract.

## Verification

Run `mvn clean test`. Tests in this workspace currently pass:

- Tests run: 17, Failures: 0, Errors: 0
- BUILD SUCCESS
