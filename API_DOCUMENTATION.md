# Library SaaS Backend - API Documentation (Swagger/OpenAPI)

**Status**: ✅ OpenAPI Configuration Ready for Phase 1E Controllers

---

## Swagger UI Access URLs

Once the application is running, access the API documentation at:

| Resource | URL | Description |
|----------|-----|-------------|
| **Swagger UI** | `http://localhost:8081/swagger-ui.html` | Interactive API documentation with try-it-out capability |
| **OpenAPI JSON** | `http://localhost:8081/v3/api-docs` | Raw OpenAPI 3.0.0 specification in JSON format |
| **OpenAPI YAML** | `http://localhost:8081/v3/api-docs.yaml` | OpenAPI specification in YAML format |

---

## How to Start the Application

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker Desktop (for MySQL Testcontainer)
- Environment variables configured:
  ```bash
  DB_URL=jdbc:mysql://localhost:3310/library_saas
  DB_USERNAME=root
  DB_PASSWORD=root
  SERVER_PORT=8081
  ```

### Start the Application

**Option 1: Run via Maven**
```bash
cd C:\LIbrarySAAS\library-saas-backend
mvn spring-boot:run
```

**Option 2: Run compiled JAR**
```bash
mvn clean package -DskipTests
java -jar target/library-saas-backend-0.0.1-SNAPSHOT.jar
```

**Option 3: Run in IDE (Eclipse/IntelliJ)**
1. Right-click project → Run As → Spring Boot App
2. Or run the `LibrarySaasApplication` main class

### Expected Startup Output
```
[main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port(s): 8081 (http)
[main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8081 (http) with context path ''
[main] com.librarysaas.LibrarySaasApplication   : Started LibrarySaasApplication in XX.XXX seconds
```

### Verify Application is Running
```bash
curl http://localhost:8081/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

---

## Accessing Swagger UI

### Step 1: Start the Application
Follow the startup instructions above.

### Step 2: Open Browser
Navigate to: `http://localhost:8081/swagger-ui.html`

### Step 3: Expected Swagger UI Screen
You should see:
- **Title**: "Library SaaS Backend API"
- **Version**: 1.0.0
- **Description**: Multi-tenant Library & Study Center Business Management SaaS
- **Authorization**: Option to enter JWT Bearer token

---

## Current API Status (Phase 1B)

### ✅ Completed (Foundation Ready)
- [x] OpenAPI/Swagger configuration
- [x] JWT authentication integration documented
- [x] Multi-tenant data model documented
- [x] Database schema finalized (MySQL 8.0.33)
- [x] Repository layer fully tested (74/74 tests passing)

### 🔄 In Progress (Phase 1E)
- [ ] REST Controllers implementation
- [ ] Request/Response DTOs
- [ ] Endpoint documentation with @Operation/@Parameter annotations
- [ ] Error handling standardization
- [ ] Request validation decorators

### 📋 Planned (Phase 1F+)
- [ ] Advanced endpoint documentation
- [ ] Example request/response bodies in Swagger
- [ ] API versioning (v1, v2, etc.)
- [ ] API gateway integration
- [ ] Rate limiting documentation

---

## API Architecture Overview

### Authentication Flow

```
┌─────────────────────────────────────────────────────────┐
│                    Client Application                    │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────┐
        │  POST /auth/login                │
        │  (Username + Password)           │
        └────────────┬─────────────────────┘
                     │
                     ▼
        ┌──────────────────────────────────┐
        │  JWT Token Generated             │
        │  (access_token + refresh_token)  │
        └────────────┬─────────────────────┘
                     │
                     ▼
        ┌──────────────────────────────────┐
        │ Authorization Header:            │
        │ Bearer eyJhbGc...                │
        └────────────┬─────────────────────┘
                     │
                     ▼
        ┌──────────────────────────────────┐
        │  Protected API Endpoints         │
        │  (Auto-scoped to user's org)     │
        └──────────────────────────────────┘
```

### Multi-Tenant Scoping

```
User makes API request with JWT token
                    │
                    ▼
        ┌────────────────────────────────┐
        │  JwtAuthenticationFilter       │
        │  (Extract user from token)     │
        └────────────┬───────────────────┘
                     │
                     ▼
        ┌────────────────────────────────┐
        │  TenantAuthorizationService    │
        │  (Verify user access)          │
        └────────────┬───────────────────┘
                     │
                     ▼
        ┌────────────────────────────────┐
        │  Repository Query              │
        │  (Auto-filter by user's org)   │
        └────────────┬───────────────────┘
                     │
                     ▼
        ┌────────────────────────────────┐
        │  Return user's data only       │
        │  (No cross-tenant leakage)     │
        └────────────────────────────────┘
```

