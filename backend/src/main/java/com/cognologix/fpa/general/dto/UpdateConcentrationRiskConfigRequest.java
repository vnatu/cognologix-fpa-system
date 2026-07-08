package com.cognologix.fpa.general.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateConcentrationRiskConfigRequest(
        @NotNull @DecimalMin("1.00") @DecimalMax("100.00") BigDecimal singleClientThresholdPct
) {}
