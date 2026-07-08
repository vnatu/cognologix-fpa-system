package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.RateCardLine;

import java.math.BigDecimal;
import java.util.UUID;

public record RateCardLineResponse(UUID id, String jobLevel, BigDecimal rateAmount) {
    public static RateCardLineResponse from(RateCardLine l) {
        return new RateCardLineResponse(l.getId(), l.getJobLevel(), l.getRateAmount());
    }
}
