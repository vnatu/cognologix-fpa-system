package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.EmployeeRegistry;
import com.cognologix.fpa.people.domain.ExitDatePrecision;
import com.cognologix.fpa.people.domain.ExitStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ExitedRegistryDetailResponse(
        UUID id,
        String employeeId,
        String fullName,
        LocalDate exitDate,
        ExitDatePrecision exitDatePrecision,
        ExitStatus exitStatus
) {
    public static ExitedRegistryDetailResponse from(EmployeeRegistry e) {
        return new ExitedRegistryDetailResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getFullName(),
                e.getExitDate(),
                e.getExitDatePrecision(),
                e.getExitStatus());
    }
}
