package com.librarysaas.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key matching the schema: (library_id, address_id, address_type).
 * address_type is part of the key, so one owner may hold several addresses of
 * different types but only one of any given type.
 */
@Embeddable
public class LibraryAddressKey implements Serializable {

    @Column(name = "library_id")
    private Long libraryId;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "address_type")
    private String addressType;

    public LibraryAddressKey() {}

    public LibraryAddressKey(Long libraryId, Long addressId, String addressType) {
        this.libraryId = libraryId;
        this.addressId = addressId;
        this.addressType = addressType;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
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
        LibraryAddressKey that = (LibraryAddressKey) o;
        return Objects.equals(libraryId, that.libraryId)
                && Objects.equals(addressId, that.addressId)
                && Objects.equals(addressType, that.addressType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(libraryId, addressId, addressType);
    }
}
