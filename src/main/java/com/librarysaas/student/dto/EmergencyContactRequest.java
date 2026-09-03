package com.librarysaas.student.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or update payload for a student's emergency contact.
 *
 * studentId is absent: it comes from the path on create and from the stored row
 * on update, so a contact cannot be moved onto another student.
 *
 * The address is nested rather than referenced by id, deliberately. See
 * {@link EmergencyContactAddressRequest}. Omitting it on update leaves any
 * existing address untouched rather than clearing it, because a missing field
 * means "unchanged" and not "remove".
 */
public class EmergencyContactRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /**
     * Free text in the schema. The seed shows FATHER, MOTHER and BROTHER, but
     * nothing constrains the column, so no closed set is imposed here.
     */
    @Size(max = 50, message = "Relationship must not exceed 50 characters")
    private String relationship;

    @Size(max = 30, message = "Mobile must not exceed 30 characters")
    @Pattern(regexp = "^[0-9+][0-9 ()-]{5,19}$", message = "Mobile must be a valid contact number")
    private String mobile;

    @Email(message = "Email must be a well-formed email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    /** Optional; treated as false when omitted. At most one per student. */
    private Boolean isPrimary;

    /** Optional. Created on first use, then updated in place. */
    @Valid
    private EmergencyContactAddressRequest address;

    public EmergencyContactRequest() {}

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

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public EmergencyContactAddressRequest getAddress() { return address; }
    public void setAddress(EmergencyContactAddressRequest address) { this.address = address; }
}
