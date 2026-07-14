package com.cognologix.fpa.people;

import com.cognologix.fpa.people.dto.MasterRecordResponse;
import com.cognologix.fpa.people.dto.MasterSummaryResponse;
import com.cognologix.fpa.people.dto.ReconcileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/people/master")
@RequiredArgsConstructor
@Tag(name = "People — Master Data", description = "Joined master records, summary, and manual reconciliation")
public class MasterDataController {

    private final PeoplePayrollService peoplePayrollService;

    @GetMapping("/{periodVersionId}")
    @Operation(summary = "List all master records for a period version")
    public List<MasterRecordResponse> list(@PathVariable UUID periodVersionId) {
        return peoplePayrollService.findMasterRecords(periodVersionId).stream()
                .map(MasterRecordResponse::from)
                .toList();
    }

    @GetMapping("/{periodVersionId}/summary")
    @Operation(summary = "Aggregated headcount and gross_pay by classification and BU")
    public MasterSummaryResponse summary(@PathVariable UUID periodVersionId) {
        return MasterSummaryResponse.from(peoplePayrollService.summarizeMaster(periodVersionId));
    }

    @PostMapping("/{periodVersionId}/reconcile")
    @Operation(summary = "Manually map an unmatched payroll row to an employee registry entry")
    public MasterRecordResponse reconcile(
            @PathVariable UUID periodVersionId,
            @Valid @RequestBody ReconcileRequest req,
            Authentication auth) {
        String mappedBy = auth != null ? auth.getName() : "system";
        return MasterRecordResponse.from(peoplePayrollService.reconcileManually(
                periodVersionId, req.payrollSnapshotId(), req.employeeRegistryId(), mappedBy));
    }
}
