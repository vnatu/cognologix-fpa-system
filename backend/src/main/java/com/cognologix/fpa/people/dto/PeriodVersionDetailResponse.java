package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PeriodVersionDetailResponse(
        UUID id,
        UUID periodId,
        int versionNumber,
        PeriodStatus status,
        boolean isLatestFinalised,
        Instant createdAt,
        Instant finalisedAt,
        String finalisedBy,
        List<SnapshotUploadSummary> uploads,
        Map<ReconciliationStatus, Long> masterCountsByReconciliationStatus
) {
    public static PeriodVersionDetailResponse from(
            PeriodVersion v,
            List<SnapshotUpload> uploads,
            Map<ReconciliationStatus, Long> counts) {
        return new PeriodVersionDetailResponse(
                v.getId(),
                v.getPeriod().getId(),
                v.getVersionNumber(),
                v.getStatus(),
                v.isLatestFinalised(),
                v.getCreatedAt(),
                v.getFinalisedAt(),
                v.getFinalisedBy(),
                uploads.stream().map(SnapshotUploadSummary::from).toList(),
                counts);
    }
}
