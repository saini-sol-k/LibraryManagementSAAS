package com.librarysaas.organization.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.organization.dto.OrganizationCreateRequest;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.organization.dto.OrganizationUpdateRequest;
import com.librarysaas.organization.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Autowired
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @RequestBody OrganizationCreateRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Organization created", response));
    }

    @GetMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganization(
            @PathVariable Long organizationId) {
        OrganizationResponse response = organizationService.getOrganization(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Organization retrieved", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public ResponseEntity<ApiResponse<List<OrganizationResponse>>> listOrganizations() {
        List<OrganizationResponse> responses = organizationService.listUserOrganizations();
        return ResponseEntity.ok(new ApiResponse<>(true, "Organizations retrieved", responses));
    }

    @PutMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable Long organizationId,
            @RequestBody OrganizationUpdateRequest request) {
        OrganizationResponse response = organizationService.updateOrganization(organizationId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Organization updated", response));
    }

    @DeleteMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> deactivateOrganization(
            @PathVariable Long organizationId) {
        organizationService.deactivateOrganization(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Organization deactivated", null));
    }
}
