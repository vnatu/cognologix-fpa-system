package com.cognologix.fpa.general.dto;

import com.cognologix.fpa.general.ConcentrationRiskConfig;

import java.math.BigDecimal;
import java.util.UUID;

public record ConcentrationRiskConfigResponse(UUID id, BigDecimal singleClientThresholdPct) {
    public static ConcentrationRiskConfigResponse from(ConcentrationRiskConfig c) {
        return new ConcentrationRiskConfigResponse(c.getId(), c.getSingleClientThresholdPct());
    }
}