---

## API Endpoints (Planned for Phase 1E)

### Authentication Endpoints

| Method | Endpoint | Status | Phase |
|--------|----------|--------|-------|
| POST | `/auth/login` | 📋 Planned | 1E |
| POST | `/auth/register` | 📋 Planned | 1E |
| POST | `/auth/refresh` | 📋 Planned | 1E |
| POST | `/auth/logout` | 📋 Planned | 1E |

### Organization Endpoints

| Method | Endpoint | Status | Phase |
|--------|----------|--------|-------|
| GET | `/api/v1/organizations` | 📋 Planned | 1E |
| GET | `/api/v1/organizations/{id}` | 📋 Planned | 1E |
| POST | `/api/v1/organizations` | 📋 Planned | 1E |
| PUT | `/api/v1/organizations/{id}` | 📋 Planned | 1E |
| DELETE | `/api/v1/organizations/{id}` | 📋 Planned | 1E |
| GET | `/api/v1/organizations/{id}/libraries` | 📋 Planned | 1E |
| GET | `/api/v1/organizations/{id}/members` | 📋 Planned | 1E |

### Library Endpoints

| Method | Endpoint | Status | Phase |
|--------|----------|--------|-------|
| GET | `/api/v1/libraries` | 📋 Planned | 1E |
| GET | `/api/v1/libraries/{id}` | 📋 Planned | 1E |
| POST | `/api/v1/organizations/{orgId}/libraries` | 📋 Planned | 1E |
| PUT | `/api/v1/libraries/{id}` | 📋 Planned | 1E |
| DELETE | `/api/v1/libraries/{id}` | 📋 Planned | 1E |
| GET | `/api/v1/libraries/{id}/students` | 📋 Planned | 1E |

### Student Endpoints

| Method | Endpoint | Status | Phase |
|--------|----------|--------|-------|
| GET | `/api/v1/libraries/{libId}/students` | 📋 Planned | 1E |
| GET | `/api/v1/students/{id}` | 📋 Planned | 1E |
| POST | `/api/v1/libraries/{libId}/students` | 📋 Planned | 1E |
| PUT | `/api/v1/students/{id}` | 📋 Planned | 1E |
| DELETE | `/api/v1/students/{id}` | 📋 Planned | 1E |

---

## Authentication Example

### Example 1: Login and Get Token

**Request:**
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "owner1",
    "password": "password123"
  }'
```

**Response (200 OK):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### Example 2: Use Token to Call Protected Endpoint

**Request:**
```bash
curl -X GET http://localhost:8081/api/v1/organizations \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Response (200 OK):**
```json
[
  {
    "organizationId": 1,
    "organizationCode": "ORG001",
    "name": "Bright Future Education",
    "status": "ACTIVE"
  },
  {
    "organizationId": 2,
    "organizationCode": "ORG002",
    "name": "Knowledge Hub Group",
    "status": "ACTIVE"
  }
]
```

---

## OpenAPI Configuration Details

### Configuration File: `OpenAPIConfiguration.java`

**Location**: `src/main/java/com/librarysaas/config/OpenAPIConfiguration.java`

**Key Features**:
- ✅ OpenAPI 3.0.0 specification
- ✅ JWT Bearer authentication scheme documented
- ✅ Multi-tenant architecture description
- ✅ Error handling patterns documented
- ✅ Automatic Swagger UI generation

### Application Properties

**File**: `src/main/resources/application.yml`

**Swagger Configuration**:
```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
    enable-cookie-params: false
    display-request-duration: true
  api-docs:
    path: /v3/api-docs
  show-actuator: false
```

**Key Properties**:
- `path`: URL to access Swagger UI
- `operations-sorter: method`: Sort endpoints by HTTP method (GET, POST, etc.)
- `tags-sorter: alpha`: Sort tags alphabetically
- `display-request-duration: true`: Show how long each request took
- `show-actuator: false`: Hide Spring Actuator endpoints from Swagger

---

## Error Responses

### Standardized Error Format

