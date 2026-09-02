package com.librarysaas.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.librarysaas.IntegrationTestBase;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.LibraryUpdateRequest;
import com.librarysaas.organization.service.LibraryService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.security.repository.UserRepository;

public class LibraryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantAuthorizationService tenantAuthorizationService;

    private void authenticateAsUser(Long userId, String username, String... permissions) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("LIBRARY_VIEW"),
                new SimpleGrantedAuthority("LIBRARY_CREATE"),
                new SimpleGrantedAuthority("LIBRARY_UPDATE"),
                new SimpleGrantedAuthority("LIBRARY_STATUS_UPDATE")
        );
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void testGetLibraryWithAccess() {
        // User 1 is member of library 1
        authenticateAsUser(1L, "superadmin");
        LibraryResponse response = libraryService.getLibrary(1L);
        assertNotNull(response);
        assertEquals(1L, response.getLibraryId());
    }

    @Test
    public void testGetLibraryWithoutAccess() {
        // Create a user without library access
        authenticateAsUser(999L, "nolib");
        
        // Should throw ForbiddenException
        assertThrows(ForbiddenException.class, () -> {
            libraryService.getLibrary(1L);
        });
    }

    @Test
    public void testListLibrariesForUser() {
        // User 1 is member of libraries 1, 2, 3
        authenticateAsUser(1L, "superadmin");
        List<LibraryResponse> libraries = libraryService.listLibrariesForUser();
        
        assertNotNull(libraries);
        assertTrue(libraries.size() >= 3);
        assertTrue(libraries.stream().anyMatch(l -> l.getLibraryId() == 1L));
        assertTrue(libraries.stream().anyMatch(l -> l.getLibraryId() == 2L));
        assertTrue(libraries.stream().anyMatch(l -> l.getLibraryId() == 3L));
    }

    @Test
    public void testListLibrariesByOrganization() {
        // User 2 (owner1) is in org 1
        authenticateAsUser(2L, "owner1");
        List<LibraryResponse> libraries = libraryService.listLibrariesByOrganization(1L);
        
        assertNotNull(libraries);
        // Org 1 has libraries 1 and 2
        assertEquals(2, libraries.size());
        assertTrue(libraries.stream().anyMatch(l -> l.getLibraryId() == 1L));
        assertTrue(libraries.stream().anyMatch(l -> l.getLibraryId() == 2L));
    }

    @Test
    public void testLibraryOrgnaizationRelationship() {
        // Verify library 1 belongs to org 1
        Library lib = libraryRepository.findById(1L).orElse(null);
        assertNotNull(lib);
        assertNotNull(lib.getOrganization());
        assertEquals(1L, lib.getOrganization().getOrganizationId());
        
        // Verify library 3 belongs to org 2
        Library lib3 = libraryRepository.findById(3L).orElse(null);
        assertNotNull(lib3);
        assertNotNull(lib3.getOrganization());
        assertEquals(2L, lib3.getOrganization().getOrganizationId());
    }

    @Test
    public void testUpdateLibraryWithAccess() {
        // User 1 is member of library 1
        authenticateAsUser(1L, "superadmin");
        
        LibraryUpdateRequest request = new LibraryUpdateRequest();
        request.setName("Updated Library Name");
        
        LibraryResponse response = libraryService.updateLibrary(1L, request);
        assertEquals("Updated Library Name", response.getName());
        
        // Verify in database
        Library lib = libraryRepository.findById(1L).orElse(null);
        assertNotNull(lib);
        assertEquals("Updated Library Name", lib.getName());
    }

    @Test
    public void testCrossTenantLibraryAccessDenied() {
        // User 4 is only in org 1, lib 1
        authenticateAsUser(4L, "reception1");
        
        // User 4 should not access library 3 (which belongs to org 2)
        assertThrows(AccessDeniedException.class, () -> {
            libraryService.getLibrary(3L);
        });
    }

    @Test
    public void testLibraryScopedToOrganization() {
        // User 2 (owner1) is in org 1 (primary) and org 2 (secondary)
        authenticateAsUser(2L, "owner1");
        
        // User can access libraries in org 1
        List<LibraryResponse> org1libs = libraryService.listLibrariesByOrganization(1L);
        assertNotNull(org1libs);
        assertTrue(org1libs.size() > 0);
        
        // User can also access libraries in org 2 (is now a member)
        List<LibraryResponse> org2libs = libraryService.listLibrariesByOrganization(2L);
        assertNotNull(org2libs);
        assertTrue(org2libs.size() > 0);
        
        // But user cannot access a non-existent org
        assertThrows(AccessDeniedException.class, () -> {
            libraryService.listLibrariesByOrganization(999L);
        });
    }
}
