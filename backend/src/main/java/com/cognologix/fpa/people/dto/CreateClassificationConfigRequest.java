package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ClassificationConfigType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateClassificationConfigRequest(
        @NotNull ClassificationConfigType configType,
        @NotBlank @Size(max = 255) String value
) {}
