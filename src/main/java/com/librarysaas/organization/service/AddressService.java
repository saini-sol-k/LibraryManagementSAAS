package com.librarysaas.organization.service;

import com.librarysaas.organization.dto.AddressRequest;
import com.librarysaas.organization.dto.AddressResponse;
import java.util.List;

/**
 * Address management for the three owning resources that the schema models:
 * organization, library and student.
 *
 * Addresses are always reached through their owner. The address table carries no
 * tenant column, so an address id on its own could not be authorised; scoping
 * every operation to the owner is what keeps tenants isolated.
 */
public interface AddressService {

    List<AddressResponse> getOrganizationAddresses(Long organizationId);

    AddressResponse addOrganizationAddress(Long organizationId, AddressRequest request);

    AddressResponse updateOrganizationAddress(Long organizationId, Long addressId, AddressRequest request);

    void removeOrganizationAddress(Long organizationId, Long addressId);

    List<AddressResponse> getLibraryAddresses(Long libraryId);

    AddressResponse addLibraryAddress(Long libraryId, AddressRequest request);

    AddressResponse updateLibraryAddress(Long libraryId, Long addressId, AddressRequest request);

    void removeLibraryAddress(Long libraryId, Long addressId);

    List<AddressResponse> getStudentAddresses(Long studentId);

    AddressResponse addStudentAddress(Long studentId, AddressRequest request);

    AddressResponse updateStudentAddress(Long studentId, Long addressId, AddressRequest request);

    void removeStudentAddress(Long studentId, Long addressId);
}
