package com.librarysaas.organization.service;

import com.librarysaas.organization.dto.LibraryCreateRequest;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.LibrarySeatCountRequest;
import com.librarysaas.organization.dto.LibrarySeatCountResponse;
import com.librarysaas.organization.dto.LibraryUpdateRequest;
import java.util.List;

public interface LibraryService {
    
    LibraryResponse createLibrary(Long organizationId, LibraryCreateRequest request);
    
    LibraryResponse getLibrary(Long libraryId);
    
    List<LibraryResponse> listLibrariesForUser();
    
    List<LibraryResponse> listLibrariesByOrganization(Long organizationId);
    
    LibraryResponse updateLibrary(Long libraryId, LibraryUpdateRequest request);
    
    /**
     * Sets the library total seat count and brings its seats in line with it.
     *
     * Kept apart from updateLibrary because it is not a field edit: it creates or
     * retires seat rows, reports what it did, and is the only route by which seat
     * numbers come into existence.
     */
    LibrarySeatCountResponse updateSeatCount(Long libraryId, LibrarySeatCountRequest request);
    
    void deactivateLibrary(Long libraryId);
}
