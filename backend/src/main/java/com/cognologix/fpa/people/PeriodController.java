package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/people/periods")
@RequiredArgsConstructor
@Tag(name = "People — Period Management", description = "Reporting periods, versions, master build, and finalisation")
public class PeriodController {

    private final PeoplePayrollService peoplePayrollService;

    @GetMapping
    @Operation(summary = "List all periods with their versions")
    public List<PeriodResponse> listPeriods() {
        return peoplePayrollService.findAllPeriods().stream()
                .map(p -> PeriodResponse.from(p, peoplePayrollService.findVersionsForPeriod(p.getId())))
                .toList();
    }

    @PostMapping
    @Operation(summary = "Create a new period (auto-creates version 1 OPEN). Returns 409 if month/year exists.")
    public ResponseEntity<PeriodResponse> createPeriod(@Valid @RequestBody CreatePeriodRequest req) {
        var period = peoplePayrollService.createPeriod(req.periodMonth(), req.periodYear());
        var versions = peoplePayrollService.findVersionsForPeriod(period.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PeriodResponse.from(period, versions));
    }

    @GetMapping("/{periodId}/versions/{versionId}")
    @Operation(summary = "Get full detail of a period version including uploads and master reconciliation counts")
    public PeriodVersionDetailResponse getVersion(
            @PathVariable UUID periodId,
            @PathVariable UUID versionId) {
        PeriodVersion version = peoplePayrollService.getPeriodVersion(periodId, versionId);
        return PeriodVersionDetailResponse.from(
                version,
                peoplePayrollService.findUploadsForVersion(versionId),
                peoplePayrollService.countMasterByReconciliationStatus(versionId));
    }

    @PostMapping("/{periodId}/versions")
    @Operation(summary = "Create a new version (post-finalisation correction). Requires latest version FINALISED.")
    public ResponseEntity<PeriodVersionSummary> createVersion(
            @PathVariable UUID periodId,
            Authentication auth) {
        String createdBy = auth != null ? auth.getName() : "system";
        PeriodVersion version = peoplePayrollService.createPeriodVersion(periodId, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(PeriodVersionSummary.from(version));
    }

    @PostMapping("/{periodId}/versions/{versionId}/build-master")
    @Operation(summary = "Trigger Master record build. Only when status is SNAPSHOTS_UPLOADED.")
    public List<MasterRecordResponse> buildMaster(
            @PathVariable UUID periodId,
            @PathVariable UUID versionId) {
        peoplePayrollService.getPeriodVersion(periodId, versionId);
        return peoplePayrollService.buildMasterRecords(versionId).stream()
                .map(MasterRecordResponse::from)
                .toList();
    }

    @PostMapping("/{periodId}/versions/{versionId}/finalise")
    @Operation(summary = "Finalise version. Only when MASTER_BUILT. Publishes PeriodFinalisedEvent.")
    public ResponseEntity<Void> finalise(
            @PathVariable UUID periodId,
            @PathVariable UUID versionId,
            Authentication auth) {
        peoplePayrollService.getPeriodVersion(periodId, versionId);
        String by = auth != null ? auth.getName() : "system";
        peoplePayrollService.finalisePeriod(versionId, by);
        return ResponseEntity.noContent().build();
    }
}
