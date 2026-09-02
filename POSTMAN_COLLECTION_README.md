# LibrarySaaS E2E API Testing Guide

## Overview
This is a complete end-to-end Postman collection for testing the LibrarySaaS backend API with all authentication, organization, library, and student management endpoints.

## File Location
**Postman Collection File:** `LibrarySaaS-E2E-API-Collection.postman_collection.json`

## How to Import

1. Open **Postman**
2. Click on **Import** button (top-left)
3. Select **Upload Files** tab
4. Choose the `LibrarySaaS-E2E-API-Collection.postman_collection.json` file
5. Click **Import**

## Setup & Configuration

### 1. Create Environment Variables
Before running requests, you need to set up environment variables:

1. In Postman, go to **Environments** (left sidebar)
2. Click **Create new** or edit an existing environment
3. Add the following variables:

```
accessToken       : (will be auto-populated after login)
refreshToken      : (will be auto-populated after login)
organizationId    : 1
libraryId         : 1
studentId         : 1
studentId1        : (will be auto-populated)
studentId2        : (will be auto-populated)
```

### 2. Update Base URL (if needed)
Default base URL is `http://localhost:8080`. If your API is running on a different port:
- Right-click on any request
- Edit the URL or create collection-level variables for the host/port

## Login Credentials

Default test credentials (based on your database):
- **Username/Email:** `owner1`
- **Password:** `password123`

**Note:** These credentials are pre-populated in the Login request. Update them if you have different test users.

## API Collection Structure

### 1. **Authentication** (3 endpoints)
- `POST /api/auth/login` - Login and get tokens
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - Logout (revoke refresh token)

**Features:**
- Auto-populates `accessToken` and `refreshToken` variables after login
- All subsequent requests use Bearer token authentication

### 2. **Organizations** (5 endpoints)
- `POST /api/organizations` - Create organization
- `GET /api/organizations/{organizationId}` - Get single organization
- `GET /api/organizations` - List user's organizations
- `PUT /api/organizations/{organizationId}` - Update organization
- `DELETE /api/organizations/{organizationId}` - Deactivate organization

**Dummy Data:**
```json
{
  "organizationCode": "ORG-2024-001",
  "name": "Acme Library Network",
  "legalName": "Acme Corporation Library Services",
  "email": "admin@acmelibrary.com",
  "mobile": "+1-555-0100"
}
```

### 3. **Libraries** (6 endpoints)
- `POST /api/libraries?organizationId={id}` - Create library
- `GET /api/libraries/{libraryId}` - Get single library
- `GET /api/libraries?organizationId={id}` - List by organization
- `GET /api/libraries` - List all user's libraries
- `PUT /api/libraries/{libraryId}` - Update library
- `DELETE /api/libraries/{libraryId}` - Deactivate library

**Dummy Data:**
```json
{
  "libraryCode": "LIB-2024-001",
  "name": "Central Library",
  "description": "Main central library for Acme organization",
  "email": "central@acmelibrary.com",
  "mobile": "+1-555-0200",
  "openingTime": "09:00",
  "closingTime": "18:00",
  "timezone": "America/New_York",
  "currency": "USD"
}
```

### 4. **Students** (6 endpoints)
- `POST /api/students` - Create student
- `GET /api/students/{id}` - Get single student
- `GET /api/students?page=0&size=20` - List students (paginated)
- `GET /api/students?search=John&page=0&size=20` - Search students
- `PUT /api/students/{id}` - Update student
- `DELETE /api/students/{id}` - Delete student

**Dummy Data:**
```json
{
  "studentCode": "STU-2024-001",
  "firstName": "John",
  "lastName": "Doe",
  "mobile": "+1-555-0300",
  "email": "john.doe@example.com",
  "dateOfBirth": "2005-03-15",
  "gender": "Male",
  "joiningDate": "2024-01-10",
  "status": "ACTIVE"
}
```

### 5. **E2E Test Scenarios** (Complete Flow)
This folder contains a full workflow:

1. **Step 1: Login** - Authenticate and get tokens
2. **Step 2: Create Organization** - Create a new organization
3. **Step 3: Create Library** - Add library to organization
4. **Step 4: Add Student 1** - Create first student
5. **Step 5: Add Student 2** - Create second student
6. **Step 6: List All Students** - Verify students are created

**Run this in sequence** to test the complete workflow.

## How to Use

