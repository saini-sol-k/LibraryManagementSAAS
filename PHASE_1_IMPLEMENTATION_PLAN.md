# Phase 1: Organization & Library/Branch Management Implementation Plan

**Date**: September 1, 2026  
**Phase**: Phase 1 - Organization & Library/Branch Multi-Tenancy  
**Status**: ✅ Analysis Complete, Ready for Approval  

---

## Executive Summary

Phase 1 will implement complete Organization and Library/Branch management with proper tenant isolation. The database schema, roles, permissions, and tenant infrastructure are **already in place**. We need to:

1. Create missing JPA entities (Organization, UserOrganization, UserLibrary, Address relationships)
2. Enhance existing entities with proper JPA relationships
3. Create services and controllers for Organization and Library management
4. Add comprehensive tests for tenant isolation
5. Ensure all operations are tenant-scoped

**No database schema changes are needed.** All infrastructure already exists and is properly configured.

---

## 1. Current State Analysis

### ✅ What Already Exists

#### Database Schema
- ✅ `organization` table (complete)
- ✅ `library` table with FK to `organization_id` (complete)
- ✅ `user_organization` table for multi-org membership (complete)
- ✅ `user_library` table for multi-library membership (complete)
- ✅ `organization_address` table for org addresses (complete)
- ✅ `library_address` table for library addresses (complete)
- ✅ `address` table for reusable addresses (complete)
- ✅ All foreign key constraints in place
- ✅ Appropriate indices for queries

#### Roles & Permissions
- ✅ SUPER_ADMIN role (platform-level access)
- ✅ ORGANIZATION_OWNER role (org-level access)
- ✅ ORGANIZATION_ADMIN role (org-level administration)
- ✅ LIBRARY_MANAGER role (library-level access)
- ✅ LIBRARY_STAFF role (library staff)
- ✅ RECEPTIONIST role (receptionist duties)
- ✅ ACCOUNTANT role (financial operations)

- ✅ ORGANIZATION_VIEW, ORGANIZATION_UPDATE permissions
- ✅ LIBRARY_VIEW, LIBRARY_CREATE, LIBRARY_UPDATE, LIBRARY_STATUS_UPDATE permissions
- ✅ USER_VIEW, USER_CREATE, USER_UPDATE permissions

#### Security Infrastructure
- ✅ TenantAuthorizationService with:
  - `hasOrganizationAccess()` / `requireOrganizationAccess()`
  - `hasLibraryAccess()` / `requireLibraryAccess()`
  - `findPrimaryOrganizationIdForUser()`
  - `findPrimaryLibraryIdForUser()`
  - `hasPermission()` / `requirePermission()`

- ✅ UserTenantRepository with:
  - `findPrimaryOrganizationId()`
  - `findPrimaryLibraryId()`
  - `existsInOrganization()`
  - `existsInLibrary()`

- ✅ TenantContext for request-scoped tenant resolution

#### Test Data
- ✅ 2 organizations (Bright Future Education, Knowledge Hub Group)
- ✅ 3 libraries across 2 organizations
- ✅ 4 users with different roles
- ✅ User-organization memberships
- ✅ User-library memberships
- ✅ Sample BCrypt-hashed password (Password@123)

#### Existing Entities
- ✅ User entity (basic, needs enhancement)
- ✅ Library entity (basic, needs enhancement)
- ✅ LibraryRepository (empty, needs queries)
- ✅ Student entity (tenant-scoped, complete)
- ✅ StudentService (good example of tenant isolation pattern)

### ❌ What's Missing

#### JPA Entities
- ❌ Organization entity (NO ENTITY CLASS, only DB table exists)
- ❌ UserOrganization entity (NO ENTITY CLASS, only DB table exists)
- ❌ UserLibrary entity (NO ENTITY CLASS, only DB table exists)
- ❌ Address entity (NO ENTITY CLASS, only DB table exists)
- ❌ Proper @ManyToOne relationships in entities

