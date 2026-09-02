package com.librarysaas.security.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_code")
    private String roleCode;

    private String scope;

    public Long getRoleId() { return roleId; }
    public String getRoleCode() { return roleCode; }
    public String getScope() { return scope; }
}
