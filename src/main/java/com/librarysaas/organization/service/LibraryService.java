package com.librarysaas.organization.service;

import com.librarysaas.organization.dto.LibraryCreateRequest;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.LibraryUpdateRequest;
import java.util.List;

public interface LibraryService {
    
    LibraryResponse createLibrary(Long organizationId, LibraryCreateRequest request);
    
    LibraryResponse getLibrary(Long libraryId);
    
    List<LibraryResponse> listLibrariesForUser();
    
    List<LibraryResponse> listLibrariesByOrganization(Long organizationId);
    
    LibraryResponse updateLibrary(Long libraryId, LibraryUpdateRequest request);
    
    void deactivateLibrary(Long libraryId);
}
