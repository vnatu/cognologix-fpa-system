package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.people.domain.PeriodStatus;

import java.util.List;
import java.util.UUID;

public record SnapshotUploadResponse(
        UUID uploadId,
        UUID periodVersionId,
        int rowsImported,
        List<String> unmappedColumns,
        List<String> missingColumns,
        List<String> unrecognizedBuCodes,
        PeriodStatus periodVersionStatus
) {
    public static SnapshotUploadResponse from(PeoplePayrollService.SnapshotUploadResult r) {
        return new SnapshotUploadResponse(
                r.uploadId(),
                r.periodVersionId(),
                r.rowsImported(),
                r.unmappedColumns(),
                r.missingColumns(),
                r.unrecognizedBuCodes(),
                r.periodVersionStatus());
    }
}
