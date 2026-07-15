package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.RateCard;
import com.cognologix.fpa.customer.domain.RateCardType;
import com.cognologix.fpa.customer.domain.RateCurrency;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RateCardResponse(
        UUID id,
        String name,
        RateCardType rateCardType,
        RateCurrency currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<ProjectCodeSummary> projectCodes,
        List<RateCardLineResponse> lines
) {
    public static RateCardResponse from(RateCard rc) {
        List<ProjectCodeSummary> codes = rc.getProjectCodeSummaries() == null
                ? List.of()
                : rc.getProjectCodeSummaries().stream()
                        .map(s -> new ProjectCodeSummary(s.id(), s.projectCode(), s.description()))
                        .toList();
        return new RateCardResponse(
                rc.getId(),
                rc.getName(),
                rc.getRateCardType(),
                rc.getCurrency(),
                rc.getEffectiveFrom(),
                rc.getEffectiveTo(),
                codes,
                rc.getLines().stream().map(RateCardLineResponse::from).toList());
    }
}
