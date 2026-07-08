package com.cognologix.fpa.customer.dto;

import com.cognologix.fpa.customer.domain.CustomerProjectCode;

import java.util.UUID;

public record ProjectCodeResponse(UUID id, String projectCode, String description) {
    public static ProjectCodeResponse from(CustomerProjectCode pc) {
        return new ProjectCodeResponse(pc.getId(), pc.getProjectCode(), pc.getDescription());
    }
}
