package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodServiceTest extends PeopleModuleIntegrationTest {

    @Autowired
    PeoplePayrollService peoplePayrollService;

    @Autowired
    PeriodVersionRepository periodVersionRepository;

    @Test
    void createPeriod_createsPeriodAndOpenVersionOne() {
        var period = peoplePayrollService.createPeriod(3, 2026);

        assertThat(period.getId()).isNotNull();
        assertThat(period.getPeriodMonth()).isEqualTo(3);
        assertThat(period.getPeriodYear()).isEqualTo(2026);

        List<PeriodVersion> versions =
                periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(period.getId());
        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().getVersionNumber()).isEqualTo(1);
        assertThat(versions.getFirst().getStatus()).isEqualTo(PeriodStatus.OPEN);
    }
}
