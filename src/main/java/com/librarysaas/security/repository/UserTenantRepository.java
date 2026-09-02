package com.librarysaas.security.repository;

import com.librarysaas.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserTenantRepository extends JpaRepository<User, Long> {
    @Query(value = "SELECT organization_id FROM user_organization WHERE user_id = :userId AND is_primary = TRUE LIMIT 1", nativeQuery = true)
    Optional<Long> findPrimaryOrganizationId(@Param("userId") Long userId);

    @Query(value = "SELECT library_id FROM user_library WHERE user_id = :userId AND is_primary = TRUE LIMIT 1", nativeQuery = true)
    Optional<Long> findPrimaryLibraryId(@Param("userId") Long userId);

    // Return a numeric count (0 or >0) to avoid DB-specific boolean mapping differences
    @Query(value = "SELECT COUNT(*) FROM user_organization WHERE user_id = :userId AND organization_id = :organizationId AND status = 'ACTIVE'", nativeQuery = true)
    Integer existsInOrganization(@Param("userId") Long userId, @Param("organizationId") Long organizationId);

    @Query(value = "SELECT COUNT(*) FROM user_library WHERE user_id = :userId AND library_id = :libraryId AND status = 'ACTIVE'", nativeQuery = true)
    Integer existsInLibrary(@Param("userId") Long userId, @Param("libraryId") Long libraryId);
}
