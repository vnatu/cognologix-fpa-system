package com.cognologix.fpa.people;

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

        peoplePayrollService.finalisePeriod(version.getId());

        assertThat(events.stream(PeriodFinalisedEvent.class))
                .singleElement()
                .extracting(PeriodFinalisedEvent::periodVersionId)
                .isEqualTo(version.getId());
    }
}
