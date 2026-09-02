package com.librarysaas.organization.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.organization.dto.LibraryCreateRequest;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.LibraryUpdateRequest;
import com.librarysaas.organization.service.LibraryService;
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
@RequestMapping("/api/libraries")
@Tag(name = "Libraries", description = "Library branches belonging to an organization")
public class LibraryController {

    private final LibraryService libraryService;

    @Autowired
    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LIBRARY_CREATE')")
    @Operation(summary = "Create a library under an organization",
            description = "Library code must be unique within the organization, which must be ACTIVE. "
                    + "Opening time must precede closing time.")
    public ResponseEntity<ApiResponse<LibraryResponse>> createLibrary(
            @RequestParam Long organizationId,
            @Valid @RequestBody LibraryCreateRequest request) {
        LibraryResponse response = libraryService.createLibrary(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Library created", response));
    }

    @GetMapping("/{libraryId}")
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    @Operation(summary = "Get a library by id",
            description = "Requires an active membership of the library.")
    public ResponseEntity<ApiResponse<LibraryResponse>> getLibrary(
            @PathVariable Long libraryId) {
        LibraryResponse response = libraryService.getLibrary(libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Library retrieved", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    @Operation(summary = "List libraries",
            description = "With organizationId: ACTIVE libraries of that organization. "
                    + "Without it: every library the caller is an active member of.")
    public ResponseEntity<ApiResponse<List<LibraryResponse>>> listLibraries(
            @RequestParam(required = false) Long organizationId) {
        List<LibraryResponse> responses;
        if (organizationId != null) {
            responses = libraryService.listLibrariesByOrganization(organizationId);
        } else {
            responses = libraryService.listLibrariesForUser();
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Libraries retrieved", responses));
    }

    @PutMapping("/{libraryId}")
    @PreAuthorize("hasAuthority('LIBRARY_UPDATE')")
    @Operation(summary = "Update a library",
            description = "Only supplied fields are applied. A library cannot be activated while its "
                    + "organization is inactive.")
    public ResponseEntity<ApiResponse<LibraryResponse>> updateLibrary(
            @PathVariable Long libraryId,
            @Valid @RequestBody LibraryUpdateRequest request) {
        LibraryResponse response = libraryService.updateLibrary(libraryId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Library updated", response));
    }

    @DeleteMapping("/{libraryId}")
    @PreAuthorize("hasAuthority('LIBRARY_STATUS_UPDATE')")
    @Operation(summary = "Deactivate a library",
            description = "Soft delete: sets status INACTIVE.")
    public ResponseEntity<ApiResponse<Void>> deactivateLibrary(
            @PathVariable Long libraryId) {
        libraryService.deactivateLibrary(libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Library deactivated", null));
    }
}