#### Repositories
- ❌ OrganizationRepository (no interface)
- ❌ UserOrganizationRepository (no interface)
- ❌ UserLibraryRepository (no interface)
- ❌ AddressRepository (no interface)
- ❌ Enhanced LibraryRepository queries

#### Services
- ❌ OrganizationService (interface + implementation)
- ❌ LibraryService (interface + implementation)
- ❌ UserManagementService (for org/library user management)

#### Controllers
- ❌ OrganizationController (REST endpoints)
- ❌ LibraryController (REST endpoints)

#### DTOs
- ❌ OrganizationCreateRequest
- ❌ OrganizationUpdateRequest
- ❌ OrganizationResponse
- ❌ LibraryCreateRequest
- ❌ LibraryUpdateRequest
- ❌ LibraryResponse
- ❌ AddressRequest
- ❌ AddressResponse

#### Tests
- ❌ OrganizationServiceTest (unit tests)
- ❌ LibraryServiceTest (unit tests)
- ❌ OrganizationIntegrationTest (integration tests with tenant isolation)
- ❌ LibraryIntegrationTest (integration tests with tenant isolation)

---

## 2. Database Relationships & Foreign Keys

### Multi-Tenancy Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                    ORGANIZATION (Top-Level Tenant)          │
│                                                             │
│  - organization_id (PK)                                    │
│  - organization_code (UNIQUE)                              │
│  - name, legal_name, email, mobile, status                 │
│  - addresses via organization_address (1:M)               │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │           LIBRARY (Branch/Location)                 │ │
│  │                                                      │ │
│  │  - library_id (PK)                                  │ │
│  │  - organization_id (FK → organization)            │ │
│  │  - library_code (UNIQUE per org)                  │ │
│  │  - name, description, email, mobile, status      │ │
│  │  - opening_time, closing_time, timezone, currency│ │
│  │  - addresses via library_address (1:M)          │ │
│  │  - students via student (1:M)                    │ │
│  │  - seats, attendance, fees, payments (1:M each) │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                         USER (Cross-Tenant)                 │
│                                                             │
│  - user_id (PK)                                            │
│  - username, email, password_hash, status                 │
│  - first_name, last_name                                  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │       USER_ORGANIZATION (M:M Membership)            │ │
│  │                                                      │ │
│  │  - (user_id, organization_id) PK                   │ │
│  │  - is_primary: User's primary organization        │ │
│  │  - status: ACTIVE or INACTIVE                      │ │
│  │  - joined_at: When joined                          │ │
│  │                                                      │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │       USER_LIBRARY (M:M Membership)                 │ │
│  │                                                      │ │
│  │  - (user_id, library_id) PK                        │ │
│  │  - is_primary: User's primary library              │ │
│  │  - status: ACTIVE or INACTIVE                      │ │
│  │  - joined_at: When joined                          │ │
│  │                                                      │ │
│  │  *** Enforced: Library's organization must match   │ │
│  │      user's active organization membership         │ │
│  │                                                      │ │
│  └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  SECURITY (Roles & Permissions)             │
│                                                             │
│  Role Scopes:                                              │
│  - PLATFORM: SUPER_ADMIN (all orgs/libraries)            │
│  - ORGANIZATION: OWNER, ADMIN (entire organization)       │
│  - LIBRARY: MANAGER, STAFF, RECEPTIONIST, ACCOUNTANT      │
│                                                             │
│  Permission Categories:                                    │
│  - ORGANIZATION_*: Organization-level operations         │
│  - LIBRARY_*: Library management                         │
│  - USER_*: User management                               │
│  - STUDENT_*, SEAT_*, ATTENDANCE_*, PAYMENT_*, FEE_*     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Key Constraints

1. **Library → Organization**: Every library belongs to exactly one organization
2. **User → Organization**: Users can belong to multiple organizations (M:M)
3. **User → Library**: Users can belong to multiple libraries (M:M)
4. **Library Membership Constraint**: 
   - User can only be added to libraries within their active organization membership
   - User's library_id must belong to an organization where user has active membership
