package com.cognologix.fpa;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

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
                .contains("people", "customer", "general", "budgeting");
    }
}
