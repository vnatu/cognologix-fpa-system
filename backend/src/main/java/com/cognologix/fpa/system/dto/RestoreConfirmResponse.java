package com.cognologix.fpa.system.dto;

import java.util.List;
import java.util.Map;

public record RestoreConfirmResponse(
        Map<String, Integer> recordsRestored,
        List<String> errors,
        String message
) {}
