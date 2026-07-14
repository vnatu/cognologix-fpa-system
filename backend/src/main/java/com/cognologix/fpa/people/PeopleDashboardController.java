package com.cognologix.fpa.people;

import com.cognologix.fpa.people.dto.DashboardPeriodResponse;
import com.cognologix.fpa.people.dto.DashboardSummaryResponse;
import com.cognologix.fpa.people.dto.DashboardTrendMetric;
import com.cognologix.fpa.people.dto.DashboardTrendPointResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/people/dashboard")
@RequiredArgsConstructor
@Tag(name = "People — Dashboard", description = "People Analytics dashboard aggregates and trends (spec §10)")
public class PeopleDashboardController {

    private final PeopleDashboardService peopleDashboardService;

    @GetMapping("/periods")
    @Operation(summary = "List all periods with versions for the dashboard period/version selector")
    public List<DashboardPeriodResponse> listPeriods() {
        return peopleDashboardService.listPeriodsForSelector();
    }

    @GetMapping("/{periodVersionId}/summary")
    @Operation(summary = "Full dashboard payload for a period version, computed from master_record rows")
    public DashboardSummaryResponse summary(@PathVariable UUID periodVersionId) {
        return peopleDashboardService.getSummary(periodVersionId);
    }

    @GetMapping("/trend")
    @Operation(summary = "Trend of a metric across FINALISED period versions only, sorted by period ASC")
    public List<DashboardTrendPointResponse> trend(
            @RequestParam DashboardTrendMetric metric,
            @RequestParam(required = false) String practiceUnit,
            @RequestParam(required = false) String businessUnit) {
        return peopleDashboardService.getTrend(metric, practiceUnit, businessUnit);
    }
}
