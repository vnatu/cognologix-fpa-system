package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.RateCurrency;
import com.cognologix.fpa.customer.domain.RateCardType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateRateCardRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull RateCardType rateCardType,
        @NotNull RateCurrency currency,
        @NotNull LocalDate effectiveFrom,
        @NotEmpty @Valid List<RateCardLineRequest> lines
) {}
