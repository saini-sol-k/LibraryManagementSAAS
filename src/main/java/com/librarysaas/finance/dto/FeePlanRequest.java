package com.librarysaas.finance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Create or update payload for a fee plan.
 *
 * libraryId is deliberately absent: it comes from the path, so a caller cannot
 * move a plan into another tenant by editing the body. Status is changed through
 * its own endpoint. Whether the name is free within the library is decided by
 * FeePlanService.
 *
 * The amount is a BigDecimal so it is parsed as exact decimal, matching the
 * DECIMAL(12,2) column. No monetary value in this module is ever a float.
 */
public class FeePlanRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 250, message = "Description must not exceed 250 characters")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    @Digits(integer = 10, fraction = 2,
            message = "Amount must have at most 10 digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Duration value is required")
    @Positive(message = "Duration value must be a positive number")
    private Integer durationValue;

    @NotBlank(message = "Duration unit is required")
    @Size(max = 20, message = "Duration unit must not exceed 20 characters")
    private String durationUnit;

    public FeePlanRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }

    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
}