All error responses follow this format:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/organizations",
  "timestamp": "2026-09-01T16:00:00.000Z",
  "details": {
    "fieldName": "error detail"
  }
}
```

### Common HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | OK | Successful request, data returned |
| 201 | Created | Resource successfully created |
| 204 | No Content | Successful deletion |
| 400 | Bad Request | Invalid input data |
| 401 | Unauthorized | Missing/invalid JWT token |
| 403 | Forbidden | User lacks permission for resource |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Resource already exists |
| 500 | Server Error | Unexpected server error |

---

## Testing API Endpoints with Swagger UI

### Step 1: Authorization
1. Click the green **"Authorize"** button in Swagger UI
2. Paste your JWT token in the value field: `Bearer <token>`
3. Click **"Authorize"** and close the dialog

### Step 2: Try an Endpoint
1. Click on an endpoint (e.g., `GET /api/v1/organizations`)
2. Click **"Try it out"** button
3. Fill in required parameters
4. Click **"Execute"** button
5. View response code and body

### Example with cURL
```bash
curl -X GET http://localhost:8081/api/v1/organizations \
  -H "Authorization: Bearer <your-jwt-token>" \
  -H "Content-Type: application/json" \
  -v
```

---

## Development Tips

### Enable Swagger UI for Development
Swagger UI is enabled by default. To disable in production, use environment variable:
```bash
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

### View Raw OpenAPI Specification
```bash
curl http://localhost:8081/v3/api-docs | jq .
```

### Generate OpenAPI from Running App
```bash
curl http://localhost:8081/v3/api-docs > openapi.json
```

### Validate OpenAPI Specification
```bash
# Using swagger-cli
swagger-cli validate openapi.json

# Using spectacle (HTML docs generator)
npm install -g spectacle-docs
spectacle openapi.json -d output-docs
```

---

## Document Generation from OpenAPI

### Generate Postman Collection
1. Open Swagger UI
2. Click menu icon (⋯) → **"Download"**
3. Import into Postman

### Generate HTML Documentation
```bash
# Option 1: Using Spectacle
npm install -g spectacle-docs
spectacle http://localhost:8081/v3/api-docs

# Option 2: Using ReDoc
docker run -p 8000:80 -e SPEC_URL=http://host.docker.internal:8081/v3/api-docs redocly/redoc
```

### Generate Code from OpenAPI
```bash
# Using OpenAPI Generator
npx openapi-generator-cli generate \
  -i http://localhost:8081/v3/api-docs \
  -g typescript-fetch \
  -o ./generated-client
```

---

## Security Considerations

### ✅ Security Implemented
- ✅ JWT Bearer token authentication
- ✅ Role-based access control (RBAC)
- ✅ Multi-tenant data isolation (automatic scoping)
- ✅ SQL injection prevention (parameterized queries)
- ✅ CORS configuration

### 🔄 Security Recommendations
- Use HTTPS in production (set up SSL/TLS)
- Rotate JWT secrets regularly
- Implement rate limiting on public endpoints
- Add request logging and monitoring
- Enable CORS only for trusted origins
- Use httpOnly + Secure cookies for refresh tokens

### ⚠️ Never expose in Swagger UI (Production)
- Database credentials
- API keys
- Secrets
- Internal service URLs

---

## Troubleshooting

### Issue: Swagger UI not loading
**Solution**: 
- Ensure application is running on correct port (8081)
- Check if springdoc-openapi dependency is in pom.xml
- Verify no exception in application logs

### Issue: "No operations found" in Swagger UI
**Status**: Expected in Phase 1B (no controllers yet)
**Solution**: Wait for Phase 1E when REST controllers are implemented

### Issue: Authorization not working
**Solution**:
1. Copy full token including "Bearer " prefix
2. Ensure token hasn't expired
3. Verify JWT secret matches in configuration

### Issue: CORS errors when testing in Swagger UI
**Solution**: 
- Configure CORS in `CorsConfiguration.java`
- Add allowed origins to `application.yml`

---

## Phase 1E Controller Development

When implementing Phase 1E REST controllers, use these annotations for automatic Swagger documentation:

```java
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization management endpoints")
public class OrganizationController {

    @GetMapping
    @Operation(summary = "List user's organizations", 
               description = "Retrieve all organizations the authenticated user is a member of")
    @ApiResponse(responseCode = "200", description = "Organizations retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    public ResponseEntity<List<OrganizationResponse>> listOrganizations() {
        // Implementation
    }
}
```

---

## Quick Reference

| Task | Command |
|------|---------|
| Start app | `mvn spring-boot:run` |
| Access Swagger | `http://localhost:8081/swagger-ui.html` |
| Get OpenAPI JSON | `curl http://localhost:8081/v3/api-docs` |
| Build JAR | `mvn clean package -DskipTests` |
| Run tests | `mvn test` |
| Check health | `curl http://localhost:8081/actuator/health` |

---

**Document Version**: 1.0  
**Created**: 2026-09-01  
**Status**: Ready for Phase 1E Controller Implementation
