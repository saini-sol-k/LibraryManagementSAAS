package com.librarysaas.security;

import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.repository.UserTenantRepository;
import com.librarysaas.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TenantAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(TenantAuthorizationService.class);

    private final UserRepository userRepository;
    private final UserTenantRepository userTenantRepository;
    private final StudentRepository studentRepository;

    public TenantAuthorizationService(UserRepository userRepository, UserTenantRepository userTenantRepository, StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.userTenantRepository = userTenantRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Get current authenticated username (principal name). Returns null if no authentication.
     */
    public String getCurrentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null) return null;
        return a.getName();
    }

    public Optional<Long> getCurrentUserId() {
        String username = getCurrentUsername();
        if (username == null) return Optional.empty();
        return userRepository.findByUsernameOrEmail(username).map(u -> u.getUserId());
    }

    public boolean isSuperAdmin() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().anyMatch(gr -> gr.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    /**
     * Returns true if the current authenticated principal has the given permission.
     * Permissions are stored as authorities (permission codes) and are loaded from
     * the database at authentication time by CustomUserDetailsService.
     */
    public boolean hasPermission(String permission) {
        if (permission == null) return false;
        if (isSuperAdmin()) return true;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().anyMatch(gr -> gr.getAuthority().equals(permission));
    }

    /**
     * Require that the current principal has the given permission, otherwise throw AccessDeniedException.
     */
    public void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            log.warn("User {} attempted an operation requiring permission {} but does not have it", getCurrentUsername(), permission);
            throw new AccessDeniedException("Access denied");
        }
    }

    public boolean hasOrganizationAccess(Long userId, Long organizationId) {
        if (organizationId == null) return false;
        if (isSuperAdmin()) return true;
        Integer cnt = userTenantRepository.existsInOrganization(userId, organizationId);
        return cnt != null && cnt > 0;
    }

    public boolean hasLibraryAccess(Long userId, Long libraryId) {
        if (libraryId == null) return false;
        if (isSuperAdmin()) return true;
        Integer cnt = userTenantRepository.existsInLibrary(userId, libraryId);
        return cnt != null && cnt > 0;
    }

    public void requireOrganizationAccess(Long userId, Long organizationId) {
        if (!hasOrganizationAccess(userId, organizationId)) {
            log.warn("User {} attempted access to organization {} but is not a member", userId, organizationId);
            throw new AccessDeniedException("Access denied");
        }
    }

    public void requireLibraryAccess(Long userId, Long libraryId) {
        if (!hasLibraryAccess(userId, libraryId)) {
            log.warn("User {} attempted access to library {} but is not a member", userId, libraryId);
            throw new AccessDeniedException("Access denied");
        }
    }

    public void requireLibraryAccessForStudent(Long userId, Long studentId) {
        studentRepository.findById(studentId).ifPresentOrElse(s -> {
            Long libId = s.getLibrary() != null ? s.getLibrary().getLibraryId() : null;
            requireLibraryAccess(userId, libId);
        }, () -> {
            // If student not found, service layer should throw ResourceNotFound instead; here we just deny
            throw new AccessDeniedException("Access denied");
        });
    }

    public java.util.Optional<Long> findPrimaryLibraryIdForUser(Long userId) {
        return userTenantRepository.findPrimaryLibraryId(userId);
    }

    public java.util.Optional<Long> findPrimaryOrganizationIdForUser(Long userId) {
        return userTenantRepository.findPrimaryOrganizationId(userId);
    }
}