5. **Primary Tenant**: Each user has ONE primary organization and ONE primary library
6. **Address Reusability**: Same address can be reused by multiple organizations/libraries/students

---

## 3. Tenant Isolation Architecture

### How Tenant Isolation Works

```
1. REQUEST ARRIVES
   ↓
2. JwtAuthenticationFilter validates JWT
   ↓
3. TenantContext.setLibraryId(user's primary library)
   (or optionally: X-Library-Id header if no JWT)
   ↓
4. Service layer:
   a) Get current user from SecurityContext
   b) Get libraryId from TenantContext (OR request parameter)
   c) Verify user has access to that library:
      → tenantAuthService.requireLibraryAccess(userId, libraryId)
   d) Query/operate on data scoped to that library_id
   ↓
5. Database query includes WHERE library_id = ?
   ↓
6. Result returned only for requested library
```

### Key Principles

- **Server-Authoritative**: Server always resolves which organization/library user can access
- **No Client Override**: Client cannot specify a different org/library via request body
- **Hierarchical**: All library operations scoped to organization
- **Permission Checks**: Before every data operation, permission is verified
- **No Cross-Tenant Data**: Queries always include library_id in WHERE clause

---

## 4. Required API Endpoints

### Organization Management

**Permissions Required**: ORGANIZATION_VIEW, ORGANIZATION_UPDATE, LIBRARY_CREATE, etc.

```
Organization Endpoints:
├── GET    /api/organizations
│   └─ List organizations user belongs to (scoped by user_organization)
├── GET    /api/organizations/{orgId}
│   └─ Get single organization (verify membership)
├── POST   /api/organizations
│   └─ Create organization (SUPER_ADMIN only initially)
├── PUT    /api/organizations/{orgId}
│   └─ Update organization (ORGANIZATION_OWNER/ADMIN only)
└── DELETE /api/organizations/{orgId}
    └─ Deactivate organization (SUPER_ADMIN/OWNER only)
```

### Library Management

**Permissions Required**: LIBRARY_VIEW, LIBRARY_CREATE, LIBRARY_UPDATE, LIBRARY_STATUS_UPDATE

```
Library Endpoints:
├── GET    /api/libraries
│   └─ List libraries user belongs to (scoped by user_library)
│   └─ Optional filter: ?organizationId=X (verify org access)
├── GET    /api/libraries/{libId}
│   └─ Get single library (verify membership)
├── POST   /api/libraries
│   └─ Create library in specified organization
│   └─ Server sets organization_id (not client-provided)
├── PUT    /api/libraries/{libId}
│   └─ Update library (must be member)
├── DELETE /api/libraries/{libId}
│   └─ Deactivate library (ORGANIZATION_OWNER/ADMIN only)
└── GET    /api/libraries/{libId}/settings
    └─ Get library-specific settings
```

### User Management (Organization/Library Level)

**Permissions Required**: USER_VIEW, USER_CREATE, USER_UPDATE

```
User Assignment Endpoints:
├── POST   /api/organizations/{orgId}/users
│   └─ Add user to organization (ORGANIZATION_OWNER/ADMIN)
├── DELETE /api/organizations/{orgId}/users/{userId}
│   └─ Remove user from organization
├── POST   /api/libraries/{libId}/users
│   └─ Add user to library (ORGANIZATION_OWNER/LIBRARY_MANAGER)
│   └─ Constraint: User must already be in library's organization
├── DELETE /api/libraries/{libId}/users/{userId}
│   └─ Remove user from library
└── GET    /api/libraries/{libId}/users
    └─ List users in library
```

---

## 5. Required JPA Entities

