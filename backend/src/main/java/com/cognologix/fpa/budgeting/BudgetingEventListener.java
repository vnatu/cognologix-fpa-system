package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.people.PeriodFinalisedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Cross-module listener: snapshots People &amp; Payroll actuals into period_actuals (ADR-022).
 */
@Component
@RequiredArgsConstructor
class BudgetingEventListener {

    private final BudgetingService budgetingService;

    @ApplicationModuleListener
    void onPeriodFinalised(PeriodFinalisedEvent event) {
        budgetingService.onPeriodFinalised(event);
    }
}
