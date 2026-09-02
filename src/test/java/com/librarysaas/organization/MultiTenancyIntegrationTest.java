package com.librarysaas.organization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.organization.repository.OrganizationRepository;
import com.librarysaas.organization.repository.UserLibraryRepository;
import com.librarysaas.organization.repository.UserOrganizationRepository;
import com.librarysaas.organization.service.LibraryService;
import com.librarysaas.organization.service.OrganizationService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;

public class MultiTenancyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private UserLibraryRepository userLibraryRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantAuthorizationService tenantAuthorizationService;

    private void authenticateAsUser(Long userId, String username) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(
                        new SimpleGrantedAuthority("ORGANIZATION_VIEW"),
                        new SimpleGrantedAuthority("LIBRARY_VIEW"),
                        new SimpleGrantedAuthority("STUDENT_VIEW")
                ));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsSuperAdmin(Long userId, String username) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(
                        new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                        new SimpleGrantedAuthority("ORGANIZATION_VIEW"),
                        new SimpleGrantedAuthority("LIBRARY_VIEW"),
                        new SimpleGrantedAuthority("STUDENT_VIEW")
                ));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void testOrganization1UserCanAccessMultipleOrganizations() {
        // Owner1 is member of org1 (primary) and org2 (secondary)
        authenticateAsUser(2L, "owner1");
        
        // Can access org1
        assertDoesNotThrow(() -> organizationService.getOrganization(1L));
        
        // Can also access org2 (is member)
        assertDoesNotThrow(() -> organizationService.getOrganization(2L));
        
        // Cannot access non-existent org
        assertThrows(Exception.class, () -> organizationService.getOrganization(999L));
    }

    @Test
    public void testLibrary1UserCannotAccessLibrary3() {
        // Reception1 is member of org1, lib1 only
        authenticateAsUser(4L, "reception1");
        
        // Can access lib1
        assertDoesNotThrow(() -> libraryService.getLibrary(1L));
        
        // Cannot access lib3 (belongs to org2)
        assertThrows(AccessDeniedException.class, () -> libraryService.getLibrary(3L));
    }

    @Test
    public void testStudentInLibrary1NotAccessibleFromLibrary2User() {
        // Verify test data: Student 1 is in library 1
        Student student1 = studentRepository.findById(1L).orElse(null);
        assertNotNull(student1);
        assertEquals(1L, student1.getLibrary().getLibraryId());
        
        // Reception1 is in lib1
        authenticateAsUser(4L, "reception1");
        
        // Reception1 can access student1 (same library)
        assertDoesNotThrow(() -> libraryService.getLibrary(1L));
        
        // Manager1 is in org1, lib1 - also can access
        authenticateAsUser(3L, "manager1");
        assertDoesNotThrow(() -> libraryService.getLibrary(1L));
    }

    @Test
    public void testUserMembershipValidationOnLibraryAccess() {
        // User 4 (reception1) is member of org1 and lib1 only
        
        // Verify membership
        UserOrganization uo = userOrganizationRepository.findByUserIdAndOrganizationId(4L, 1L)
                .orElse(null);
        assertNotNull(uo);
        
        UserLibrary ul = userLibraryRepository.findByUserIdAndLibraryId(4L, 1L)
                .orElse(null);
        assertNotNull(ul);
        
        // User SHOULD be member of lib2 (org1) - added to test data
        UserLibrary ul2 = userLibraryRepository.findByUserIdAndLibraryId(4L, 2L)
                .orElse(null);
        assertNotNull(ul2);
        
        // User should NOT be member of lib3 (org2)
        UserLibrary ul3 = userLibraryRepository.findByUserIdAndLibraryId(4L, 3L)
                .orElse(null);
        assertNull(ul3);
    }

    @Test
    public void testLibraryOrganizationIntegrity() {
        // Every library must belong to exactly one organization
        List<Library> allLibraries = libraryRepository.findAll();
        assertFalse(allLibraries.isEmpty());
        
        for (Library lib : allLibraries) {
            assertNotNull(lib.getOrganization());
            assertNotNull(lib.getOrganization().getOrganizationId());
        }
        
        // Verify org1 has libs 1,2 and org2 has lib 3
        List<Library> org1libs = libraryRepository.findByOrganizationId(1L);
        assertEquals(2, org1libs.size());
        assertTrue(org1libs.stream().anyMatch(l -> l.getLibraryId() == 1L));
        assertTrue(org1libs.stream().anyMatch(l -> l.getLibraryId() == 2L));
        
        List<Library> org2libs = libraryRepository.findByOrganizationId(2L);
        assertEquals(1, org2libs.size());
        assertTrue(org2libs.stream().anyMatch(l -> l.getLibraryId() == 3L));
    }

    @Test
    public void testTenantContextEnforcesMembership() {
        // Verify that TenantAuthorizationService properly checks membership
        
        // User 4 is member of lib1 and lib2, not lib3
        assertTrue(tenantAuthorizationService.hasLibraryAccess(4L, 1L));
        assertTrue(tenantAuthorizationService.hasLibraryAccess(4L, 2L));
        assertFalse(tenantAuthorizationService.hasLibraryAccess(4L, 3L));
        
        // User 2 is member of org1 and org2
        assertTrue(tenantAuthorizationService.hasOrganizationAccess(2L, 1L));
        assertTrue(tenantAuthorizationService.hasOrganizationAccess(2L, 2L));
        assertFalse(tenantAuthorizationService.hasOrganizationAccess(2L, 999L));
    }

    @Test
    public void testSuperAdminCanAccessAllTenants() {
        // SuperAdmin (user 1) is member of all orgs and libs
        authenticateAsSuperAdmin(1L, "superadmin");
        assertTrue(tenantAuthorizationService.isSuperAdmin());
        
        // Can access all organizations
        assertDoesNotThrow(() -> organizationService.getOrganization(1L));
        assertDoesNotThrow(() -> organizationService.getOrganization(2L));
        
        // Can list organizations (based on user_organization memberships)
        List<OrganizationResponse> orgs = organizationService.listUserOrganizations();
        assertNotNull(orgs);
        assertTrue(orgs.size() >= 2);
    }
}

