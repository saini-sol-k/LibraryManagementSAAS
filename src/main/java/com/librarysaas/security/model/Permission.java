package com.librarysaas.security.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission {
    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "permission_code")
    private String permissionCode;

    public Long getPermissionId() { return permissionId; }
    public String getPermissionCode() { return permissionCode; }
}
