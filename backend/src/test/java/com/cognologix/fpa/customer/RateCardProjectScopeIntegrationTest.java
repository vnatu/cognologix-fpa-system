package com.cognologix.fpa.customer;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.customer.domain.RateCardLine;
import com.cognologix.fpa.customer.domain.RateCardType;
import com.cognologix.fpa.customer.domain.RateCurrency;
import com.cognologix.fpa.customer.repository.CustomerRepository;
import com.cognologix.fpa.customer.repository.RateCardProjectCodeRepository;
import com.cognologix.fpa.customer.repository.RateCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class RateCardProjectScopeIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customerRepository;
    @Autowired RateCardRepository rateCardRepository;
    @Autowired RateCardProjectCodeRepository rateCardProjectCodeRepository;

    private UUID customerId;
    private UUID engnId;
    private UUID cloudOpsId;
    private UUID devopsId;
    private UUID aiMlId;
    private UUID otherProjId;

    @BeforeEach
    void setUp() {
        rateCardRepository.deleteAll();
        customerRepository.deleteAll();
        var customer = customerService.createCustomer(
                "ICERTI", "Icertis", null, null, LifecycleStatus.ACTIVE, 45);
        customerId = customer.getId();
        engnId = customerService.addProjectCode(customerId, "ENGN", "Engineering").getId();
        cloudOpsId = customerService.addProjectCode(customerId, "CLOUD_OPS", "Cloud Ops").getId();
        devopsId = customerService.addProjectCode(customerId, "DEV_OPS", "DevOps").getId();
        aiMlId = customerService.addProjectCode(customerId, "AI_ML", "AI/ML").getId();
        otherProjId = customerService.addProjectCode(customerId, "OTHER", "Other").getId();
    }

    private static List<RateCardLine> flatLine(String amount) {
        return List.of(RateCardLine.builder().rateAmount(new BigDecimal(amount)).build());
    }

    @Test
    void rateCardWithFourProjectCodes_createdSuccessfully() throws Exception {
        var body = """
                {
                  "name": "FY2627 Multi",
                  "rateCardType": "FLAT",
                  "currency": "INR",
                  "effectiveFrom": "2026-04-01",
                  "projectCodeIds": ["%s","%s","%s","%s"],
                  "lines": [{"rateAmount": 160000}]
                }
                """.formatted(engnId, cloudOpsId, devopsId, aiMlId);

        mockMvc.perform(post("/api/customers/{id}/rate-cards", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("FY2627 Multi"))
                .andExpect(jsonPath("$.projectCodes.length()").value(4))
                .andExpect(jsonPath("$.projectCodes[?(@.projectCode=='ENGN')]").exists())
                .andExpect(jsonPath("$.projectCodes[?(@.projectCode=='CLOUD_OPS')]").exists())
                .andExpect(jsonPath("$.projectCodes[?(@.projectCode=='DEV_OPS')]").exists())
                .andExpect(jsonPath("$.projectCodes[?(@.projectCode=='AI_ML')]").exists());

        var cards = rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId);
        assertThat(cards).hasSize(1);
        assertThat(rateCardProjectCodeRepository.findByRateCardId(cards.getFirst().getId())).hasSize(4);
    }

    @Test
    void secondRateCard_nonOverlappingProjectCodes_succeeds() {
        customerService.createRateCard(
                customerId, "Card A", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1), flatLine("150000"),
                List.of(engnId, cloudOpsId));
        customerService.createRateCard(
                customerId, "Card B", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1), flatLine("180000"),
                List.of(devopsId, aiMlId));

        assertThat(rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId)).hasSize(2);
        assertThat(customerService.findActiveRateCardForProjectCode(
                customerId, engnId, LocalDate.of(2026, 6, 1)))
                .get().extracting(c -> c.getName()).isEqualTo("Card A");
        assertThat(customerService.findActiveRateCardForProjectCode(
                customerId, devopsId, LocalDate.of(2026, 6, 1)))
                .get().extracting(c -> c.getName()).isEqualTo("Card B");
    }

    @Test
    void duplicateProjectCodeAcrossTwoActiveRateCards_returns409() throws Exception {
        customerService.createRateCard(
                customerId, "Existing", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1), flatLine("150000"),
                List.of(engnId, cloudOpsId));

        var body = """
                {
                  "name": "Conflict",
                  "rateCardType": "FLAT",
                  "currency": "INR",
                  "effectiveFrom": "2026-04-01",
                  "projectCodeIds": ["%s","%s"],
                  "lines": [{"rateAmount": 170000}]
                }
                """.formatted(cloudOpsId, otherProjId);

        mockMvc.perform(post("/api/customers/{id}/rate-cards", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "Project code [CLOUD_OPS] is already assigned to active rate card [Existing]")));
    }

    @Test
    void findActiveRateCardForProjectCode_returnsCorrectCard() {
        customerService.createRateCard(
                customerId, "Blended", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1), flatLine("100000"), List.of());
        customerService.createRateCard(
                customerId, "Scoped", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1), flatLine("150000"),
                List.of(engnId, cloudOpsId, devopsId, aiMlId));

        var asOf = LocalDate.of(2026, 6, 15);
        assertThat(customerService.findActiveRateCardForProjectCode(customerId, cloudOpsId, asOf))
                .get().extracting(c -> c.getName()).isEqualTo("Scoped");
        assertThat(customerService.findActiveBlendedRateCard(customerId, asOf))
                .get().extracting(c -> c.getName()).isEqualTo("Blended");
        assertThat(customerService.findActiveRateCardForProjectCode(customerId, otherProjId, asOf))
                .isEmpty();
    }

    @Test
    void blendedRateCard_duplicateActive_returnsConflict() {
        customerService.createRateCard(
                customerId, "Blended A", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1), flatLine("100000"), List.of());

        assertThatThrownBy(() -> customerService.createRateCard(
                customerId, "Blended B", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 6, 1), flatLine("110000"), List.of()))
                .isInstanceOf(CustomerConflictException.class);
    }

    @Test
    void edit_createsNewVersionAndClosesOld() throws Exception {
        var current = customerService.createRateCard(
                customerId, "FY2526 Standard", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2025, 4, 1), flatLine("150000"), List.of(engnId));

        var body = """
                {
                  "effectiveTo": "2026-03-31",
                  "effectiveFrom": "2026-04-01",
                  "name": "FY2627 Standard",
                  "rateCardType": "FLAT",
                  "currency": "INR",
                  "projectCodeIds": ["%s","%s"],
                  "lines": [{"rateAmount": 165000}]
                }
                """.formatted(engnId, cloudOpsId);

        mockMvc.perform(put("/api/customers/{id}/rate-cards/{rateCardId}", customerId, current.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("FY2627 Standard"))
                .andExpect(jsonPath("$.projectCodes.length()").value(2));

        var old = rateCardRepository.findById(current.getId()).orElseThrow();
        assertThat(old.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId)).hasSize(2);
    }

    @Test
    void edit_missingEffectiveFrom_returns400() throws Exception {
        var current = customerService.createRateCard(
                customerId, "FY2526 Standard", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2025, 4, 1), flatLine("150000"), List.of());

        var body = """
                {
                  "effectiveTo": "2026-03-31",
                  "name": "FY2627 Standard",
                  "rateCardType": "FLAT",
                  "currency": "INR",
                  "lines": [{"rateAmount": 165000}]
                }
                """;

        mockMvc.perform(put("/api/customers/{id}/rate-cards/{rateCardId}", customerId, current.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
