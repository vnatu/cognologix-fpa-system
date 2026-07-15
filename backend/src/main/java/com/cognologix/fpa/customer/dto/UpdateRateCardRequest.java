package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.RateCardType;
import com.cognologix.fpa.customer.domain.RateCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Edit via versioning: closes the current card at {@code effectiveTo} and creates
 * a new version starting at {@code effectiveFrom} (ADR-035). Both dates required —
 * no defaults.
 */
public record UpdateRateCardRequest(
        @NotNull LocalDate effectiveTo,
        @NotNull LocalDate effectiveFrom,
        @NotBlank @Size(max = 255) String name,
        @NotNull RateCardType rateCardType,
        @NotNull RateCurrency currency,
        @NotEmpty @Valid List<RateCardLineRequest> lines,
        /**
         * Optional — null inherits current associations; empty list = blended;
         * non-empty replaces associations for the new version.
         */
        List<UUID> projectCodeIds
) {}
