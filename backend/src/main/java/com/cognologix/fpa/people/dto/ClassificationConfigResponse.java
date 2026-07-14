package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ClassificationConfig;
import com.cognologix.fpa.people.domain.ClassificationConfigType;

import java.util.UUID;

public record ClassificationConfigResponse(
        UUID id,
        ClassificationConfigType configType,
        String value
) {
    public static ClassificationConfigResponse from(ClassificationConfig c) {
        return new ClassificationConfigResponse(c.getId(), c.getConfigType(), c.getValue());
    }
}
