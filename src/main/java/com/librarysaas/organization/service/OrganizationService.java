package com.librarysaas.organization.service;

import com.librarysaas.organization.dto.OrganizationCreateRequest;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.organization.dto.OrganizationUpdateRequest;
import java.util.List;

public interface OrganizationService {
    
    OrganizationResponse createOrganization(OrganizationCreateRequest request);
    
    OrganizationResponse getOrganization(Long organizationId);
    
    List<OrganizationResponse> listUserOrganizations();
    
    OrganizationResponse updateOrganization(Long organizationId, OrganizationUpdateRequest request);
    
    void deactivateOrganization(Long organizationId);
}
