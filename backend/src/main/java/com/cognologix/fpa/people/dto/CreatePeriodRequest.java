package com.cognologix.fpa.people.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePeriodRequest(
        @NotNull @Min(1) @Max(12) Integer periodMonth,
        @NotNull @Min(2000) @Max(2100) Integer periodYear
) {}
