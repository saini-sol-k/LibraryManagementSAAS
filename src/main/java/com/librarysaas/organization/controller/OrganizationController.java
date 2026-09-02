package com.librarysaas.organization.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.organization.dto.OrganizationCreateRequest;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.organization.dto.OrganizationUpdateRequest;
import com.librarysaas.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Organizations", description = "Tenant root: organizations the caller belongs to")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Autowired
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    @Operation(summary = "Create an organization",
            description = "Restricted to SUPER_ADMIN. Organization code must be globally unique.")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody OrganizationCreateRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Organization created", response));
    }

    @GetMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    @Operation(summary = "Get an organization by id",
            description = "Requires an active membership of the organization.")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganization(
            @PathVariable Long organizationId) {
        OrganizationResponse response = organizationService.getOrganization(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Organization retrieved", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    @Operation(summary = "List the caller's organizations",
            description = "Returns only ACTIVE organizations reached through an ACTIVE membership.")
    public ResponseEntity<ApiResponse<List<OrganizationResponse>>> listOrganizations() {
        List<OrganizationResponse> responses = organizationService.listUserOrganizations();
        return ResponseEntity.ok(new ApiResponse<>(true, "Organizations retrieved", responses));
    }

    @PutMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    @Operation(summary = "Update an organization",
            description = "Only supplied fields are applied. Allowed status values: ACTIVE, INACTIVE, SUSPENDED.")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable Long organizationId,
            @Valid @RequestBody OrganizationUpdateRequest request) {
        OrganizationResponse response = organizationService.updateOrganization(organizationId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Organization updated", response));
    }

    @DeleteMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    @Operation(summary = "Deactivate an organization",
            description = "Soft delete: sets status INACTIVE. Restricted to SUPER_ADMIN and rejected "
                    + "while the organization still has active libraries.")
    public ResponseEntity<ApiResponse<Void>> deactivateOrganization(
            @PathVariable Long organizationId) {
        organizationService.deactivateOrganization(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Organization deactivated", null));
    }
}
