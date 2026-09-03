package com.librarysaas.student.dto;

import com.librarysaas.organization.entity.Address;
import com.librarysaas.student.entity.StudentEmergencyContact;

import java.time.LocalDateTime;

/**
 * One emergency contact, with its address inlined.
 *
 * The address is returned as a nested object rather than an id, mirroring the
 * request: since a caller can never supply an address id, exposing one would
 * only invite an attempt to reuse it. Its own audit columns are not published,
 * as they are internal.
 */
public class EmergencyContactResponse {

    private Long emergencyContactId;
    private Long studentId;
    private String firstName;
    private String lastName;
    private String relationship;
    private String mobile;
    private String email;
    private Boolean isPrimary;
    private AddressDetail address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** The address fields, without the identifier or audit columns. */
    public static class AddressDetail {
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

        public String getAddressLine1() { return addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public String getAddressLine3() { return addressLine3; }
        public String getLandmark() { return landmark; }
        public String getCity() { return city; }
        public String getDistrict() { return district; }
        public String getState() { return state; }
        public String getCountry() { return country; }
        public String getPostalCode() { return postalCode; }
        public String getPhone1() { return phone1; }
        public String getPhone2() { return phone2; }
        public String getEmail() { return email; }
    }

    public static EmergencyContactResponse from(StudentEmergencyContact contact) {
        EmergencyContactResponse r = new EmergencyContactResponse();
        r.emergencyContactId = contact.getEmergencyContactId();
        if (contact.getStudent() != null) {
            r.studentId = contact.getStudent().getStudentId();
        }
        r.firstName = contact.getFirstName();
        r.lastName = contact.getLastName();
        r.relationship = contact.getRelationship();
        r.mobile = contact.getMobile();
        r.email = contact.getEmail();
        r.isPrimary = contact.getIsPrimary();
        r.createdAt = contact.getCreatedAt();
        r.updatedAt = contact.getUpdatedAt();

        Address address = contact.getAddress();
        if (address != null) {
            AddressDetail detail = new AddressDetail();
            detail.addressLine1 = address.getAddressLine1();
            detail.addressLine2 = address.getAddressLine2();
            detail.addressLine3 = address.getAddressLine3();
            detail.landmark = address.getLandmark();
            detail.city = address.getCity();
            detail.district = address.getDistrict();
            detail.state = address.getState();
            detail.country = address.getCountry();
            detail.postalCode = address.getPostalCode();
            detail.phone1 = address.getPhone1();
            detail.phone2 = address.getPhone2();
            detail.email = address.getEmail();
            r.address = detail;
        }
        return r;
    }

    public Long getEmergencyContactId() { return emergencyContactId; }
    public Long getStudentId() { return studentId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRelationship() { return relationship; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public Boolean getIsPrimary() { return isPrimary; }
    public AddressDetail getAddress() { return address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
