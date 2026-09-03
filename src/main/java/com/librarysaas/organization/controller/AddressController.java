package com.librarysaas.organization.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.organization.dto.AddressRequest;
import com.librarysaas.organization.dto.AddressResponse;
import com.librarysaas.organization.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Addresses, managed through the resource that owns them.
 *
 * There is deliberately no standalone /api/addresses CRUD: the address table has
 * no tenant column, so an address id on its own cannot be authorised. Nesting
 * under the owner makes every request tenant-checkable and matches the schema,
 * where address_type and is_primary live on the link table. This follows the
 * same nesting the membership endpoints already use.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Addresses", description = "Addresses of organizations, libraries and students")
public class AddressController {

    private final AddressService addressService;

    @Autowired
    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /* ------------------------------------------------------------ organization */

    @GetMapping("/organizations/{organizationId}/addresses")
    @Operation(summary = "List an organization's addresses",
            description = "Primary address first. Requires active membership of the organization.")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listOrganizationAddresses(
            @PathVariable Long organizationId) {
        List<AddressResponse> addresses = addressService.getOrganizationAddresses(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Addresses retrieved", addresses));
    }

    @PostMapping("/organizations/{organizationId}/addresses")
    @Operation(summary = "Add an address to an organization",
            description = "One address per type. Marking it primary demotes the previous primary.")
    public ResponseEntity<ApiResponse<AddressResponse>> addOrganizationAddress(
            @PathVariable Long organizationId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.addOrganizationAddress(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Address added", address));
    }

    @PutMapping("/organizations/{organizationId}/addresses/{addressId}")
    @Operation(summary = "Update an organization address",
            description = "The address must already belong to this organization.")
    public ResponseEntity<ApiResponse<AddressResponse>> updateOrganizationAddress(
            @PathVariable Long organizationId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.updateOrganizationAddress(organizationId, addressId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address updated", address));
    }

    @DeleteMapping("/organizations/{organizationId}/addresses/{addressId}")
    @Operation(summary = "Remove an organization address")
    public ResponseEntity<ApiResponse<Void>> removeOrganizationAddress(
            @PathVariable Long organizationId,
            @PathVariable Long addressId) {
        addressService.removeOrganizationAddress(organizationId, addressId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address removed", null));
    }

    /* ---------------------------------------------------------------- library */

    @GetMapping("/libraries/{libraryId}/addresses")
    @Operation(summary = "List a library's addresses",
            description = "Primary address first. Requires active membership of the library.")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listLibraryAddresses(
            @PathVariable Long libraryId) {
        List<AddressResponse> addresses = addressService.getLibraryAddresses(libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Addresses retrieved", addresses));
    }

    @PostMapping("/libraries/{libraryId}/addresses")
    @Operation(summary = "Add an address to a library")
    public ResponseEntity<ApiResponse<AddressResponse>> addLibraryAddress(
            @PathVariable Long libraryId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.addLibraryAddress(libraryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Address added", address));
    }

    @PutMapping("/libraries/{libraryId}/addresses/{addressId}")
    @Operation(summary = "Update a library address")
    public ResponseEntity<ApiResponse<AddressResponse>> updateLibraryAddress(
            @PathVariable Long libraryId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.updateLibraryAddress(libraryId, addressId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address updated", address));
    }

    @DeleteMapping("/libraries/{libraryId}/addresses/{addressId}")
    @Operation(summary = "Remove a library address")
    public ResponseEntity<ApiResponse<Void>> removeLibraryAddress(
            @PathVariable Long libraryId,
            @PathVariable Long addressId) {
        addressService.removeLibraryAddress(libraryId, addressId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address removed", null));
    }

    /* ---------------------------------------------------------------- student */

    @GetMapping("/students/{studentId}/addresses")
    @Operation(summary = "List a student's addresses",
            description = "Scoped to the library that owns the student.")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listStudentAddresses(
            @PathVariable Long studentId) {
        List<AddressResponse> addresses = addressService.getStudentAddresses(studentId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Addresses retrieved", addresses));
    }

    @PostMapping("/students/{studentId}/addresses")
    @Operation(summary = "Add an address to a student",
            description = "Types: HOME, PERMANENT, CURRENT, CORRESPONDENCE, OTHER. One per type.")
    public ResponseEntity<ApiResponse<AddressResponse>> addStudentAddress(
            @PathVariable Long studentId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.addStudentAddress(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Address added", address));
    }

    @PutMapping("/students/{studentId}/addresses/{addressId}")
    @Operation(summary = "Update a student address")
    public ResponseEntity<ApiResponse<AddressResponse>> updateStudentAddress(
            @PathVariable Long studentId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.updateStudentAddress(studentId, addressId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address updated", address));
    }

    @DeleteMapping("/students/{studentId}/addresses/{addressId}")
    @Operation(summary = "Remove a student address")
    public ResponseEntity<ApiResponse<Void>> removeStudentAddress(
            @PathVariable Long studentId,
            @PathVariable Long addressId) {
        addressService.removeStudentAddress(studentId, addressId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address removed", null));
    }
}
