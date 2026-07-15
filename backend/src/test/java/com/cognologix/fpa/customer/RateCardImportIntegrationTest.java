package com.cognologix.fpa.customer;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.customer.domain.RateCardType;
import com.cognologix.fpa.customer.domain.RateCurrency;
import com.cognologix.fpa.customer.repository.CustomerRepository;
import com.cognologix.fpa.customer.repository.RateCardRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class RateCardImportIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customerRepository;
    @Autowired RateCardRepository rateCardRepository;

    @BeforeEach
    void cleanData() {
        rateCardRepository.deleteAll();
        customerRepository.deleteAll();
        customerService.createCustomer(
                "ICERTI", "Icertis", null, null, LifecycleStatus.ACTIVE, 45);
        customerService.createCustomer(
                "CADENT", "Cadent", null, null, LifecycleStatus.ACTIVE, 30);
    }

    @Test
    void importAllNew_createsFlatAndTieredRateCards() throws Exception {
        var file = xlsx(List.of(
                List.of("ICERTI", "", "FY2526 Standard", "FLAT", "INR", "2026-01-01", "", "150000"),
                List.of("CADENT", "", "FY2526 Tiered", "TIERED", "USD", "2026-04-01", "L3", "120000"),
                List.of("CADENT", "", "FY2526 Tiered", "TIERED", "USD", "2026-04-01", "L4", "150000")));

        mockMvc.perform(multipart("/api/customers/rate-cards/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.rateCardsCreated").value(2))
                .andExpect(jsonPath("$.rateCardsSkipped").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        var icertis = customerRepository.findByCustomerCode("ICERTI").orElseThrow();
        var icertisCards = rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(icertis.getId());
        assertThat(icertisCards).hasSize(1);
        assertThat(icertisCards.getFirst().getRateCardType()).isEqualTo(RateCardType.FLAT);
        assertThat(icertisCards.getFirst().getLines()).hasSize(1);
        assertThat(icertisCards.getFirst().getLines().getFirst().getRateAmount())
                .isEqualByComparingTo(new BigDecimal("150000"));

        var cadent = customerRepository.findByCustomerCode("CADENT").orElseThrow();
        var cadentCards = rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(cadent.getId());
        assertThat(cadentCards).hasSize(1);
        assertThat(cadentCards.getFirst().getRateCardType()).isEqualTo(RateCardType.TIERED);
        assertThat(cadentCards.getFirst().getLines()).hasSize(2);
    }

    @Test
    void import_skipsWhenActiveRateCardExists() throws Exception {
        var icertis = customerRepository.findByCustomerCode("ICERTI").orElseThrow();
        customerService.createRateCard(
                icertis.getId(),
                "Existing Card",
                RateCardType.FLAT,
                RateCurrency.INR,
                LocalDate.of(2025, 1, 1),
                List.of(com.cognologix.fpa.customer.domain.RateCardLine.builder()
                        .rateAmount(new BigDecimal("100000"))
                        .build()),
                List.of());

        var file = xlsx(List.of(
                List.of("ICERTI", "", "FY2526 Standard", "FLAT", "INR", "2026-01-01", "", "150000")));

        mockMvc.perform(multipart("/api/customers/rate-cards/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCardsCreated").value(0))
                .andExpect(jsonPath("$.rateCardsSkipped").value(1))
                .andExpect(jsonPath("$.skipped[0].customerCode").value("ICERTI"));

        var cards = rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(icertis.getId());
        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().getName()).isEqualTo("Existing Card");
    }

    @Test
    void import_unknownCustomerCode_returnsRowError() throws Exception {
        var file = xlsx(List.of(
                List.of("UNKNOWN", "", "FY2526", "FLAT", "INR", "2026-01-01", "", "150000")));

        mockMvc.perform(multipart("/api/customers/rate-cards/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCardsCreated").value(0))
                .andExpect(jsonPath("$.errors[0].customerCode").value("UNKNOWN"))
                .andExpect(jsonPath("$.errors[0].reason").value("Customer Code not found: UNKNOWN"));
    }

    @Test
    void import_flatWithMultipleLines_returnsGroupErrors() throws Exception {
        var file = xlsx(List.of(
                List.of("ICERTI", "", "FY2526 Standard", "FLAT", "INR", "2026-01-01", "", "150000"),
                List.of("ICERTI", "", "FY2526 Standard", "FLAT", "INR", "2026-01-01", "", "160000")));

        mockMvc.perform(multipart("/api/customers/rate-cards/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCardsCreated").value(0))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].reason")
                        .value("FLAT rate card allows only one line per group"));
    }

    @Test
    void downloadSample_returnsExcelTemplate() throws Exception {
        mockMvc.perform(get("/api/customers/rate-cards/import/sample"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"rate_card_import_template.xlsx\""))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private static MockMultipartFile xlsx(List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Rate Cards");
            var headerRow = sheet.createRow(0);
            String[] headers = {
                    "Customer Code", "Project Code", "Rate Card Name", "Rate Card Type", "Currency",
                    "Effective From", "Job Level", "Rate Amount"
            };
            for (int c = 0; c < headers.length; c++) {
                headerRow.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "rate-cards.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
