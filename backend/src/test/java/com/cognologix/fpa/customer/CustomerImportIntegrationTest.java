package com.cognologix.fpa.customer;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.customer.repository.CommercialTermsRepository;
import com.cognologix.fpa.customer.repository.CustomerRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class CustomerImportIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customerRepository;
    @Autowired CommercialTermsRepository commercialTermsRepository;

    @BeforeEach
    void cleanCustomers() {
        commercialTermsRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void importAllNew_createsCustomersWithCommercialTerms() throws Exception {
        var file = xlsx(
                List.of("Customer Code", "Customer Name", "Zoho Books Customer Ref",
                        "Lifecycle Status", "DSO Days", "Relationship Owner Employee ID"),
                List.of(
                        List.of("ICERTI", "Icertis", "ZB-001", "ACTIVE", "45", "EMP001"),
                        List.of("CADENT", "Cadent", "", "", "", "")));

        mockMvc.perform(multipart("/api/customers/import")
                        .file(file)
                        .param("conflictResolution", "SKIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(customerRepository.findByCustomerCode("ICERTI")).isPresent();
        assertThat(customerRepository.findByCustomerCode("CADENT")).isPresent();

        var icertisTerms = commercialTermsRepository.findById(
                customerRepository.findByCustomerCode("ICERTI").orElseThrow().getId());
        assertThat(icertisTerms).isPresent();
        assertThat(icertisTerms.orElseThrow().getDsoDays()).isEqualTo(45);

        var cadentTerms = commercialTermsRepository.findById(
                customerRepository.findByCustomerCode("CADENT").orElseThrow().getId());
        assertThat(cadentTerms).isPresent();
        assertThat(cadentTerms.orElseThrow().getDsoDays()).isEqualTo(30);
    }

    @Test
    void importWithSkip_skipsExistingCustomers() throws Exception {
        customerRepository.save(com.cognologix.fpa.customer.domain.Customer.builder()
                .customerCode("ICERTI")
                .customerName("Icertis Old")
                .lifecycleStatus(LifecycleStatus.ACTIVE)
                .build());

        var file = xlsx(
                List.of("Customer Code", "Customer Name", "Zoho Books Customer Ref",
                        "Lifecycle Status", "DSO Days", "Relationship Owner Employee ID"),
                List.of(
                        List.of("ICERTI", "Icertis New", "ZB-001", "AT_RISK", "60", "EMP001"),
                        List.of("NEWCO", "New Corp", "ZB-002", "PROSPECT", "30", "")));

        mockMvc.perform(multipart("/api/customers/import")
                        .file(file)
                        .param("conflictResolution", "SKIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.updated").value(0));

        var icertis = customerRepository.findByCustomerCode("ICERTI").orElseThrow();
        assertThat(icertis.getCustomerName()).isEqualTo("Icertis Old");
        assertThat(customerRepository.findByCustomerCode("NEWCO")).isPresent();
    }

    @Test
    void importWithReplace_updatesExistingCustomers() throws Exception {
        customerService.createCustomer(
                "ICERTI", "Icertis Old", null, null, LifecycleStatus.ACTIVE, 45);

        var file = xlsx(
                List.of("Customer Code", "Customer Name", "Zoho Books Customer Ref",
                        "Lifecycle Status", "DSO Days", "Relationship Owner Employee ID"),
                List.of(List.of("ICERTI", "Icertis Updated", "ZB-999", "AT_RISK", "60", "EMP042")));

        mockMvc.perform(multipart("/api/customers/import")
                        .file(file)
                        .param("conflictResolution", "REPLACE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        var icertis = customerRepository.findByCustomerCode("ICERTI").orElseThrow();
        assertThat(icertis.getCustomerName()).isEqualTo("Icertis Updated");
        assertThat(icertis.getZohoBooksCustomerRef()).isEqualTo("ZB-999");
        assertThat(icertis.getLifecycleStatus()).isEqualTo(LifecycleStatus.AT_RISK);
        assertThat(icertis.getRelationshipOwnerEmployeeId()).isEqualTo("EMP042");

        var terms = commercialTermsRepository.findById(icertis.getId()).orElseThrow();
        assertThat(terms.getDsoDays()).isEqualTo(60);
    }

    @Test
    void detectConflicts_returnsExistingAndNewCodes() throws Exception {
        customerRepository.save(com.cognologix.fpa.customer.domain.Customer.builder()
                .customerCode("ICERTI")
                .customerName("Icertis")
                .lifecycleStatus(LifecycleStatus.ACTIVE)
                .build());

        var file = xlsx(
                List.of("Customer Code", "Customer Name", "Zoho Books Customer Ref",
                        "Lifecycle Status", "DSO Days", "Relationship Owner Employee ID"),
                List.of(
                        List.of("ICERTI", "Icertis", "", "", "", ""),
                        List.of("NEWCO", "New Corp", "", "", "", "")));

        mockMvc.perform(multipart("/api/customers/import/conflicts").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingCodes[0]").value("ICERTI"))
                .andExpect(jsonPath("$.newCodes[0]").value("NEWCO"));
    }

    private static MockMultipartFile xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Customers");
            var headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                headerRow.createCell(c).setCellValue(headers.get(c));
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
                    "customers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
