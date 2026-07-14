package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.AlternateIdLink;

import java.time.Instant;
import java.util.UUID;

public record AlternateIdLinkResponse(
        UUID id,
        UUID employeeRegistryId,
        String employeeId,
        String alternateEmployeeNo,
        String mappedBy,
        Instant mappedAt
) {
    public static AlternateIdLinkResponse from(AlternateIdLink link) {
        return new AlternateIdLinkResponse(
                link.getId(),
                link.getEmployeeRegistry().getId(),
                link.getEmployeeRegistry().getEmployeeId(),
                link.getAlternateEmployeeNo(),
                link.getMappedBy(),
                link.getMappedAt());
    }
}
