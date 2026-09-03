package com.librarysaas.finance.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.finance.dto.FeePlanRequest;
import com.librarysaas.finance.dto.FeePlanResponse;
import com.librarysaas.finance.entity.FeePlan;
import com.librarysaas.finance.repository.FeePlanRepository;
import com.librarysaas.finance.service.FeePlanService;
import com.librarysaas.library.entity.Library;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Fee plan rules.
 *
 * Writes are guarded by FEE_PLAN_CREATE, including updates: the schema defines
 * FEE_PLAN_VIEW and FEE_PLAN_CREATE and no FEE_PLAN_UPDATE, so inventing one
 * would need a migration to seed it and its role grants.
 *
 * Statuses are ACTIVE and INACTIVE. ACTIVE is the column default and the only
 * value the seed carries; INACTIVE is the retired counterpart, matching the
 * ACTIVE/INACTIVE pair the organization, library and staff-membership services
 * already use. No other value is admitted.
 */
@Service
public class FeePlanServiceImpl implements FeePlanService {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_INACTIVE = "INACTIVE";

    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE);

    private final FeePlanRepository feePlanRepository;
    private final FinanceTenantGuard guard;

    @Autowired
    public FeePlanServiceImpl(FeePlanRepository feePlanRepository, FinanceTenantGuard guard) {
        this.feePlanRepository = feePlanRepository;
        this.guard = guard;
    }

    /* ---------------------------------------------------------------- reads */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    public List<FeePlanResponse> getLibraryFeePlans(Long libraryId, String status) {
        guard.requireLibrary(libraryId);

        List<FeePlan> plans = status == null || status.isBlank()
                ? feePlanRepository.findAllByLibrary(libraryId)
                : feePlanRepository.findAllByLibraryAndStatus(libraryId, normaliseStatus(status));

        return plans.stream().map(FeePlanResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FEE_PLAN_VIEW')")
    public FeePlanResponse getFeePlan(Long feePlanId) {
        return FeePlanResponse.from(requireFeePlan(feePlanId));
    }

    /* --------------------------------------------------------------- writes */

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    public FeePlanResponse createFeePlan(Long libraryId, FeePlanRequest request) {
        Library library = guard.requireLibrary(libraryId);

        String name = request.getName().trim();
        if (feePlanRepository.existsByLibraryLibraryIdAndName(libraryId, name)) {
            throw new ConflictException("A fee plan named " + name + " already exists in this library",
                    "FEE_PLAN_NAME_ALREADY_EXISTS");
        }

        BigDecimal amount = requireNonNegativeAmount(request.getAmount());
        LocalDateTime now = LocalDateTime.now();
        Long actor = guard.currentUserId();

        FeePlan plan = new FeePlan();
        plan.setLibrary(library);
        plan.setName(name);
        plan.setDescription(trimToNull(request.getDescription()));
        plan.setAmount(amount);
        plan.setDurationValue(request.getDurationValue());
        plan.setDurationUnit(request.getDurationUnit().trim().toUpperCase());
        plan.setStatus(STATUS_ACTIVE);
        plan.setCreatedAt(now);
        plan.setCreatedBy(actor);
        plan.setUpdatedAt(now);
        plan.setUpdatedBy(actor);

        return FeePlanResponse.from(feePlanRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    public FeePlanResponse updateFeePlan(Long feePlanId, FeePlanRequest request) {
        FeePlan plan = requireFeePlan(feePlanId);
        Long libraryId = plan.getLibrary().getLibraryId();

        String name = request.getName().trim();
        if (feePlanRepository.existsByLibraryAndNameExcluding(libraryId, name, feePlanId)) {
            throw new ConflictException("A fee plan named " + name + " already exists in this library",
                    "FEE_PLAN_NAME_ALREADY_EXISTS");
        }

        // Repricing a plan deliberately does not touch invoices already raised
        // from it: student_fee stores its own amounts, so past billing stands.
        plan.setName(name);
        plan.setDescription(trimToNull(request.getDescription()));
        plan.setAmount(requireNonNegativeAmount(request.getAmount()));
        plan.setDurationValue(request.getDurationValue());
        plan.setDurationUnit(request.getDurationUnit().trim().toUpperCase());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setUpdatedBy(guard.currentUserId());

        return FeePlanResponse.from(feePlanRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('FEE_PLAN_CREATE')")
    public FeePlanResponse updateStatus(Long feePlanId, String status) {
        FeePlan plan = requireFeePlan(feePlanId);
        String newStatus = normaliseStatus(status);

        // Setting the status a plan already holds changes nothing, so it is
        // accepted as a no-op rather than treated as an error.
        if (newStatus.equals(plan.getStatus())) {
            return FeePlanResponse.from(plan);
        }

        plan.setStatus(newStatus);
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setUpdatedBy(guard.currentUserId());

        return FeePlanResponse.from(feePlanRepository.saveAndFlush(plan));
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves a plan and authorises against the library on its own row, so a
     * plan id from another tenant is refused rather than served.
     */
    private FeePlan requireFeePlan(Long feePlanId) {
        FeePlan plan = feePlanRepository.findById(feePlanId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Fee plan not found", "FEE_PLAN_NOT_FOUND"));

        Long libraryId = plan.getLibrary() == null ? null : plan.getLibrary().getLibraryId();
        if (libraryId == null) {
            throw new ResourceNotFoundException("Fee plan not found", "FEE_PLAN_NOT_FOUND");
        }
        guard.requireLibraryAccess(libraryId);
        return plan;
    }

    /**
     * Bean Validation already rejects a negative amount; this is the service-side
     * guarantee that no negative price can reach the money columns whatever the
     * entry point.
     */
    private BigDecimal requireNonNegativeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("Amount cannot be negative", "INVALID_FEE_AMOUNT");
        }
        return amount;
    }

    private String normaliseStatus(String requested) {
        String status = requested == null ? "" : requested.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    "Invalid fee plan status: " + requested + ". Allowed: "
                            + String.join(", ", ALLOWED_STATUSES),
                    "INVALID_FEE_PLAN_STATUS");
        }
        return status;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
