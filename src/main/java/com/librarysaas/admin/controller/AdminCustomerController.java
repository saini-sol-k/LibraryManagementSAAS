package com.librarysaas.admin.controller;

import com.librarysaas.admin.dto.CustomerOnboardingRequest;
import com.librarysaas.admin.dto.CustomerOnboardingResponse;
import com.librarysaas.admin.service.CustomerOnboardingService;
import com.librarysaas.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform administration: onboarding a paying customer onto the SaaS.
 *
 * This is the only place in the application that creates a tenant from nothing.
 * It sits under /api/admin rather than beside the tenant-scoped resources,
 * because it is not scoped to a tenant at all - it is the product owner acting on
 * the platform.
 *
 * There is exactly one endpoint and it is a POST. No GET is offered on purpose:
 * the creation response carries the customer's initial password, and an endpoint
 * that could return it a second time would turn a one-time secret into a stored
 * one. Existing endpoints already list organizations and libraries for anyone
 * entitled to see them.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Platform Administration",
        description = "Super-admin-only onboarding of new SaaS customers")
public class AdminCustomerController {

    private final CustomerOnboardingService customerOnboardingService;

    @Autowired
    public AdminCustomerController(CustomerOnboardingService customerOnboardingService) {
        this.customerOnboardingService = customerOnboardingService;
    }

    /**
     * The service repeats this authority check. That is deliberate rather than
     * redundant: the annotation keeps the restriction visible in the OpenAPI
     * document and rejects the call before any argument is bound, while the
     * service-level check means the boundary survives any future caller that does
     * not arrive through this controller.
     */
    @PostMapping("/customers")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Onboard a new SaaS customer",
            description = "Creates an organization, a library under it, and the customer's own "
                    + "administrator with the ORGANIZATION_OWNER role, in a single transaction. "
                    + "Requires SUPER_ADMIN. The response carries a generated initial password "
                    + "and is the only time it is available: it is stored as a BCrypt hash and "
                    + "no endpoint can return it again.")
    public ResponseEntity<ApiResponse<CustomerOnboardingResponse>> onboardCustomer(
            @Valid @RequestBody CustomerOnboardingRequest request) {
        CustomerOnboardingResponse response = customerOnboardingService.onboardCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Customer onboarded", response));
    }
}
