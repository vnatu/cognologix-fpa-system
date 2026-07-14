package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.EmployeeRegistry;
import com.cognologix.fpa.people.domain.ExitDatePrecision;
import com.cognologix.fpa.people.domain.ExitStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRegistryResponse(
        UUID id,
        String employeeId,
        String fullName,
        LocalDate dateOfJoining,
        ExitStatus exitStatus,
        LocalDate exitDate,
        ExitDatePrecision exitDatePrecision
) {
    public static EmployeeRegistryResponse from(EmployeeRegistry e) {
        return new EmployeeRegistryResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getFullName(),
                e.getDateOfJoining(),
                e.getExitStatus(),
                e.getExitDate(),
                e.getExitDatePrecision());
    }
}
