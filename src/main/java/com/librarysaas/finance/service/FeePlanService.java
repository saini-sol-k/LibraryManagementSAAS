package com.librarysaas.finance.service;

import com.librarysaas.finance.dto.FeePlanRequest;
import com.librarysaas.finance.dto.FeePlanResponse;

import java.util.List;

/**
 * Fee plans: the priced templates a library offers.
 *
 * Every operation is library scoped and re-checks the caller's membership of
 * that library. Plans are never deleted, because invoices reference them;
 * retiring one is a status change.
 */
public interface FeePlanService {

    List<FeePlanResponse> getLibraryFeePlans(Long libraryId, String status);

    FeePlanResponse getFeePlan(Long feePlanId);

    FeePlanResponse createFeePlan(Long libraryId, FeePlanRequest request);

    FeePlanResponse updateFeePlan(Long feePlanId, FeePlanRequest request);

    FeePlanResponse updateStatus(Long feePlanId, String status);
}
