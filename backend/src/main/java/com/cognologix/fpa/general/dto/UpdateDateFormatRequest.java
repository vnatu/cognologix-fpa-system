package com.cognologix.fpa.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateDateFormatRequest(
        @NotBlank
        @Pattern(regexp = "DD MMM YYYY|DD/MM/YYYY|MM/DD/YYYY")
        String format
) {}
