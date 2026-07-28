package com.cognologix.fpa;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTest {

    @Test
    void moduleStructureIsValid() {
        ApplicationModules modules = ApplicationModules.of(FpaApplication.class);
        modules.verify();

        Set<String> names = modules.stream()
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet());

        assertThat(names)
                .as("Spring Modulith must detect all bounded-context modules")
                .contains("people", "customer", "general", "budgeting", "revenue", "expenses",
                        "application", "system");
    }

    @Test
    void budgetingDependsOnRevenueForActuals() {
        ApplicationModules modules = ApplicationModules.of(FpaApplication.class);
        ApplicationModule budgeting = modules.getModuleByName("budgeting").orElseThrow();

        assertThat(budgeting.getDependencies(modules, DependencyType.USES_COMPONENT)
                .containsModuleNamed("revenue"))
                .as("Budgeting must depend on Revenue in-process for actual revenue figures (ADR-043)")
                .isTrue();
    }

    @Test
    void budgetingDependsOnExpensesForOverheadActuals() {
        ApplicationModules modules = ApplicationModules.of(FpaApplication.class);
        ApplicationModule budgeting = modules.getModuleByName("budgeting").orElseThrow();

        assertThat(budgeting.getDependencies(modules, DependencyType.USES_COMPONENT)
                .containsModuleNamed("expenses"))
                .as("Budgeting must depend on Expenses in-process for overhead actuals (ADR-050)")
                .isTrue();
    }
}
