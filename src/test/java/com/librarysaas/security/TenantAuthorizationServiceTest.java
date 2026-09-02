package com.librarysaas.security;

import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.repository.UserTenantRepository;
import com.librarysaas.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TenantAuthorizationServiceTest {

    @Test
    public void hasLibraryAccessChecksRepository() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantRepository tenantRepo = mock(UserTenantRepository.class);
        StudentRepository studentRepository = mock(StudentRepository.class);

        TenantAuthorizationService svc = new TenantAuthorizationService(userRepository, tenantRepo, studentRepository);

        when(tenantRepo.existsInLibrary(10L, 5L)).thenReturn(1);
        assertTrue(svc.hasLibraryAccess(10L, 5L));

        when(tenantRepo.existsInLibrary(10L, 6L)).thenReturn(0);
        assertFalse(svc.hasLibraryAccess(10L, 6L));
    }

    @Test
    public void requireLibraryAccessThrowsWhenNotMember() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantRepository tenantRepo = mock(UserTenantRepository.class);
        StudentRepository studentRepository = mock(StudentRepository.class);

        TenantAuthorizationService svc = new TenantAuthorizationService(userRepository, tenantRepo, studentRepository);

        when(tenantRepo.existsInLibrary(10L, 5L)).thenReturn(0);

        assertThrows(AccessDeniedException.class, () -> svc.requireLibraryAccess(10L, 5L));
    }

    @Test
    public void permissionChecksWorkAgainstAuthenticationAuthorities() {
        UserRepository userRepository = mock(UserRepository.class);
        UserTenantRepository tenantRepo = mock(UserTenantRepository.class);
        StudentRepository studentRepository = mock(StudentRepository.class);

        TenantAuthorizationService svc = new TenantAuthorizationService(userRepository, tenantRepo, studentRepository);

        // Set up a SecurityContext with authorities containing STUDENT_VIEW only
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "bob", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("STUDENT_VIEW")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(svc.hasPermission("STUDENT_VIEW"));
        assertFalse(svc.hasPermission("STUDENT_CREATE"));

        // requirePermission should throw for missing permission
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> svc.requirePermission("STUDENT_CREATE"));

        // cleanup
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