### Organization Entity
```java
@Entity
@Table(name = "organization")
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long organizationId;
    
    @Column(unique = true, nullable = false)
    private String organizationCode;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    private String legalName;
    private String email;
    private String mobile;
    private String status;
    
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Library> libraries;
    
    @OneToMany(mappedBy = "organization")
    private List<UserOrganization> userOrganizations;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // ... getters/setters
}
```

### UserOrganization Entity
```java
@Entity
@Table(name = "user_organization")
public class UserOrganization {
    @EmbeddedId
    private UserOrganizationKey id;  // (user_id, organization_id)
    
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "organization_id", insertable = false, updatable = false)
    private Organization organization;
    
    private Boolean isPrimary;
    private String status;
    private LocalDateTime joinedAt;
    // ... getters/setters
}
```

### UserLibrary Entity
```java
@Entity
@Table(name = "user_library")
public class UserLibrary {
    @EmbeddedId
    private UserLibraryKey id;  // (user_id, library_id)
    
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "library_id", insertable = false, updatable = false)
    private Library library;
    
    private Boolean isPrimary;
    private String status;
    private LocalDateTime joinedAt;
    // ... getters/setters
}
```

### Address Entity
```java
@Entity
@Table(name = "address")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;
    
    private String firstName;
    private String lastName;
    private String addressLine1;
    private String addressLine2;
    private String landmark;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String phone1;
    private String phone2;
    private String email;
    
    private LocalDateTime createdAt;
    // ... getters/setters
}
```

### Enhanced Library Entity
```java
@Entity
@Table(name = "library")
public class Library {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long libraryId;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;  // ← NEW: Relationship
    
    @Column(nullable = false, length = 50)
    private String libraryCode;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    private String description;
    private String email;
    private String mobile;
    private String status;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private String timezone;
    private String currency;
    
    @OneToMany(mappedBy = "library")
    private List<UserLibrary> userLibraries;
    
    @OneToMany(mappedBy = "library")
    private List<Student> students;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // ... getters/setters
}
```

### Enhanced User Entity
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    private String mobile;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    private String firstName;
    
    private String lastName;
    private String status;
    private Boolean emailVerified;
    private Boolean mobileVerified;
    private LocalDateTime lastLoginAt;
    
    @OneToMany(mappedBy = "user")
    private List<UserOrganization> userOrganizations;  // ← NEW
    
    @OneToMany(mappedBy = "user")
    private List<UserLibrary> userLibraries;  // ← NEW
    
    @OneToMany(mappedBy = "user")
    private List<Role> roles;  // Already exists
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // ... getters/setters
}
```

---

## 6. Required Repositories

```java
// OrganizationRepository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByOrganizationCode(String code);
    List<Organization> findByStatus(String status);
}

// UserOrganizationRepository
public interface UserOrganizationRepository extends JpaRepository<UserOrganization, UserOrganizationKey> {
    Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, Long orgId);
    List<UserOrganization> findByUserId(Long userId);
    Optional<UserOrganization> findByUserIdAndIsPrimary(Long userId, Boolean isPrimary);
}

// UserLibraryRepository
public interface UserLibraryRepository extends JpaRepository<UserLibrary, UserLibraryKey> {
    Optional<UserLibrary> findByUserIdAndLibraryId(Long userId, Long libId);
    List<UserLibrary> findByUserId(Long userId);
    Optional<UserLibrary> findByUserIdAndIsPrimary(Long userId, Boolean isPrimary);
    List<UserLibrary> findByLibraryId(Long libId);
}

// LibraryRepository (enhanced)
public interface LibraryRepository extends JpaRepository<Library, Long> {
    Optional<Library> findByLibraryCodeAndOrganizationId(String code, Long orgId);
    List<Library> findByOrganizationId(Long orgId);
    List<Library> findByOrganizationIdAndStatus(Long orgId, String status);
}

