package com.cognologix.fpa.people.dto;

import java.math.BigDecimal;

public record DashboardTrendPointResponse(
        int periodMonth,
        int periodYear,
        int versionNumber,
        BigDecimal value
) {}