### Method 1: Manual Testing
1. Click on any request in the collection
2. Review the payload in the Body tab (pre-filled with dummy data)
3. Click **Send**
4. View the response in the lower panel

### Method 2: Run Complete Collection
1. Click on the collection name
2. Click **Run** (▶️ icon)
3. Select all requests or specific folder
4. Set iterations (number of times to run)
5. Click **Run**

### Method 3: Run E2E Scenario Only
1. Open the **E2E Test Scenarios** folder
2. Click the folder, then click **Run**
3. Run the "Complete Flow" folder to execute all steps in sequence

## Authentication Flow

Every API request (except login) requires:
- **Type:** Bearer Token
- **Token:** `{{accessToken}}`

The token is automatically set after you run the Login request. If token expires:
1. Run **Refresh Token** request
2. A new `{{accessToken}}` will be automatically updated
3. Continue with other requests

## Response Format

All responses follow this format:
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    // Response payload
  }
}
```

## Test Assertions

Each request includes automated tests that verify:
- ✅ Correct HTTP status code (200, 201, etc.)
- ✅ Response has expected properties
- ✅ Data values match expectations
- ✅ Tokens are properly set

**View test results** in the **Test Results** tab after running a request.

## Important Notes

### Security Features
- Multi-tenancy: Each organization/library is isolated
- Authorization: Users can only access their organizations/libraries
- Token expiration: Access tokens expire, use refresh token to get new ones

### Pagination
Student list endpoints support pagination:
- `page=0` - First page (0-indexed)
- `size=20` - Records per page (default 20)

### Search & Filter
Student search supports:
- Search by name: `?search=John`
- Filter by status: `?status=ACTIVE`
- Combine: `?search=John&status=ACTIVE&page=0&size=10`

### Required Fields

**Organization:**
- organizationCode, name, legalName, email, mobile

**Library:**
- libraryCode, name, email, mobile, openingTime, closingTime, timezone, currency

**Student:**
- studentCode, firstName, joiningDate (lastName, gender, etc. are optional but provided in examples)

## Troubleshooting

### 401 Unauthorized
**Problem:** Token expired or invalid
**Solution:** Run the Login request again to get a fresh token

### 403 Forbidden
**Problem:** User doesn't have permission
**Solution:** Ensure you're using the correct user (owner1) with proper authorities

### 404 Not Found
**Problem:** Resource doesn't exist
**Solution:** 
- Create the resource first (Organization → Library → Students)
- Check the IDs in URLs match existing resources

### 400 Bad Request
**Problem:** Invalid payload
**Solution:** Verify all required fields are present and have correct data types

## Environment Variables Reference

| Variable | Default | Auto-Set | Purpose |
|----------|---------|----------|---------|
| accessToken | empty | ✅ Login | JWT access token for API requests |
| refreshToken | empty | ✅ Login | Token to refresh access token |
| organizationId | 1 | - | Organization ID for API calls |
| libraryId | 1 | - | Library ID for API calls |
| studentId | 1 | - | Student ID for API calls |
| studentId1 | empty | ✅ E2E Step 4 | First student created in E2E |
| studentId2 | empty | ✅ E2E Step 5 | Second student created in E2E |

## Sample Response Examples

### Successful Login
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresInSec": 3600,
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "refreshExpiresInSec": 604800
  }
}
```

### Student Created
```json
{
  "success": true,
  "message": "Student created",
  "data": {
    "id": 1,
    "libraryId": 1,
    "studentCode": "STU-2024-001",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "mobile": "+1-555-0300",
    "dateOfBirth": "2005-03-15",
    "gender": "Male",
    "joiningDate": "2024-01-10",
    "status": "ACTIVE",
    "createdAt": "2024-09-01T10:30:45"
  }
}
```

## Best Practices

1. **Always login first** - Get tokens before testing other endpoints
2. **Follow resource hierarchy** - Create Org → Library → Students
3. **Use variables** - Don't hardcode IDs in subsequent requests
4. **Check test results** - Every request has assertions to verify correctness
5. **Clean up** - Delete created resources after testing to avoid data clutter

## Additional Resources

- **OpenAPI Documentation:** `http://localhost:8080/swagger-ui.html`
- **API Docs:** `http://localhost:8080/v3/api-docs`
- **Database:** localhost:3307 (MySQL)
  - User: root
  - Password: password
  - Database: librarydb

## Support

For issues with the API, check:
1. Application logs for errors
2. Database for data consistency
3. Postman console (View → Show Postman Console) for HTTP details
