package com.cognologix.fpa.customer;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.customer.domain.RateCardType;
import com.cognologix.fpa.customer.domain.RateCurrency;
import com.cognologix.fpa.customer.repository.CustomerProjectCodeRepository;
import com.cognologix.fpa.customer.repository.CustomerRepository;
import com.cognologix.fpa.customer.repository.RateCardRepository;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class CustomerExportImportIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerProjectCodeRepository projectCodeRepository;
    @Autowired RateCardRepository rateCardRepository;

    @Test
    void exportCustomers_includesInternalBus() throws Exception {
        long internalCount = customerRepository.findByInternalTrue().size();
        customerService.createCustomer(
                "EXPORT1", "Export Client One", "ZB-1", "EMP1", LifecycleStatus.ACTIVE, 45);
        customerService.createCustomer(
                "EXPORT2", "Export Client Two", null, null, LifecycleStatus.PROSPECT, 30);

        byte[] content = mockMvc.perform(get("/api/customers/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        List<List<String>> rows = readDataRows(content);
        assertThat(rows).hasSize((int) (internalCount + 2));
        assertThat(rows.stream().map(r -> r.getFirst()).toList())
                .contains("EXPORT1", "EXPORT2", "MGMT");
    }

    @Test
    void exportRateCards_sortedByCustomerCodeThenEffectiveFrom() throws Exception {
        customerService.createCustomer(
                "ZZLAST", "Z Last", null, null, LifecycleStatus.ACTIVE, 30);
        customerService.createCustomer(
                "AATEST", "A First", null, null, LifecycleStatus.ACTIVE, 30);

        var aFirst = customerRepository.findByCustomerCode("AATEST").orElseThrow();
        var zLast = customerRepository.findByCustomerCode("ZZLAST").orElseThrow();

        customerService.createRateCard(
                zLast.getId(), "Z Card Old", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2025, 1, 1),
                List.of(com.cognologix.fpa.customer.domain.RateCardLine.builder()
                        .rateAmount(new BigDecimal("100000")).build()));
        var closedOld = rateCardRepository.findByCustomerIdAndEffectiveToIsNull(zLast.getId()).orElseThrow();
        closedOld.setEffectiveTo(LocalDate.of(2025, 12, 31));
        rateCardRepository.save(closedOld);
        customerService.createRateCard(
                zLast.getId(), "Z Card New", RateCardType.FLAT, RateCurrency.INR,
                LocalDate.of(2026, 1, 1),
                List.of(com.cognologix.fpa.customer.domain.RateCardLine.builder()
                        .rateAmount(new BigDecimal("120000")).build()));
        customerService.createRateCard(
                aFirst.getId(), "A Card", RateCardType.FLAT, RateCurrency.USD,
                LocalDate.of(2026, 6, 1),
                List.of(com.cognologix.fpa.customer.domain.RateCardLine.builder()
                        .rateAmount(new BigDecimal("90000")).build()));

        byte[] content = mockMvc.perform(get("/api/customers/rate-cards/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        List<List<String>> rows = readDataRows(content);
        List<String> customerCodes = rows.stream().map(r -> r.get(0)).toList();
        List<String> effectiveFroms = rows.stream().map(r -> r.get(4)).toList();

        assertThat(customerCodes.indexOf("AATEST")).isLessThan(customerCodes.indexOf("ZZLAST"));
        int zFirst = customerCodes.indexOf("ZZLAST");
        int zSecond = customerCodes.lastIndexOf("ZZLAST");
        assertThat(effectiveFroms.get(zFirst)).isEqualTo("2025-01-01");
        assertThat(effectiveFroms.get(zSecond)).isEqualTo("2026-01-01");
    }

    @Test
    void importProjectCodes_createsNewAndSkipsExisting() throws Exception {
        var client = customerService.createCustomer(
                "PCCLIENT", "PC Client", null, null, LifecycleStatus.ACTIVE, 30);
        customerService.addProjectCode(client.getId(), "EXISTING", "Already there");

        var file = xlsx(
                List.of("Customer Code", "Project Code", "Description"),
                List.of(
                        List.of("PCCLIENT", "EXISTING", "Ignored"),
                        List.of("PCCLIENT", "NEWCODE", "New project"),
                        List.of("UNKNOWN", "XCODE", "Bad customer")));

        mockMvc.perform(multipart("/api/customers/project-codes/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].customerCode").value("UNKNOWN"))
                .andExpect(jsonPath("$.errors[0].reason").value("Customer Code not found: UNKNOWN"));

        assertThat(projectCodeRepository.findByCustomerId(client.getId())).hasSize(2);
        assertThat(projectCodeRepository.findByCustomerIdAndProjectCode(client.getId(), "NEWCODE"))
                .isPresent();
    }

    private static List<List<String>> readDataRows(byte[] content) throws Exception {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                var row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    var cell = row.getCell(c);
                    values.add(cell == null ? "" : CustomerImportParser.cellValueAsString(cell));
                }
                if (values.stream().anyMatch(v -> v != null && !v.isBlank())) {
                    rows.add(values);
                }
            }
            return rows;
        }
    }

    private static MockMultipartFile xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
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
                    "upload.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
