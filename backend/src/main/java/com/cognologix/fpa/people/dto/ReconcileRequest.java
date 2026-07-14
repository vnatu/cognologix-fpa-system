package com.cognologix.fpa.people.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReconcileRequest(
        @NotNull UUID payrollSnapshotId,
        @NotNull UUID employeeRegistryId
) {}
