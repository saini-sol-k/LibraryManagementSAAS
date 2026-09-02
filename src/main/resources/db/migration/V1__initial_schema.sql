SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS student_fee;
DROP TABLE IF EXISTS fee_plan;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS student_membership;
DROP TABLE IF EXISTS seat_assignment;
DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS seat_zone;
DROP TABLE IF EXISTS seat_type;
DROP TABLE IF EXISTS student_document;
DROP TABLE IF EXISTS student_emergency_contact;
DROP TABLE IF EXISTS student_address;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS login_history;
DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS user_library;
DROP TABLE IF EXISTS user_organization;
DROP TABLE IF EXISTS role_permission;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS library_address;
DROP TABLE IF EXISTS organization_address;
DROP TABLE IF EXISTS address;
DROP TABLE IF EXISTS library;
DROP TABLE IF EXISTS organization;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. ORGANIZATION
-- A customer/business account on the SaaS platform.
-- ============================================================

CREATE TABLE `organization` (
    organization_id BIGINT NOT NULL AUTO_INCREMENT,
    organization_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    legal_name VARCHAR(250),
    email VARCHAR(150),
    mobile VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (organization_id),
    UNIQUE KEY uk_organization_code (organization_code),
    KEY idx_organization_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. LIBRARY
-- A physical library/branch belonging to an organization.
-- ============================================================

CREATE TABLE library (
    library_id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,

    library_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    email VARCHAR(150),
    mobile VARCHAR(30),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    opening_time TIME,
    closing_time TIME,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (library_id),

    CONSTRAINT fk_library_organization
        FOREIGN KEY (organization_id)
        REFERENCES `organization`(organization_id),

    UNIQUE KEY uk_library_org_code
        (organization_id, library_code),

    KEY idx_library_organization (organization_id),
    KEY idx_library_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. ADDRESS
-- Reusable address model.
-- first_name and last_name are intentionally included.
-- The same address_id may be referenced by multiple students.
-- ============================================================

CREATE TABLE address (
    address_id BIGINT NOT NULL AUTO_INCREMENT,

    first_name VARCHAR(100),
    last_name VARCHAR(100),

    address_line1 VARCHAR(250) NOT NULL,
    address_line2 VARCHAR(250),
    address_line3 VARCHAR(250),

    landmark VARCHAR(200),
    city VARCHAR(100),
    district VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),

    phone1 VARCHAR(30),
    phone2 VARCHAR(30),
    email VARCHAR(150),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,

    PRIMARY KEY (address_id),

    KEY idx_address_postal_code (postal_code),
    KEY idx_address_city (city),
    KEY idx_address_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. ORGANIZATION ADDRESS
-- ============================================================

CREATE TABLE organization_address (
    organization_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,

    address_type VARCHAR(30) NOT NULL DEFAULT 'BUSINESS',
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (organization_id, address_id, address_type),

    CONSTRAINT fk_org_address_org
        FOREIGN KEY (organization_id)
        REFERENCES `organization`(organization_id),

    CONSTRAINT fk_org_address_address
        FOREIGN KEY (address_id)
        REFERENCES address(address_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. LIBRARY ADDRESS
-- ============================================================

CREATE TABLE library_address (
    library_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,

    address_type VARCHAR(30) NOT NULL DEFAULT 'BUSINESS',
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (library_id, address_id, address_type),

    CONSTRAINT fk_library_address_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_library_address_address
        FOREIGN KEY (address_id)
        REFERENCES address(address_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. USERS
-- ============================================================

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,

    username VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    mobile VARCHAR(30),

    password_hash VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mobile_verified BOOLEAN NOT NULL DEFAULT FALSE,

    last_login_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (user_id),

    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),

    KEY idx_users_mobile (mobile),
    KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. ROLES
-- ============================================================

CREATE TABLE roles (
    role_id BIGINT NOT NULL AUTO_INCREMENT,

    role_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(250),

    scope VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,

    PRIMARY KEY (role_id),

    UNIQUE KEY uk_roles_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. PERMISSIONS
-- ============================================================

CREATE TABLE permissions (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,

    permission_code VARCHAR(100) NOT NULL,
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(250),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (permission_id),

    UNIQUE KEY uk_permissions_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 9. USER ROLE
-- Relationship table as requested.
-- ============================================================

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id)
        REFERENCES roles(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 10. ROLE PERMISSION
-- ============================================================

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id)
        REFERENCES roles(role_id),

    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id)
        REFERENCES permissions(permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 11. USER ORGANIZATION
-- A user can belong to one or more organizations.
-- ============================================================

CREATE TABLE user_organization (
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, organization_id),

    CONSTRAINT fk_user_org_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_user_org_organization
        FOREIGN KEY (organization_id)
        REFERENCES `organization`(organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 12. USER LIBRARY
-- Allows managers/staff to have access to specific branches.
-- ============================================================

CREATE TABLE user_library (
    user_id BIGINT NOT NULL,
    library_id BIGINT NOT NULL,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, library_id),

    CONSTRAINT fk_user_library_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_user_library_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 13. REFRESH TOKEN
-- ============================================================

CREATE TABLE refresh_token (
    refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,

    user_id BIGINT NOT NULL,

    token_hash VARCHAR(500) NOT NULL,

    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (refresh_token_id),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    KEY idx_refresh_token_user (user_id),
    KEY idx_refresh_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 14. LOGIN HISTORY
-- ============================================================

CREATE TABLE login_history (
    login_history_id BIGINT NOT NULL AUTO_INCREMENT,

    user_id BIGINT NOT NULL,

    login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ip_address VARCHAR(50),
    user_agent VARCHAR(500),

    success BOOLEAN NOT NULL,

    failure_reason VARCHAR(250),

    PRIMARY KEY (login_history_id),

    CONSTRAINT fk_login_history_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    KEY idx_login_history_user (user_id),
    KEY idx_login_history_date (login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 15. STUDENT
-- Student belongs to a specific library.
-- student_code is unique only within that library.
-- ============================================================

CREATE TABLE student (
    student_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,

    student_code VARCHAR(50) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),

    mobile VARCHAR(30),
    email VARCHAR(150),

    date_of_birth DATE,
    gender VARCHAR(30),

    joining_date DATE NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (student_id),

    CONSTRAINT fk_student_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    UNIQUE KEY uk_student_library_code
        (library_id, student_code),

    KEY idx_student_library (library_id),
    KEY idx_student_mobile (mobile),
    KEY idx_student_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 16. STUDENT ADDRESS
-- Same address can be reused by multiple students.
-- ============================================================

CREATE TABLE student_address (
    student_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,

    address_type VARCHAR(30) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (student_id, address_id, address_type),

    CONSTRAINT fk_student_address_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    CONSTRAINT fk_student_address_address
        FOREIGN KEY (address_id)
        REFERENCES address(address_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 17. STUDENT EMERGENCY CONTACT
-- ============================================================

CREATE TABLE student_emergency_contact (
    emergency_contact_id BIGINT NOT NULL AUTO_INCREMENT,

    student_id BIGINT NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),

    relationship VARCHAR(50),

    mobile VARCHAR(30),
    email VARCHAR(150),

    address_id BIGINT,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (emergency_contact_id),

    CONSTRAINT fk_emergency_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    CONSTRAINT fk_emergency_address
        FOREIGN KEY (address_id)
        REFERENCES address(address_id),

    KEY idx_emergency_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 18. STUDENT DOCUMENT
-- ============================================================

CREATE TABLE student_document (
    document_id BIGINT NOT NULL AUTO_INCREMENT,

    student_id BIGINT NOT NULL,

    document_type VARCHAR(50) NOT NULL,
    document_number VARCHAR(100),

    document_url VARCHAR(500),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,

    PRIMARY KEY (document_id),

    CONSTRAINT fk_student_document_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    KEY idx_student_document_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 19. SEAT TYPE
-- ============================================================

CREATE TABLE seat_type (
    seat_type_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,

    name VARCHAR(100) NOT NULL,
    description VARCHAR(250),

    price DECIMAL(12,2) NOT NULL DEFAULT 0.00,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,

    PRIMARY KEY (seat_type_id),

    CONSTRAINT fk_seat_type_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    UNIQUE KEY uk_seat_type_library_name
        (library_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 20. SEAT ZONE
-- ============================================================

CREATE TABLE seat_zone (
    zone_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,

    name VARCHAR(100) NOT NULL,
    floor VARCHAR(50),
    description VARCHAR(250),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (zone_id),

    CONSTRAINT fk_seat_zone_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    UNIQUE KEY uk_seat_zone_library_name
        (library_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 21. SEAT
-- ============================================================

CREATE TABLE seat (
    seat_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    zone_id BIGINT,
    seat_type_id BIGINT,

    seat_number VARCHAR(50) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (seat_id),

    CONSTRAINT fk_seat_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_seat_zone
        FOREIGN KEY (zone_id)
        REFERENCES seat_zone(zone_id),

    CONSTRAINT fk_seat_type
        FOREIGN KEY (seat_type_id)
        REFERENCES seat_type(seat_type_id),

    UNIQUE KEY uk_seat_library_number
        (library_id, seat_number),

    KEY idx_seat_library_status
        (library_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 22. SEAT ASSIGNMENT
-- Historical assignments are preserved.
-- Service layer must allow only one active assignment per
-- student and one active student per seat.
-- ============================================================

CREATE TABLE seat_assignment (
    assignment_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,

    PRIMARY KEY (assignment_id),

    CONSTRAINT fk_assignment_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_assignment_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    CONSTRAINT fk_assignment_seat
        FOREIGN KEY (seat_id)
        REFERENCES seat(seat_id),

    KEY idx_assignment_student (student_id),
    KEY idx_assignment_seat (seat_id),
    KEY idx_assignment_library_status (library_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 23. STUDENT MEMBERSHIP
-- ============================================================

CREATE TABLE student_membership (
    membership_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,

    membership_number VARCHAR(50) NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (membership_id),

    CONSTRAINT fk_membership_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_membership_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    UNIQUE KEY uk_membership_library_number
        (library_id, membership_number),

    KEY idx_membership_student (student_id),
    KEY idx_membership_expiry (library_id, end_date),
    KEY idx_membership_status (library_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 24. ATTENDANCE
-- ============================================================

CREATE TABLE attendance (
    attendance_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    seat_id BIGINT,

    attendance_date DATE NOT NULL,

    check_in_time TIMESTAMP NOT NULL,
    check_out_time TIMESTAMP NULL,

    duration_minutes INT,

    status VARCHAR(30) NOT NULL DEFAULT 'PRESENT',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (attendance_id),

    CONSTRAINT fk_attendance_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_attendance_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    CONSTRAINT fk_attendance_seat
        FOREIGN KEY (seat_id)
        REFERENCES seat(seat_id),

    KEY idx_attendance_library_date
        (library_id, attendance_date),

    KEY idx_attendance_student_date
        (student_id, attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 25. FEE PLAN
-- ============================================================

CREATE TABLE fee_plan (
    fee_plan_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,

    name VARCHAR(100) NOT NULL,
    description VARCHAR(250),

    amount DECIMAL(12,2) NOT NULL,

    duration_value INT NOT NULL,
    duration_unit VARCHAR(20) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,

    PRIMARY KEY (fee_plan_id),

    CONSTRAINT fk_fee_plan_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    UNIQUE KEY uk_fee_plan_library_name
        (library_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 26. STUDENT FEE
-- ============================================================

CREATE TABLE student_fee (
    student_fee_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    membership_id BIGINT,
    fee_plan_id BIGINT,

    invoice_number VARCHAR(50) NOT NULL,

    amount DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL,

    due_date DATE NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,

    PRIMARY KEY (student_fee_id),

    CONSTRAINT fk_student_fee_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_student_fee_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    CONSTRAINT fk_student_fee_membership
        FOREIGN KEY (membership_id)
        REFERENCES student_membership(membership_id),

    CONSTRAINT fk_student_fee_plan
        FOREIGN KEY (fee_plan_id)
        REFERENCES fee_plan(fee_plan_id),

    UNIQUE KEY uk_student_fee_invoice
        (library_id, invoice_number),

    KEY idx_student_fee_student (student_id),
    KEY idx_student_fee_due (library_id, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 27. PAYMENT
-- Student -> Library payment.
-- This is deliberately separate from future SaaS subscription
-- billing between Library/Organization -> SaaS platform.
-- ============================================================

CREATE TABLE payment (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,

    library_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    student_fee_id BIGINT,

    receipt_number VARCHAR(50) NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    payment_method VARCHAR(30) NOT NULL,

    transaction_reference VARCHAR(150),

    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,

    PRIMARY KEY (payment_id),

    CONSTRAINT fk_payment_library
        FOREIGN KEY (library_id)
        REFERENCES library(library_id),

    CONSTRAINT fk_payment_student
        FOREIGN KEY (student_id)
        REFERENCES student(student_id),

    CONSTRAINT fk_payment_student_fee
        FOREIGN KEY (student_fee_id)
        REFERENCES student_fee(student_fee_id),

    UNIQUE KEY uk_payment_receipt
        (library_id, receipt_number),

    KEY idx_payment_library_date
        (library_id, payment_date),

    KEY idx_payment_student (student_id),

    KEY idx_payment_transaction
        (transaction_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SAMPLE DATA
-- ============================================================

-- BCrypt hash for Password@123:
-- $2a$10$X1jhothXcJO/JjRATEJX8e2WiX3N86FFe7PTRTwMFy8jVzs8H/hv.

-- ============================================================
-- ORGANIZATIONS
-- ============================================================

INSERT INTO organization
(organization_id, organization_code, name, legal_name, email, mobile, status)
VALUES
(1, 'ORG001', 'Bright Future Education',
 'Bright Future Education Pvt Ltd',
 'admin@brightfuture.example', '9876500001', 'ACTIVE'),

(2, 'ORG002', 'Knowledge Hub Group',
 'Knowledge Hub Group Pvt Ltd',
 'admin@knowledgehub.example', '9876500002', 'ACTIVE');

-- ============================================================
-- ADDRESSES
-- ============================================================

INSERT INTO address
(address_id, first_name, last_name, address_line1, address_line2,
 landmark, city, district, state, country, postal_code,
 phone1, email)
VALUES
(1, 'Bright', 'Future', 'Main Market Road', 'Near City Park',
 'City Park', 'Saharanpur', 'Saharanpur', 'Uttar Pradesh',
 'India', '247001', '9876500001',
 'admin@brightfuture.example'),

(2, 'Bright', 'Future', 'Station Road', 'Second Floor',
 'Near Railway Station', 'Dehradun', 'Dehradun', 'Uttarakhand',
 'India', '248001', '9876500011',
 'dehradun@brightfuture.example'),

(3, 'Knowledge', 'Hub', 'College Road', 'Block A',
 'Near University', 'Meerut', 'Meerut', 'Uttar Pradesh',
 'India', '250001', '9876500002',
 'admin@knowledgehub.example'),

(4, 'Rahul', 'Sharma', 'House 101', 'Green Park',
 'Near School', 'Saharanpur', 'Saharanpur', 'Uttar Pradesh',
 'India', '247001', '9876511111',
 'rahul@example.com'),

(5, 'Priya', 'Verma', 'House 202', 'Model Town',
 'Near Market', 'Saharanpur', 'Saharanpur', 'Uttar Pradesh',
 'India', '247001', '9876522222',
 'priya@example.com'),

(6, 'Amit', 'Sharma', 'House 303', 'Civil Lines',
 'Near Hospital', 'Meerut', 'Meerut', 'Uttar Pradesh',
 'India', '250001', '9876533333',
 'amit@example.com');

-- ============================================================
-- ORGANIZATION ADDRESS
-- ============================================================

INSERT INTO organization_address
(organization_id, address_id, address_type, is_primary)
VALUES
(1, 1, 'BUSINESS', TRUE),
(2, 3, 'BUSINESS', TRUE);

-- ============================================================
-- LIBRARIES
-- Same library_code can exist under different organizations.
-- ============================================================

INSERT INTO library
(library_id, organization_id, library_code, name, description,
 email, mobile, status, opening_time, closing_time)
VALUES
(1, 1, 'LIB001', 'Bright Future Saharanpur',
 'Main study center',
 'saharanpur@brightfuture.example', '9876500010',
 'ACTIVE', '06:00:00', '22:00:00'),

(2, 1, 'LIB002', 'Bright Future Dehradun',
 'Second branch',
 'dehradun@brightfuture.example', '9876500011',
 'ACTIVE', '06:00:00', '22:00:00'),

(3, 2, 'LIB001', 'Knowledge Hub Meerut',
 'Main study center',
 'meerut@knowledgehub.example', '9876500020',
 'ACTIVE', '07:00:00', '23:00:00');

-- ============================================================
-- LIBRARY ADDRESS
-- ============================================================

INSERT INTO library_address
(library_id, address_id, address_type, is_primary)
VALUES
(1, 1, 'BUSINESS', TRUE),
(2, 2, 'BUSINESS', TRUE),
(3, 3, 'BUSINESS', TRUE);

-- ============================================================
-- USERS
-- ============================================================

INSERT INTO users
(user_id, username, email, mobile, password_hash,
 first_name, last_name, status, email_verified, mobile_verified)
VALUES
(1, 'superadmin', 'superadmin@example.com', '9000000001',
 '$2a$10$X1jhothXcJO/JjRATEJX8e2WiX3N86FFe7PTRTwMFy8jVzs8H/hv.', 'Super', 'Admin', 'ACTIVE', TRUE, TRUE),

(2, 'owner1', 'owner1@brightfuture.example', '9000000002',
 '$2a$10$X1jhothXcJO/JjRATEJX8e2WiX3N86FFe7PTRTwMFy8jVzs8H/hv.', 'Raj', 'Owner', 'ACTIVE', TRUE, TRUE),

(3, 'manager1', 'manager1@brightfuture.example', '9000000003',
 '$2a$10$X1jhothXcJO/JjRATEJX8e2WiX3N86FFe7PTRTwMFy8jVzs8H/hv.', 'Amit', 'Manager', 'ACTIVE', TRUE, TRUE),

(4, 'reception1', 'reception1@brightfuture.example', '9000000004',
 '$2a$10$X1jhothXcJO/JjRATEJX8e2WiX3N86FFe7PTRTwMFy8jVzs8H/hv.', 'Neha', 'Reception', 'ACTIVE', TRUE, TRUE);

-- ============================================================
-- ROLES
-- ============================================================

INSERT INTO roles
(role_id, role_code, name, description, scope)
VALUES
(1, 'SUPER_ADMIN', 'Super Admin',
 'Platform administrator with access to all organizations and libraries',
 'PLATFORM'),

(2, 'ORGANIZATION_OWNER', 'Organization Owner',
 'Owner of a customer organization',
 'ORGANIZATION'),

(3, 'ORGANIZATION_ADMIN', 'Organization Admin',
 'Organization administrator',
 'ORGANIZATION'),

(4, 'LIBRARY_MANAGER', 'Library Manager',
 'Manager of one or more library branches',
 'LIBRARY'),

(5, 'LIBRARY_STAFF', 'Library Staff',
 'General library staff',
 'LIBRARY'),

(6, 'RECEPTIONIST', 'Receptionist',
 'Handles registration, attendance and payments',
 'LIBRARY'),

(7, 'ACCOUNTANT', 'Accountant',
 'Handles financial operations',
 'LIBRARY');

-- ============================================================
-- PERMISSIONS
-- ============================================================

INSERT INTO permissions
(permission_id, permission_code, module, action, description)
VALUES
(1, 'ORGANIZATION_VIEW', 'ORGANIZATION', 'VIEW', 'View organization'),
(2, 'ORGANIZATION_UPDATE', 'ORGANIZATION', 'UPDATE', 'Update organization'),

(3, 'LIBRARY_VIEW', 'LIBRARY', 'VIEW', 'View library'),
(4, 'LIBRARY_CREATE', 'LIBRARY', 'CREATE', 'Create library'),
(5, 'LIBRARY_UPDATE', 'LIBRARY', 'UPDATE', 'Update library'),
(6, 'LIBRARY_STATUS_UPDATE', 'LIBRARY', 'STATUS_UPDATE',
 'Activate/deactivate/suspend library'),

(7, 'STUDENT_VIEW', 'STUDENT', 'VIEW', 'View students'),
(8, 'STUDENT_CREATE', 'STUDENT', 'CREATE', 'Create student'),
(9, 'STUDENT_UPDATE', 'STUDENT', 'UPDATE', 'Update student'),
(10, 'STUDENT_DELETE', 'STUDENT', 'DELETE',
 'Deactivate/delete student'),

(11, 'SEAT_VIEW', 'SEAT', 'VIEW', 'View seats'),
(12, 'SEAT_CREATE', 'SEAT', 'CREATE', 'Create seats'),
(13, 'SEAT_UPDATE', 'SEAT', 'UPDATE', 'Update seats'),
(14, 'SEAT_ASSIGN', 'SEAT', 'ASSIGN', 'Assign seat'),

(15, 'ATTENDANCE_VIEW', 'ATTENDANCE', 'VIEW', 'View attendance'),
(16, 'ATTENDANCE_CREATE', 'ATTENDANCE', 'CREATE', 'Create attendance'),

(17, 'PAYMENT_VIEW', 'PAYMENT', 'VIEW', 'View payments'),
(18, 'PAYMENT_CREATE', 'PAYMENT', 'CREATE', 'Create payment'),
(19, 'PAYMENT_REFUND', 'PAYMENT', 'REFUND', 'Refund payment'),

(20, 'FEE_PLAN_VIEW', 'FEE_PLAN', 'VIEW', 'View fee plans'),
(21, 'FEE_PLAN_CREATE', 'FEE_PLAN', 'CREATE', 'Create fee plans'),

(22, 'REPORT_VIEW', 'REPORT', 'VIEW', 'View reports'),

(23, 'USER_VIEW', 'USER', 'VIEW', 'View users'),
(24, 'USER_CREATE', 'USER', 'CREATE', 'Create users'),
(25, 'USER_UPDATE', 'USER', 'UPDATE', 'Update users');

-- ============================================================
-- USER ROLE
-- ============================================================

INSERT INTO user_role (user_id, role_id)
VALUES
(1, 1),
(2, 2),
(3, 4),
(4, 6);

-- ============================================================
-- ROLE PERMISSION
-- ============================================================

-- Super Admin gets every current permission.
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, permission_id
FROM permissions;

-- Organization Owner
INSERT INTO role_permission (role_id, permission_id)
SELECT 2, permission_id
FROM permissions
WHERE permission_code IN (
    'ORGANIZATION_VIEW',
    'ORGANIZATION_UPDATE',
    'LIBRARY_VIEW',
    'LIBRARY_CREATE',
    'LIBRARY_UPDATE',
    'LIBRARY_STATUS_UPDATE',
    'STUDENT_VIEW',
    'STUDENT_CREATE',
    'STUDENT_UPDATE',
    'STUDENT_DELETE',
    'SEAT_VIEW',
    'SEAT_CREATE',
    'SEAT_UPDATE',
    'SEAT_ASSIGN',
    'ATTENDANCE_VIEW',
    'ATTENDANCE_CREATE',
    'PAYMENT_VIEW',
    'PAYMENT_CREATE',
    'PAYMENT_REFUND',
    'FEE_PLAN_VIEW',
    'FEE_PLAN_CREATE',
    'REPORT_VIEW',
    'USER_VIEW',
    'USER_CREATE',
    'USER_UPDATE'
);

-- Library Manager
INSERT INTO role_permission (role_id, permission_id)
SELECT 4, permission_id
FROM permissions
WHERE permission_code IN (
    'LIBRARY_VIEW',
    'STUDENT_VIEW',
    'STUDENT_CREATE',
    'STUDENT_UPDATE',
    'SEAT_VIEW',
    'SEAT_CREATE',
    'SEAT_UPDATE',
    'SEAT_ASSIGN',
    'ATTENDANCE_VIEW',
    'ATTENDANCE_CREATE',
    'PAYMENT_VIEW',
    'PAYMENT_CREATE',
    'FEE_PLAN_VIEW',
    'FEE_PLAN_CREATE',
    'REPORT_VIEW'
);

-- Receptionist
INSERT INTO role_permission (role_id, permission_id)
SELECT 6, permission_id
FROM permissions
WHERE permission_code IN (
    'LIBRARY_VIEW',
    'STUDENT_VIEW',
    'STUDENT_CREATE',
    'STUDENT_UPDATE',
    'SEAT_VIEW',
    'SEAT_ASSIGN',
    'ATTENDANCE_VIEW',
    'ATTENDANCE_CREATE',
    'PAYMENT_VIEW',
    'PAYMENT_CREATE'
);

-- Accountant
INSERT INTO role_permission (role_id, permission_id)
SELECT 7, permission_id
FROM permissions
WHERE permission_code IN (
    'LIBRARY_VIEW',
    'PAYMENT_VIEW',
    'PAYMENT_CREATE',
    'PAYMENT_REFUND',
    'FEE_PLAN_VIEW',
    'FEE_PLAN_CREATE',
    'REPORT_VIEW'
);

-- ============================================================
-- USER ORGANIZATION
-- ============================================================

INSERT INTO user_organization
(user_id, organization_id, is_primary, status)
VALUES
(1, 1, TRUE, 'ACTIVE'),
(1, 2, FALSE, 'ACTIVE'),
(2, 1, TRUE, 'ACTIVE'),
(2, 2, FALSE, 'ACTIVE'),
(3, 1, TRUE, 'ACTIVE'),
(4, 1, TRUE, 'ACTIVE');

-- ============================================================
-- USER LIBRARY
-- ============================================================

INSERT INTO user_library
(user_id, library_id, is_primary, status)
VALUES
(1, 1, TRUE, 'ACTIVE'),
(1, 2, FALSE, 'ACTIVE'),
(1, 3, FALSE, 'ACTIVE'),
(2, 1, TRUE, 'ACTIVE'),
(2, 2, FALSE, 'ACTIVE'),
(3, 1, TRUE, 'ACTIVE'),
(4, 1, TRUE, 'ACTIVE'),
(4, 2, FALSE, 'ACTIVE');

-- ============================================================
-- STUDENTS
-- ============================================================

INSERT INTO student
(student_id, library_id, student_code, first_name, last_name,
 mobile, email, date_of_birth, gender, joining_date, status)
VALUES
(1, 1, 'STU001', 'Rahul', 'Sharma',
 '9876511111', 'rahul@example.com',
 '2004-05-12', 'MALE', '2026-01-10', 'ACTIVE'),

(2, 1, 'STU002', 'Priya', 'Verma',
 '9876522222', 'priya@example.com',
 '2003-11-20', 'FEMALE', '2026-02-01', 'ACTIVE'),

(3, 1, 'STU003', 'Suresh', 'Kumar',
 '9876544444', 'suresh@example.com',
 '2002-08-15', 'MALE', '2026-02-15', 'ACTIVE'),

(4, 2, 'STU001', 'Ankit', 'Singh',
 '9876555555', 'ankit@example.com',
 '2004-03-10', 'MALE', '2026-03-01', 'ACTIVE'),

(5, 3, 'STU001', 'Amit', 'Sharma',
 '9876533333', 'amit@example.com',
 '2003-06-25', 'MALE', '2026-01-15', 'ACTIVE');

-- ============================================================
-- STUDENT ADDRESS
-- Address ID 4 is deliberately reused by students 1 and 3.
-- ============================================================

INSERT INTO student_address
(student_id, address_id, address_type, is_primary)
VALUES
(1, 4, 'HOME', TRUE),
(2, 5, 'HOME', TRUE),
(3, 4, 'HOME', TRUE),
(4, 2, 'HOME', TRUE),
(5, 6, 'HOME', TRUE);

-- ============================================================
-- EMERGENCY CONTACTS
-- ============================================================

INSERT INTO student_emergency_contact
(student_id, first_name, last_name, relationship,
 mobile, email, address_id, is_primary)
VALUES
(1, 'Ramesh', 'Sharma', 'FATHER',
 '9876599991', 'ramesh@example.com', 4, TRUE),

(2, 'Sunita', 'Verma', 'MOTHER',
 '9876599992', 'sunita@example.com', 5, TRUE),

(3, 'Mahesh', 'Kumar', 'FATHER',
 '9876599993', 'mahesh@example.com', 4, TRUE),

(4, 'Raj', 'Singh', 'BROTHER',
 '9876599994', 'raj@example.com', 2, TRUE),

(5, 'Vijay', 'Sharma', 'FATHER',
 '9876599995', 'vijay@example.com', 6, TRUE);

-- ============================================================
-- STUDENT DOCUMENTS
-- ============================================================

INSERT INTO student_document
(student_id, document_type, document_number, document_url)
VALUES
(1, 'AADHAAR', 'XXXX-XXXX-1001', 'students/1/aadhaar.pdf'),
(2, 'AADHAAR', 'XXXX-XXXX-1002', 'students/2/aadhaar.pdf'),
(3, 'PAN', 'ABCDE1001F', 'students/3/pan.pdf');

-- ============================================================
-- SEAT TYPES
-- ============================================================

INSERT INTO seat_type
(seat_type_id, library_id, name, description, price, status)
VALUES
(1, 1, 'STANDARD', 'Standard study seat', 1500.00, 'ACTIVE'),
(2, 1, 'PREMIUM', 'Premium study seat', 2000.00, 'ACTIVE'),
(3, 2, 'STANDARD', 'Standard study seat', 1400.00, 'ACTIVE'),
(4, 3, 'STANDARD', 'Standard study seat', 1600.00, 'ACTIVE');

-- ============================================================
-- SEAT ZONES
-- ============================================================

INSERT INTO seat_zone
(zone_id, library_id, name, floor, description, status)
VALUES
(1, 1, 'GROUND', 'Ground Floor',
 'Main study area', 'ACTIVE'),

(2, 1, 'FIRST', 'First Floor',
 'Quiet study area', 'ACTIVE'),

(3, 2, 'GROUND', 'Ground Floor',
 'Main study area', 'ACTIVE'),

(4, 3, 'GROUND', 'Ground Floor',
 'Main study area', 'ACTIVE');

-- ============================================================
-- SEATS
-- ============================================================

INSERT INTO seat
(seat_id, library_id, zone_id, seat_type_id, seat_number, status)
VALUES
(1, 1, 1, 1, 'A001', 'OCCUPIED'),
(2, 1, 1, 1, 'A002', 'OCCUPIED'),
(3, 1, 1, 2, 'A003', 'AVAILABLE'),
(4, 1, 2, 2, 'B001', 'AVAILABLE'),
(5, 1, 2, 2, 'B002', 'MAINTENANCE'),
(6, 2, 3, 3, 'A001', 'AVAILABLE'),
(7, 2, 3, 3, 'A002', 'AVAILABLE'),
(8, 3, 4, 4, 'A001', 'AVAILABLE');

-- ============================================================
-- SEAT ASSIGNMENTS
-- ============================================================

INSERT INTO seat_assignment
(assignment_id, library_id, student_id, seat_id, start_date, status)
VALUES
(1, 1, 1, 1, '2026-01-10', 'ACTIVE'),
(2, 1, 2, 2, '2026-02-01', 'ACTIVE');

-- ============================================================
-- MEMBERSHIPS
-- ============================================================

INSERT INTO student_membership
(membership_id, library_id, student_id, membership_number,
 start_date, end_date, status, auto_renew)
VALUES
(1, 1, 1, 'MEM001',
 '2026-01-10', '2026-12-31', 'ACTIVE', TRUE),

(2, 1, 2, 'MEM002',
 '2026-02-01', '2026-12-31', 'ACTIVE', FALSE),

(3, 1, 3, 'MEM003',
 '2026-02-15', '2026-08-31', 'ACTIVE', FALSE),

(4, 2, 4, 'MEM001',
 '2026-03-01', '2026-12-31', 'ACTIVE', FALSE),

(5, 3, 5, 'MEM001',
 '2026-01-15', '2026-12-31', 'ACTIVE', FALSE);

-- ============================================================
-- ATTENDANCE
-- ============================================================

INSERT INTO attendance
(attendance_id, library_id, student_id, seat_id,
 attendance_date, check_in_time, check_out_time,
 duration_minutes, status)
VALUES
(1, 1, 1, 1,
 CURRENT_DATE,
 CURRENT_TIMESTAMP - INTERVAL 4 HOUR,
 CURRENT_TIMESTAMP - INTERVAL 1 HOUR,
 180, 'COMPLETED'),

(2, 1, 2, 2,
 CURRENT_DATE,
 CURRENT_TIMESTAMP - INTERVAL 2 HOUR,
 NULL,
 NULL, 'PRESENT');

-- ============================================================
-- FEE PLANS
-- ============================================================

INSERT INTO fee_plan
(fee_plan_id, library_id, name, description, amount,
 duration_value, duration_unit, status)
VALUES
(1, 1, 'MONTHLY STANDARD',
 'Monthly standard membership',
 1500.00, 1, 'MONTH', 'ACTIVE'),

(2, 1, 'MONTHLY PREMIUM',
 'Monthly premium membership',
 2000.00, 1, 'MONTH', 'ACTIVE'),

(3, 2, 'MONTHLY STANDARD',
 'Monthly standard membership',
 1400.00, 1, 'MONTH', 'ACTIVE'),

(4, 3, 'MONTHLY STANDARD',
 'Monthly standard membership',
 1600.00, 1, 'MONTH', 'ACTIVE');

-- ============================================================
-- STUDENT FEES
-- ============================================================

INSERT INTO student_fee
(student_fee_id, library_id, student_id, membership_id,
 fee_plan_id, invoice_number, amount, discount_amount,
 tax_amount, total_amount, due_date, status)
VALUES
(1, 1, 1, 1, 1,
 'INV001', 1500.00, 0.00, 0.00, 1500.00,
 '2026-09-05', 'PAID'),

(2, 1, 2, 2, 1,
 'INV002', 1500.00, 100.00, 0.00, 1400.00,
 '2026-09-05', 'PARTIALLY_PAID'),

(3, 1, 3, 3, 1,
 'INV003', 1500.00, 0.00, 0.00, 1500.00,
 '2026-08-31', 'PENDING');

-- ============================================================
-- PAYMENTS
-- ============================================================

INSERT INTO payment
(payment_id, library_id, student_id, student_fee_id,
 receipt_number, amount, payment_method,
 transaction_reference, payment_date, status)
VALUES
(1, 1, 1, 1,
 'REC001', 1500.00, 'UPI', 'UPI-DEMO-10001',
 CURRENT_TIMESTAMP - INTERVAL 5 DAY, 'SUCCESS'),

(2, 1, 2, 2,
 'REC002', 700.00, 'CASH', NULL,
 CURRENT_TIMESTAMP - INTERVAL 2 DAY, 'SUCCESS');

-- ============================================================
-- VERIFICATION
-- ============================================================

SELECT 'organization' AS table_name, COUNT(*) AS total FROM `organization`
UNION ALL SELECT 'library', COUNT(*) FROM library
UNION ALL SELECT 'address', COUNT(*) FROM address
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'roles', COUNT(*) FROM roles
UNION ALL SELECT 'permissions', COUNT(*) FROM permissions
UNION ALL SELECT 'student', COUNT(*) FROM student
UNION ALL SELECT 'seat', COUNT(*) FROM seat
UNION ALL SELECT 'student_membership', COUNT(*) FROM student_membership
UNION ALL SELECT 'attendance', COUNT(*) FROM attendance
UNION ALL SELECT 'fee_plan', COUNT(*) FROM fee_plan
UNION ALL SELECT 'student_fee', COUNT(*) FROM student_fee
UNION ALL SELECT 'payment', COUNT(*) FROM payment;

-- ============================================================
-- MULTI-TENANT TEST
-- Same student_code STU001 exists in different libraries.
-- This should return 3 rows.
-- ============================================================

SELECT
    l.library_id,
    o.organization_code,
    l.library_code,
    l.name AS library_name,
    s.student_code,
    s.first_name,
    s.last_name
FROM student s
JOIN library l ON l.library_id = s.library_id
JOIN `organization` o ON o.organization_id = l.organization_id
WHERE s.student_code = 'STU001'
ORDER BY o.organization_id, l.library_id;

-- ============================================================
-- END OF DATABASE SETUP
-- ============================================================

-- SAMPLE LOGIN ACCOUNTS
-- ============================================================
-- superadmin@example.com / Password@123
-- owner1@brightfuture.example / Password@123
-- manager1@brightfuture.example / Password@123
-- reception1@brightfuture.example / Password@123
--
-- IMPORTANT:
-- These credentials are ONLY for local development/testing.
-- Change/remove them before any real production deployment.
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;