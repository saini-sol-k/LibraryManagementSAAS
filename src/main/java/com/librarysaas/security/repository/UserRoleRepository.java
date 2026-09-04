package com.librarysaas.security.repository;

import com.librarysaas.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Writes to the user_role join table.
 *
 * The table has no entity: RoleRepository and PermissionRepository already read
 * it with native queries, and this follows the same approach rather than
 * introducing a composite-key entity for a two-column join.
 *
 * The insert resolves the role by its code rather than taking an id, so no role
 * id is hard-coded anywhere and a caller cannot grant a role that does not
 * exist. It returns the number of rows written, which is 0 when the role code is
 * unknown - the caller must treat that as a failure rather than assume success.
 */
public interface UserRoleRepository extends JpaRepository<User, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO user_role (user_id, role_id) "
            + "SELECT :userId, r.role_id FROM roles r WHERE r.role_code = :roleCode",
            nativeQuery = true)
    int assignRoleByCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Query(value = "SELECT COUNT(*) FROM user_role WHERE user_id = :userId", nativeQuery = true)
    int countRolesForUser(@Param("userId") Long userId);
}
