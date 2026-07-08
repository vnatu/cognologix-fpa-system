package com.cognologix.fpa.general.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFxRateRequest(
        @NotBlank @Size(max = 10) String currencyPair,
        @NotNull @DecimalMin("0.0001") BigDecimal rate,
        @NotNull LocalDate effectiveFrom
) {}
