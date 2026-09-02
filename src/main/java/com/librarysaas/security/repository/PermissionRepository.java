package com.librarysaas.security.repository;

import com.librarysaas.security.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    @Query(value = "SELECT p.permission_code FROM permissions p JOIN role_permission rp ON rp.permission_id = p.permission_id JOIN user_role ur ON ur.role_id = rp.role_id WHERE ur.user_id = :userId", nativeQuery = true)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
