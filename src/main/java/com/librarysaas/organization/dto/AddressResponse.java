package com.librarysaas.organization.dto;

import com.librarysaas.organization.entity.Address;
import java.time.LocalDateTime;

/**
 * Address as returned by the API, flattened from the reusable Address row plus
 * the owning link's relationship attributes.
 *
 * The audit columns created_by / updated_by are deliberately not exposed: they
 * are internal user ids of no use to a client.
 */
public class AddressResponse {

    private Long addressId;
    private String firstName;
    private String lastName;
    private String addressLine1;
    private String addressLine2;
    private String addressLine3;
    private String landmark;
    private String city;
    private String district;
    private String state;
    private String country;
    private String postalCode;
    private String phone1;
    private String phone2;
    private String email;

    /** From the link table, not the address row. */
    private String addressType;
    private Boolean isPrimary;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AddressResponse() {}

    public static AddressResponse from(Address address, String addressType, Boolean isPrimary) {
        AddressResponse r = new AddressResponse();
        r.addressId = address.getAddressId();
        r.firstName = address.getFirstName();
        r.lastName = address.getLastName();
        r.addressLine1 = address.getAddressLine1();
        r.addressLine2 = address.getAddressLine2();
        r.addressLine3 = address.getAddressLine3();
        r.landmark = address.getLandmark();
        r.city = address.getCity();
        r.district = address.getDistrict();
        r.state = address.getState();
        r.country = address.getCountry();
        r.postalCode = address.getPostalCode();
        r.phone1 = address.getPhone1();
        r.phone2 = address.getPhone2();
        r.email = address.getEmail();
        r.addressType = addressType;
        r.isPrimary = isPrimary;
        r.createdAt = address.getCreatedAt();
        r.updatedAt = address.getUpdatedAt();
        return r;
    }

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getAddressLine3() { return addressLine3; }
    public void setAddressLine3(String addressLine3) { this.addressLine3 = addressLine3; }

    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getPhone1() { return phone1; }
    public void setPhone1(String phone1) { this.phone1 = phone1; }

    public String getPhone2() { return phone2; }
    public void setPhone2(String phone2) { this.phone2 = phone2; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddressType() { return addressType; }
    public void setAddressType(String addressType) { this.addressType = addressType; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
