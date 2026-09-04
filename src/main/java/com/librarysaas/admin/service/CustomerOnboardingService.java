package com.librarysaas.admin.service;

import com.librarysaas.admin.dto.CustomerOnboardingRequest;
import com.librarysaas.admin.dto.CustomerOnboardingResponse;

/**
 * Onboards a paying customer onto the platform.
 *
 * One call creates the whole tenant - organization, library, administrator, role
 * and both memberships - or none of it. There is no read side: the initial
 * password exists only in the creation response, so there is nothing to fetch
 * later.
 */
public interface CustomerOnboardingService {

    CustomerOnboardingResponse onboardCustomer(CustomerOnboardingRequest request);
}
