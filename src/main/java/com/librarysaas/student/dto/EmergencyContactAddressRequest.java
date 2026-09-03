package com.librarysaas.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The address of an emergency contact, supplied inline.
 *
 * There is deliberately no {@code addressId} field. The {@code address} table is
 * global, with no tenant column, so accepting an id from a caller would let a
 * contact be pointed at an address belonging to another tenant or another
 * student. The service creates the row on first use and updates that same row
 * afterwards, which means a contact can only ever reference an address this
 * service made for it.
 *
 * Field rules mirror AddressRequest from Phase 2A so the two behave alike, minus
 * addressType and isPrimary, which describe a join-table relationship this
 * direct foreign key does not have.
 */
public class EmergencyContactAddressRequest {

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 250, message = "Address line 1 must not exceed 250 characters")
    private String addressLine1;

    @Size(max = 250, message = "Address line 2 must not exceed 250 characters")
    private String addressLine2;

    @Size(max = 250, message = "Address line 3 must not exceed 250 characters")
    private String addressLine3;

    @Size(max = 200, message = "Landmark must not exceed 200 characters")
    private String landmark;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 100, message = "District must not exceed 100 characters")
    private String district;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{2,19}$",
            message = "Postal code may only contain letters, digits, spaces and hyphens")
    private String postalCode;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    @Pattern(regexp = "^[0-9+][0-9 ()-]{5,19}$", message = "Phone must be a valid contact number")
    private String phone1;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    @Pattern(regexp = "^[0-9+][0-9 ()-]{5,19}$", message = "Phone must be a valid contact number")
    private String phone2;

    @Email(message = "Email must be a well-formed email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    public EmergencyContactAddressRequest() {}

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
}
