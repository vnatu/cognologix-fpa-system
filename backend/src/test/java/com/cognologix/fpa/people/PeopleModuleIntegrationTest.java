package com.cognologix.fpa.people;

import com.cognologix.fpa.customer.CustomerService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared {@code @ApplicationModuleTest} setup for the people module.
 * Starts Postgres before the Spring context so {@code @ServiceConnection}
 * can resolve the JDBC URL (ApplicationModuleTest can race Testcontainers JUnit lifecycle).
 */
@ApplicationModuleTest
abstract class PeopleModuleIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** Cross-module dependency — mocked so the people slice does not need the customer module. */
    @MockBean
    CustomerService customerService;
}
