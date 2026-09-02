package com.librarysaas.organization.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.organization.dto.LibraryCreateRequest;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.LibraryUpdateRequest;
import com.librarysaas.organization.service.LibraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryService libraryService;

    @Autowired
    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LIBRARY_CREATE')")
    public ResponseEntity<ApiResponse<LibraryResponse>> createLibrary(
            @RequestParam Long organizationId,
            @RequestBody LibraryCreateRequest request) {
        LibraryResponse response = libraryService.createLibrary(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Library created", response));
    }

    @GetMapping("/{libraryId}")
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public ResponseEntity<ApiResponse<LibraryResponse>> getLibrary(
            @PathVariable Long libraryId) {
        LibraryResponse response = libraryService.getLibrary(libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Library retrieved", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
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
    public ResponseEntity<ApiResponse<LibraryResponse>> updateLibrary(
            @PathVariable Long libraryId,
            @RequestBody LibraryUpdateRequest request) {
        LibraryResponse response = libraryService.updateLibrary(libraryId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Library updated", response));
    }

    @DeleteMapping("/{libraryId}")
    @PreAuthorize("hasAuthority('LIBRARY_STATUS_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> deactivateLibrary(
            @PathVariable Long libraryId) {
        libraryService.deactivateLibrary(libraryId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Library deactivated", null));
    }
}
