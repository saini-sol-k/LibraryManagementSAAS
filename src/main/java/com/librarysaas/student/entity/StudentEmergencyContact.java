package com.librarysaas.student.entity;

import com.librarysaas.organization.entity.Address;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Someone to contact about a student, such as a parent or sibling.
 *
 * The address is a direct nullable foreign key, unlike student_address which
 * links through a typed join table. Because {@code address} is a globally scoped
 * table with no tenant column, this module never accepts an address id from a
 * caller: the row is created and updated inline with the contact, so a contact
 * can only ever point at an address this service made for it. Accepting an
 * arbitrary id would let one tenant attach another tenant's address.
 *
 * The schema has no unique constraint on {@code isPrimary}, so the
 * one-primary-per-student rule is enforced by the service. There is no status
 * column and no created_by / updated_by, so none is invented here.
 */
@Entity
@Table(name = "student_emergency_contact")
public class StudentEmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emergency_contact_id")
    private Long emergencyContactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "relationship", length = 50)
    private String relationship;

    @Column(name = "mobile", length = 30)
    private String mobile;

    @Column(name = "email", length = 150)
    private String email;

    /** Created and maintained inline; never taken from a caller-supplied id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getEmergencyContactId() { return emergencyContactId; }
    public void setEmergencyContactId(Long emergencyContactId) { this.emergencyContactId = emergencyContactId; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