// AddressRepository
public interface AddressRepository extends JpaRepository<Address, Long> {
    // For looking up reusable addresses
}
```

---

## 7. Service Layer Architecture

### OrganizationService
```
Responsibilities:
- Create organization (SUPER_ADMIN or initially restricted)
- Get organization (verify user membership)
- List user's organizations (from user_organization)
- Update organization (verify ownership)
- Deactivate organization (verify ownership)
- Add/remove users to/from organization

Tenant Isolation:
- All operations verify user has access to organization
- Use TenantAuthorizationService.requireOrganizationAccess()
```

### LibraryService
```
Responsibilities:
- Create library in organization (verify org ownership)
- Get library (verify user membership)
- List user's libraries (from user_library)
- Update library (verify org/library access)
- Deactivate library (verify org ownership)
- Get library-specific settings

Tenant Isolation:
- All operations scoped to library_id
- Library operations scoped to organization
- Verify user is member of both org and library
- Use TenantAuthorizationService methods
```

### UserManagementService
```
Responsibilities:
- Add user to organization
- Remove user from organization
- Add user to library (must verify org membership first)
- Remove user from library
- Set primary organization for user
- Set primary library for user

Constraints:
- User must have org membership before library membership
- Cannot add to library outside user's accessible organizations
- Maintain referential integrity
```

---

## 8. Security & Authorization

### Roles Hierarchy

```
SUPER_ADMIN (PLATFORM scope)
├─ Full access to all organizations and libraries
└─ Can manage all users and permissions

ORGANIZATION_OWNER (ORGANIZATION scope)
├─ Can manage their organization
├─ Can create/manage libraries within org
├─ Can manage users within org
└─ Can manage organization settings

ORGANIZATION_ADMIN (ORGANIZATION scope)
├─ Similar to OWNER but without billing/legal access
└─ Can delegate to library managers

LIBRARY_MANAGER (LIBRARY scope)
├─ Can manage their libraries
├─ Can manage students, seats, attendance
└─ Can view reports

LIBRARY_STAFF (LIBRARY scope)
├─ Limited library operations
└─ Can manage students

RECEPTIONIST (LIBRARY scope)
├─ Can register students
├─ Can manage attendance
└─ Can process payments

ACCOUNTANT (LIBRARY scope)
├─ Can view and manage payments
├─ Can manage fees
└─ Can view reports
```

### Permission Checks Per Operation

```
GET /api/organizations
  → Verify user has at least one org membership
  → Return only user's organizations

GET /api/organizations/{orgId}
  → Require ORGANIZATION_VIEW permission
  → requireOrganizationAccess(userId, orgId)

POST /api/organizations
  → Require no restriction (admin only, TBD)

PUT /api/organizations/{orgId}
  → Require ORGANIZATION_UPDATE permission
  → requireOrganizationAccess(userId, orgId)

GET /api/libraries
  → Verify user has at least one library membership
  → Return only user's libraries

GET /api/libraries/{libId}
  → Require LIBRARY_VIEW permission
  → requireLibraryAccess(userId, libId)

POST /api/libraries
  → Require LIBRARY_CREATE permission
  → Library must belong to org user is member of
  → Verify org membership

PUT /api/libraries/{libId}
  → Require LIBRARY_UPDATE permission
  → requireLibraryAccess(userId, libId)

POST /api/libraries/{libId}/users
  → Require USER_CREATE permission
  → Verify user is member of target library's org
  → Verify target user is member of org
