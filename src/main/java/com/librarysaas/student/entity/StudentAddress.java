package com.librarysaas.student.entity;

import com.librarysaas.organization.entity.Address;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Links an student to a reusable {@link Address}. The address row itself is
 * owner-agnostic; the relationship - its type and whether it is the primary one
 * - is held here, exactly as the schema models it.
 */
@Entity
@Table(name = "student_address")
public class StudentAddress {

    @EmbeddedId
    private StudentAddressKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("addressId")
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public StudentAddress() {}

    public StudentAddress(Long studentId, Long addressId, String addressType) {
        this.id = new StudentAddressKey(studentId, addressId, addressType);
    }

    public StudentAddressKey getId() {
        return id;
    }

    public void setId(StudentAddressKey id) {
        this.id = id;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
