package com.cognologix.fpa.general.dto;

import com.cognologix.fpa.general.FxRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FxRateResponse(
        UUID id,
        String currencyPair,
        BigDecimal rate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String createdBy
) {
    public static FxRateResponse from(FxRate f) {
        return new FxRateResponse(f.getId(), f.getCurrencyPair(), f.getRate(),
                f.getEffectiveFrom(), f.getEffectiveTo(), f.getCreatedBy());
    }
}