```

---

## 9. Required Tests

### Unit Tests (H2, Mocked Repositories)

1. **OrganizationServiceTest**
   - Test organization creation (permission checks)
   - Test organization retrieval (authorization)
   - Test list organizations (filters by user membership)

2. **LibraryServiceTest**
   - Test library creation (verify org membership)
   - Test library retrieval (tenant-scoped)
   - Test library update (authorization checks)

3. **UserManagementServiceTest**
   - Test adding user to organization
   - Test adding user to library (org membership constraint)
   - Test removing users

### Integration Tests (MySQL via Testcontainers)

1. **OrganizationIntegrationTest extends IntegrationTestBase**
   - Test complete org lifecycle with real database
   - Test tenant isolation (user from org1 cannot access org2)
   - Test user-organization relationships
   - Test multi-organization scenarios

2. **LibraryIntegrationTest extends IntegrationTestBase**
   - Test complete library lifecycle
   - Test tenant isolation (cross-org library access denied)
   - Test library constraint validation
   - Test user-library relationships
   - Verify students/seats/attendance scoped correctly

3. **MultiTenancyIntegrationTest extends IntegrationTestBase**
   - Test complete multi-tenancy scenarios
   - Org1/Lib1 user cannot access Org2/Lib2 data
   - Verify library_id present in all queries
   - Test cross-tenant data leakage scenarios

---

## 10. Data Flow Examples

### Creating a Library

```
REQUEST:
POST /api/organizations/{orgId}/libraries
Authorization: Bearer <JWT>
Body: {
  "libraryCode": "LIB001",
  "name": "New Branch",
  "email": "branch@example.com"
}

FLOW:
1. JwtAuthenticationFilter validates JWT → user = alice
2. TenantContext.setLibraryId(alice's primary library)
3. LibraryController.createLibrary(orgId, request)
4. LibraryService.createLibrary(orgId, request):
   a) Get currentUserId = alice
   b) Verify LIBRARY_CREATE permission
   c) Require organization access: requireOrganizationAccess(alice, orgId)
   d) Load organization (verify exists)
   e) Verify user is member of organization
   f) Create new library with organization_id = orgId
   g) Save library
   h) Return LibraryResponse
5. Controller returns 201 Created with library data

TENANT ISOLATION:
- Library.organization_id is set server-side (not from request)
- Alice can only create within organizations she's a member of
- Library tied to that organization, cannot be moved
```

### Adding User to Library

```
REQUEST:
POST /api/organizations/{orgId}/libraries/{libId}/users
Authorization: Bearer <JWT>
Body: {
  "userId": 42,
  "isPrimary": false
}

FLOW:
1. JwtAuthenticationFilter validates JWT → user = alice
2. UserManagementService.addUserToLibrary(orgId, libId, userId, isPrimary):
   a) Get currentUserId = alice
   b) Verify USER_CREATE permission
   c) Require org access: requireOrganizationAccess(alice, orgId)
   d) Require library access: requireLibraryAccess(alice, libId)
   e) Verify library belongs to organization (library.organization_id == orgId)
   f) Load target user (userId = 42)
   g) Verify user 42 is member of organization:
      → requireOrganizationAccess(42, orgId)
   h) Create UserLibrary(user=42, library=libId, isPrimary=isPrimary)
   i) Save relationship
   j) Return success

CONSTRAINTS:
- Cannot add to library if not in org first
- Cross-organization library access prevented
- Audit trail recorded (created_by, created_at)
```

### Querying Libraries for User

```
REQUEST:
GET /api/libraries?organizationId=1
Authorization: Bearer <JWT>

FLOW:
1. JwtAuthenticationFilter validates JWT → user = alice
2. LibraryController.getLibraries(orgId, pageable):
3. LibraryService.listLibrariesForUser(orgId, pageable):
   a) Get currentUserId = alice
   b) If orgId provided:
      → Require organization access: requireOrganizationAccess(alice, orgId)
      → Query: libraries WHERE organization_id = orgId
   c) If no orgId:
      → Query user's organizations
      → For each org, get their libraries
   d) Return paginated results
   e) All returned data is from libraries alice belongs to

