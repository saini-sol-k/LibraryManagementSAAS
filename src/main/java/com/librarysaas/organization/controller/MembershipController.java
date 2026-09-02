package com.librarysaas.organization.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.organization.dto.MembershipRequest;
import com.librarysaas.organization.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Organization and library membership APIs.
 *
 * Every operation delegates to {@link UserManagementService}; membership rules
 * (tenant must be active, target user must belong to the library's organization,
 * one primary tenant per user, last-member protection) live there.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Membership", description = "Manage which users belong to an organization or library")
public class MembershipController {

    private final UserManagementService userManagementService;

    @Autowired
    public MembershipController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping("/organizations/{organizationId}/members")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Add a user to an organization",
            description = "Adds an ACTIVE membership. A previously deactivated membership is "
                    + "reactivated; an existing active membership is a 409 conflict.")
    public ResponseEntity<ApiResponse<Void>> addUserToOrganization(
            @PathVariable Long organizationId,
            @Valid @RequestBody MembershipRequest request) {
        userManagementService.addUserToOrganization(organizationId, request.getUserId(), request.getIsPrimary());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User added to organization", null));
    }

    @DeleteMapping("/organizations/{organizationId}/members/{userId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Remove a user from an organization",
            description = "Also removes the user's memberships of every library in that organization. "
                    + "The last active member of an organization cannot be removed.")
    public ResponseEntity<ApiResponse<Void>> removeUserFromOrganization(
            @PathVariable Long organizationId,
            @PathVariable Long userId) {
        userManagementService.removeUserFromOrganization(organizationId, userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User removed from organization", null));
    }

    @PutMapping("/organizations/{organizationId}/members/{userId}/primary")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Set the caller's primary organization",
            description = "A user may only set their own primary organization, and only for an "
                    + "organization they are an active member of.")
    public ResponseEntity<ApiResponse<Void>> setPrimaryOrganization(
            @PathVariable Long organizationId,
            @PathVariable Long userId) {
        userManagementService.setUserPrimaryOrganization(userId, organizationId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Primary organization updated", null));
    }

    @PostMapping("/libraries/{libraryId}/members")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Add a user to a library",
            description = "The target user must already be a member of the library's organization.")
    public ResponseEntity<ApiResponse<Void>> addUserToLibrary(
            @PathVariable Long libraryId,
            @Valid @RequestBody MembershipRequest request) {
        userManagementService.addUserToLibrary(libraryId, request.getUserId(), request.getIsPrimary());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User added to library", null));
    }

    @DeleteMapping("/libraries/{libraryId}/members/{userId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Remove a user from a library")
    public ResponseEntity<ApiResponse<Void>> removeUserFromLibrary(
            @PathVariable Long libraryId,
            @PathVariable Long userId) {
        userManagementService.removeUserFromLibrary(libraryId, userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User removed from library", null));
    }

    @PutMapping("/libraries/{libraryId}/members/{userId}/primary")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Set the caller's primary library",
            description = "A user may only set their own primary library, and only for a library "
                    + "they are an active member of.")
    public ResponseEntity<ApiResponse<Void>> setPrimaryLibrary(
            @PathVariable Long libraryId,
            @PathVariable Long userId) {
        userManagementService.setUserPrimaryLibrary(userId, libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Primary library updated", null));
    }
}
