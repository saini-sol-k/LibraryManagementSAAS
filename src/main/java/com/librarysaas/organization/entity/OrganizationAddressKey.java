package com.librarysaas.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key matching the schema: (organization_id, address_id, address_type).
 * address_type is part of the key, so one owner may hold several addresses of
 * different types but only one of any given type.
 */
@Embeddable
public class OrganizationAddressKey implements Serializable {

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "address_type")
    private String addressType;

    public OrganizationAddressKey() {}

    public OrganizationAddressKey(Long organizationId, Long addressId, String addressType) {
        this.organizationId = organizationId;
        this.addressId = addressId;
        this.addressType = addressType;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getAddressType() {
        return addressType;
    }

    public void setAddressType(String addressType) {
        this.addressType = addressType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrganizationAddressKey that = (OrganizationAddressKey) o;
        return Objects.equals(organizationId, that.organizationId)
                && Objects.equals(addressId, that.addressId)
                && Objects.equals(addressType, that.addressType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, addressId, addressType);
    }
}
