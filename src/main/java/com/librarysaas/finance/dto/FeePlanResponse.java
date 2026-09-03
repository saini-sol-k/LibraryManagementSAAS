package com.librarysaas.finance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import com.librarysaas.finance.entity.FeePlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One fee plan. Amounts stay BigDecimal all the way to the JSON. */
public class FeePlanResponse {

    private Long feePlanId;
    private Long libraryId;
    private String name;
    private String description;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal amount;
    private Integer durationValue;
    private String durationUnit;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FeePlanResponse from(FeePlan plan) {
        FeePlanResponse r = new FeePlanResponse();
        r.feePlanId = plan.getFeePlanId();
        if (plan.getLibrary() != null) {
            r.libraryId = plan.getLibrary().getLibraryId();
        }
        r.name = plan.getName();
        r.description = plan.getDescription();
        r.amount = plan.getAmount();
        r.durationValue = plan.getDurationValue();
        r.durationUnit = plan.getDurationUnit();
        r.status = plan.getStatus();
        r.createdAt = plan.getCreatedAt();
        r.updatedAt = plan.getUpdatedAt();
        return r;
    }

    public Long getFeePlanId() { return feePlanId; }
    public Long getLibraryId() { return libraryId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public Integer getDurationValue() { return durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
