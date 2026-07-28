package com.cognologix.fpa.system.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RestoreDryRunResponse(
        String restoreToken,
        Instant expiresAt,
        String warning,
        List<String> filesPresent,
        List<String> filesMissing,
        Map<String, Integer> recordCounts
) {}