RESULTS:
- Only libraries alice is a member of (via user_library)
- Optionally scoped to specific organization
- Cross-tenant data access prevented
```

---

## 11. Implementation Sequence

### Phase 1A: Entities & Repositories (2-3 days)
1. Create Organization entity
2. Create UserOrganization entity
3. Create UserLibrary entity
4. Create Address entity
5. Enhance Library entity with @ManyToOne(Organization)
6. Enhance User entity with relationships
7. Create all repositories
8. Create repository tests

### Phase 1B: Services (2-3 days)
1. Create OrganizationService interface & implementation
2. Create LibraryService interface & implementation
3. Create UserManagementService
4. Add comprehensive validation & error handling
5. Create service-level unit tests

### Phase 1C: Controllers & DTOs (1-2 days)
1. Create all DTOs
2. Create OrganizationController
3. Create LibraryController
4. Add endpoint-level validation
5. Create controller unit tests

### Phase 1D: Integration Tests (2-3 days)
1. Create OrganizationIntegrationTest
2. Create LibraryIntegrationTest
3. Create MultiTenancyIntegrationTest
4. Verify all 23 baseline tests still pass
5. Test cross-tenant data leakage scenarios

---

## 12. Expected Test Results After Phase 1

```
Initial Baseline:
├─ 23 existing tests (unchanged)

New Tests to Add:
├─ OrganizationServiceTest: 8-12 tests
├─ LibraryServiceTest: 8-12 tests
├─ UserManagementServiceTest: 6-8 tests
├─ OrganizationIntegrationTest: 12-15 tests
├─ LibraryIntegrationTest: 15-20 tests
└─ MultiTenancyIntegrationTest: 10-15 tests

Expected Total:
├─ ~23 + 60-90 new tests
├─ All 23 baseline tests still passing
├─ 100% pass rate required
└─ Build time: ~2-3 minutes
```

---

## 13. No Database Schema Changes

### ✅ CONFIRMED: NO MIGRATIONS NEEDED
- All tables already exist (organization, library, user_organization, user_library, address)
- All foreign keys already in place
- All indices already created
- Sample data already loaded
- Role & permission data already defined

**Only JPA entities, repositories, services, controllers, and tests need to be created.**

---

## 14. Risk Assessment

### ✅ Low Risk Items
- Entities follow existing patterns (Student entity as reference)
- Services follow StudentService pattern
- Controllers follow StudentController pattern
- Database already has multi-tenant data

### ⚠️ Medium Risk Items
- UserOrganization.isPrimary constraint (verify during setters)
- Library-Organization referential integrity (prevent cross-org additions)
- Cascade delete behavior (ensure no data loss)

### Risk Mitigation
- Comprehensive integration tests before merge
- Database constraint validation tests
- Cross-tenant data leakage tests
- All 23 baseline tests remain passing

---

## 15. Approval Checklist

**Before Implementation, Confirm:**

- [ ] Database schema, tables, and test data are finalized
- [ ] Roles and permissions are appropriate for Phase 1
- [ ] Tenant isolation model is correct (library_id scope)
- [ ] API endpoints are acceptable
- [ ] Service layer pattern matches StudentService
- [ ] Test coverage expectations are reasonable
- [ ] No breaking changes to existing 23 tests
- [ ] Timeline and resource allocation are feasible

---

## Summary

| Category | Status | Notes |
|---|---|---|
| **Database Schema** | ✅ Complete | No migrations needed |
| **Entities** | ❌ Missing | Need 5 new entities + 2 enhanced |
| **Repositories** | ❌ Missing | Need 4 new + 1 enhanced |
| **Services** | ❌ Missing | Need 3 new services |
| **Controllers** | ❌ Missing | Need 2 new controllers |
| **DTOs** | ❌ Missing | Need 6 new DTOs |
| **Tests** | ❌ Missing | Need 30-50 new tests |
| **Roles/Permissions** | ✅ Complete | Already defined & assigned |
| **Tenant Infrastructure** | ✅ Complete | TenantAuthorizationService ready |
| **Test Data** | ✅ Complete | 2 orgs, 3 libs, 4 users |
| **Breaking Changes** | ✅ None | 23 baseline tests unaffected |

---

**Status**: ✅ **ANALYSIS COMPLETE - READY FOR APPROVAL**

Awaiting approval to proceed with implementation.
