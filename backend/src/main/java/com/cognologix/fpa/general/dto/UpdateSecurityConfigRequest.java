package com.cognologix.fpa.general.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSecurityConfigRequest(
        @NotNull @Min(1) @Max(24) Integer jwtExpiryHours,
        @NotNull @Min(5) @Max(120) Integer inactivityTimeoutMinutes
) {}
