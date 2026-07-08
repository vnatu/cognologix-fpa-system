package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ExitStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRegistryTest extends PeopleModuleIntegrationTest {

    @Autowired
    PeoplePayrollService peoplePayrollService;

    @Test
    void registerEmployee_persistsAndRetrievesByEmployeeId() {
        var registered = peoplePayrollService.registerEmployee("EMP0042", "Ada Lovelace");

        assertThat(registered.getId()).isNotNull();
        assertThat(registered.getEmployeeId()).isEqualTo("EMP0042");
        assertThat(registered.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(registered.getExitStatus()).isEqualTo(ExitStatus.ACTIVE);

        var found = peoplePayrollService.findEmployeeByEmployeeId("EMP0042");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Ada Lovelace");
        assertThat(found.get().getId()).isEqualTo(registered.getId());
    }
}
