package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.domain.SnapshotUpload;

import java.util.List;
import java.util.UUID;

public record SnapshotDetailResponse(
        UUID periodVersionId,
        int periodMonth,
        int periodYear,
        int versionNumber,
        ImportType importType,
        SnapshotUploadMetadataResponse upload,
        List<SnapshotUploadMetadataResponse> payrollUploads,
        List<PeopleSnapshotDetailResponse> peopleRows,
        List<PayrollSnapshotDetailResponse> payrollRows,
        List<ExitedRegistryDetailResponse> exitedRegistryRows
) {
    public static SnapshotDetailResponse of(
            PeriodVersion version,
            SnapshotUpload upload,
            List<PeopleSnapshotDetailResponse> peopleRows,
            List<PayrollSnapshotDetailResponse> payrollRows,
            List<ExitedRegistryDetailResponse> exitedRegistryRows) {
        return new SnapshotDetailResponse(
                version.getId(),
                version.getPeriod().getPeriodMonth(),
                version.getPeriod().getPeriodYear(),
                version.getVersionNumber(),
                upload.getImportType(),
                SnapshotUploadMetadataResponse.from(upload),
                List.of(),
                peopleRows,
                payrollRows,
                exitedRegistryRows);
    }

    public static SnapshotDetailResponse ofPayroll(
            PeriodVersion version,
            ImportType responseImportType,
            SnapshotUpload primaryUpload,
            List<SnapshotUpload> allPayrollUploads,
            List<PayrollSnapshotDetailResponse> payrollRows) {
        return new SnapshotDetailResponse(
                version.getId(),
                version.getPeriod().getPeriodMonth(),
                version.getPeriod().getPeriodYear(),
                version.getVersionNumber(),
                responseImportType,
                SnapshotUploadMetadataResponse.from(primaryUpload),
                allPayrollUploads.stream().map(SnapshotUploadMetadataResponse::from).toList(),
                List.of(),
                payrollRows,
                List.of());
    }
}
