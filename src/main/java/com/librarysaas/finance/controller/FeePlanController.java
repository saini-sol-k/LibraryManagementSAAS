package com.librarysaas.finance.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.finance.dto.FeePlanRequest;
import com.librarysaas.finance.dto.FeePlanResponse;
import com.librarysaas.finance.dto.FeePlanStatusRequest;
import com.librarysaas.finance.service.FeePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fee plans: the priced templates a library bills from.
 *
 * Nested under the library that owns them, so a create is tenant-checkable from
 * the path alone. There is no delete: invoices reference plans, so retiring one
 * is a status change and the row survives.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Fee Plans", description = "Priced plans a library bills its students from")
public class FeePlanController {

    private final FeePlanService feePlanService;

    @Autowired
    public FeePlanController(FeePlanService feePlanService) {
        this.feePlanService = feePlanService;
    }

    @GetMapping("/libraries/{libraryId}/fee-plans")
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    @Operation(summary = "List a library's fee plans",
            description = "Ordered by name. Optional status filter: ACTIVE or INACTIVE. "
                    + "Requires FEE_PLAN_VIEW and membership of the library.")
    public ResponseEntity<ApiResponse<List<FeePlanResponse>>> listFeePlans(
            @PathVariable Long libraryId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Fee plans retrieved",
                feePlanService.getLibraryFeePlans(libraryId, status)));
    }

    @PostMapping("/libraries/{libraryId}/fee-plans")
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    @Operation(summary = "Create a fee plan",
            description = "The name must be free within the library and the amount cannot be "
                    + "negative. New plans start ACTIVE.")
    public ResponseEntity<ApiResponse<FeePlanResponse>> createFeePlan(
            @PathVariable Long libraryId,
            @Valid @RequestBody FeePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Fee plan created",
                        feePlanService.createFeePlan(libraryId, request)));
    }

    @GetMapping("/fee-plans/{feePlanId}")
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    @Operation(summary = "Get one fee plan",
            description = "Requires membership of the library the plan belongs to.")
    public ResponseEntity<ApiResponse<FeePlanResponse>> getFeePlan(@PathVariable Long feePlanId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Fee plan retrieved",
                feePlanService.getFeePlan(feePlanId)));
    }

    @PutMapping("/fee-plans/{feePlanId}")
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    @Operation(summary = "Update a fee plan",
            description = "Repricing a plan does not change invoices already raised from it, "
                    + "because each invoice stores its own amounts. Guarded by FEE_PLAN_CREATE "
                    + "because the schema defines no FEE_PLAN_UPDATE permission.")
    public ResponseEntity<ApiResponse<FeePlanResponse>> updateFeePlan(
            @PathVariable Long feePlanId,
            @Valid @RequestBody FeePlanRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Fee plan updated",
                feePlanService.updateFeePlan(feePlanId, request)));
    }

    @PutMapping("/fee-plans/{feePlanId}/status")
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    @Operation(summary = "Retire or reinstate a fee plan",
            description = "ACTIVE or INACTIVE. A retired plan cannot be billed against, but the "
                    + "row and every invoice that referenced it survive.")
    public ResponseEntity<ApiResponse<FeePlanResponse>> updateStatus(
            @PathVariable Long feePlanId,
            @Valid @RequestBody FeePlanStatusRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Fee plan status updated",
                feePlanService.updateStatus(feePlanId, request.getStatus())));
    }
}
