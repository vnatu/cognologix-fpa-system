package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.people.domain.ExitStatus;
import com.cognologix.fpa.people.domain.MasterRecord;
import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.domain.ReconciliationStatus;
import com.cognologix.fpa.people.repository.EmployeeRegistryRepository;
import com.cognologix.fpa.people.repository.MasterRecordRepository;
import com.cognologix.fpa.people.repository.PeriodRepository;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class PeopleDashboardIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger PERIOD_MONTH = new AtomicInteger(1);
    private static final AtomicInteger EMP_SEQ = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired PeriodRepository periodRepository;
    @Autowired PeriodVersionRepository periodVersionRepository;
    @Autowired MasterRecordRepository masterRecordRepository;
    @Autowired EmployeeRegistryRepository employeeRegistryRepository;
    @Autowired CustomerService customerService;

    @Test
    void summary_returnsCorrectHeadcountAndBillableRatio() throws Exception {
        PeriodVersion version = createPeriodVersion(PeriodStatus.MASTER_BUILT, false);

        // 3 billable, 1 bench, 1 leadership → total 5, billable ratio 60.00
        saveMaster(version, "Product Engineering", "Icertis", true, false, false, false, false,
                "100000", ReconciliationStatus.MATCHED, null);
        saveMaster(version, "Product Engineering", "Icertis", true, false, false, false, false,
                "200000", ReconciliationStatus.MATCHED, null);
        saveMaster(version, "Product Engineering", "Icertis", true, false, false, false, false,
                null, ReconciliationStatus.PAYROLL_PENDING, null);
        saveMaster(version, "Product Engineering", "Pool", false, true, false, false, false,
                "50000", ReconciliationStatus.MATCHED, null);
        saveMaster(version, "Product Engineering", "Leadership", false, false, false, true, false,
                "300000", ReconciliationStatus.MATCHED, null);

        mockMvc.perform(get("/api/people/dashboard/{id}/summary", version.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount.total").value(5))
                .andExpect(jsonPath("$.headcount.billable").value(3))
                .andExpect(jsonPath("$.headcount.bench").value(1))
                .andExpect(jsonPath("$.headcount.leadership").value(1))
                .andExpect(jsonPath("$.headcount.billableRatioPct").value(60.00))
                .andExpect(jsonPath("$.salaryMetrics.billableGrossPay").value(300000.00))
                .andExpect(jsonPath("$.salaryMetrics.avgPerHeadBillable").value(150000.00));
    }

    @Test
    void summary_separatesInternalBuFromClientBreakdown() throws Exception {
        String clientCode = "DASH" + EMP_SEQ.getAndIncrement();
        customerService.createCustomer(
                clientCode, "Dash Client " + clientCode, null, null, LifecycleStatus.ACTIVE, 45);

        PeriodVersion version = createPeriodVersion(PeriodStatus.MASTER_BUILT, false);
        saveMaster(version, "Product Engineering", clientCode, true, false, false, false, false,
                "100000", ReconciliationStatus.MATCHED, null);
        saveMaster(version, "Product Engineering", "Leadership", false, false, false, true, false,
                "200000", ReconciliationStatus.MATCHED, null);
        saveMaster(version, "Product Engineering", "Management", false, false, false, false, true,
                "150000", ReconciliationStatus.MATCHED, null);

        mockMvc.perform(get("/api/people/dashboard/{id}/summary", version.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientBreakdown", hasSize(1)))
                .andExpect(jsonPath("$.clientBreakdown[0].businessUnit").value(clientCode))
                .andExpect(jsonPath("$.clientBreakdown[0].isInternal").value(false))
                .andExpect(jsonPath("$.internalBuBreakdown", hasSize(2)));
    }

    @Test
    void trend_returnsOnlyFinalisedVersionsSortedAsc() throws Exception {
        PeriodVersion open = createPeriodVersion(PeriodStatus.OPEN, false);
        PeriodVersion jan = createPeriodVersionFixed(1, 2025, PeriodStatus.FINALISED, true);
        PeriodVersion feb = createPeriodVersionFixed(2, 2025, PeriodStatus.FINALISED, true);
        PeriodVersion mar = createPeriodVersionFixed(3, 2025, PeriodStatus.FINALISED, true);

        saveMaster(jan, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);
        saveMaster(feb, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);
        saveMaster(feb, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);
        saveMaster(mar, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);
        saveMaster(mar, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);
        saveMaster(mar, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);
        saveMaster(open, "PU", "BU", true, false, false, false, false,
                "10", ReconciliationStatus.MATCHED, null);

        mockMvc.perform(get("/api/people/dashboard/trend").param("metric", "TOTAL_HC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodMonth").value(1))
                .andExpect(jsonPath("$[0].periodYear").value(2025))
                .andExpect(jsonPath("$[0].value").value(1))
                .andExpect(jsonPath("$[1].periodMonth").value(2))
                .andExpect(jsonPath("$[1].periodYear").value(2025))
                .andExpect(jsonPath("$[1].value").value(2))
                .andExpect(jsonPath("$[2].periodMonth").value(3))
                .andExpect(jsonPath("$[2].periodYear").value(2025))
                .andExpect(jsonPath("$[2].value").value(3))
                .andExpect(jsonPath("$", hasSize(3)));

        String body = mockMvc.perform(get("/api/people/dashboard/trend").param("metric", "BILLABLE_HC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"periodYear\":2030");
        assertThat(open.getStatus()).isEqualTo(PeriodStatus.OPEN);
    }

    private PeriodVersion createPeriodVersion(PeriodStatus status, boolean latestFinalised) {
        int month = PERIOD_MONTH.getAndIncrement();
        if (month > 12) {
            throw new IllegalStateException("Too many periods in test class");
        }
        return createPeriodVersionFixed(month, 2030, status, latestFinalised);
    }

    private PeriodVersion createPeriodVersionFixed(
            int month, int year, PeriodStatus status, boolean latestFinalised) {
        Period period = periodRepository.save(Period.builder()
                .periodMonth(month)
                .periodYear(year)
                .build());
        return periodVersionRepository.save(PeriodVersion.builder()
                .period(period)
                .versionNumber(1)
                .status(status)
                .latestFinalised(latestFinalised)
                .createdBy("test")
                .build());
    }

    private void saveMaster(
            PeriodVersion version,
            String practiceUnit,
            String businessUnit,
            boolean billable,
            boolean bench,
            boolean support,
            boolean leadership,
            boolean management,
            String grossPay,
            ReconciliationStatus reconciliationStatus,
            String dataQualityFlags) {
        EmployeeRegistry registry = employeeRegistryRepository.save(EmployeeRegistry.builder()
                .employeeId("DASH-EMP-" + EMP_SEQ.getAndIncrement())
                .fullName("Dash Emp")
                .exitStatus(ExitStatus.ACTIVE)
                .build());
        masterRecordRepository.save(MasterRecord.builder()
                .periodVersion(version)
                .employeeRegistry(registry)
                .practiceUnit(practiceUnit)
                .businessUnit(businessUnit)
                .billableStatus(billable ? "Y" : "N")
                .grossPay(grossPay == null ? null : new BigDecimal(grossPay))
                .billable(billable)
                .bench(bench)
                .support(support)
                .leadership(leadership)
                .management(management)
                .reconciliationStatus(reconciliationStatus)
                .dataQualityFlags(dataQualityFlags)
                .builtBy("test")
                .build());
    }
}
