package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ClassificationConfigType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationConfigTest extends PeopleModuleIntegrationTest {

    @Autowired
    PeoplePayrollService peoplePayrollService;

    @Test
    void seededClassificationConfig_isPresentAfterMigration() {
        var delivery = peoplePayrollService
                .findClassificationByType(ClassificationConfigType.DELIVERY_PU)
                .stream()
                .map(c -> c.getValue())
                .toList();
        assertThat(delivery).containsExactlyInAnyOrder(
                "Product Engineering", "DevOps & Cloud", "Data & AI");

        var management = peoplePayrollService
                .findClassificationByType(ClassificationConfigType.MANAGEMENT_BU)
                .stream()
                .map(c -> c.getValue())
                .toList();
        assertThat(management).containsExactly("Management");

        var leadership = peoplePayrollService
                .findClassificationByType(ClassificationConfigType.LEADERSHIP_BU)
                .stream()
                .map(c -> c.getValue())
                .toList();
        assertThat(leadership).containsExactly("Leadership");

        assertThat(peoplePayrollService.findAllClassificationConfig()).hasSize(5);
    }
}
