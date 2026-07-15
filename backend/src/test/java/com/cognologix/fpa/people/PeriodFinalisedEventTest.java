package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

@RecordApplicationEvents
class PeriodFinalisedEventTest extends PeopleModuleIntegrationTest {

    @Autowired
    PeoplePayrollService peoplePayrollService;

    @Autowired
    PeriodVersionRepository periodVersionRepository;

    @Test
    void finalisePeriod_publishesPeriodFinalisedEvent(ApplicationEvents events) {
        var period = peoplePayrollService.createPeriod(4, 2026);
        var version = periodVersionRepository
                .findByPeriodIdOrderByVersionNumberDesc(period.getId())
                .getFirst();

        // Finalise requires MASTER_BUILT (ADR-018) — set directly for this unit of behaviour
        version.setStatus(PeriodStatus.MASTER_BUILT);
        periodVersionRepository.save(version);

        peoplePayrollService.finalisePeriod(version.getId());

        assertThat(events.stream(PeriodFinalisedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.periodVersionId()).isEqualTo(version.getId());
                    assertThat(event.periodMonth()).isEqualTo(4);
                    assertThat(event.periodYear()).isEqualTo(2026);
                });

        var reloaded = periodVersionRepository.findById(version.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PeriodStatus.FINALISED);
        assertThat(reloaded.isLatestFinalised()).isTrue();
    }
}
