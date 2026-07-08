package com.cognologix.fpa.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RateCardLineRequest(
        @Size(max = 100) String jobLevel,
        @NotNull @DecimalMin("0.01") BigDecimal rateAmount
) {}
