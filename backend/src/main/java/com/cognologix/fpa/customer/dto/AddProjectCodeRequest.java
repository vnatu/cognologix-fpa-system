package com.cognologix.fpa.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddProjectCodeRequest(
        @NotBlank @Size(max = 50) String projectCode,
        @Size(max = 500) String description
) {}
