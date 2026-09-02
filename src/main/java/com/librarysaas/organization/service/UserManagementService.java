package com.librarysaas.organization.service;

public interface UserManagementService {
    
    void addUserToOrganization(Long organizationId, Long userId, Boolean isPrimary);
    
    void removeUserFromOrganization(Long organizationId, Long userId);
    
    void addUserToLibrary(Long libraryId, Long userId, Boolean isPrimary);
    
    void removeUserFromLibrary(Long libraryId, Long userId);
    
    void setUserPrimaryOrganization(Long userId, Long organizationId);
    
    void setUserPrimaryLibrary(Long userId, Long libraryId);
}
