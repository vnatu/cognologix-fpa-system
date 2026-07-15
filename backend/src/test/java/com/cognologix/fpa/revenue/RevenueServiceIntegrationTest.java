package com.cognologix.fpa.revenue;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.general.GeneralConfigService;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.domain.RevenueUploadStatus;
import com.cognologix.fpa.revenue.domain.RevenueSystemAttribute;
import com.cognologix.fpa.revenue.repository.RevenueCreditNoteRepository;
import com.cognologix.fpa.revenue.repository.RevenueInvoiceRepository;
import com.cognologix.fpa.revenue.repository.RevenueUploadRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
class RevenueServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired RevenueService revenueService;
    @Autowired CustomerService customerService;
    @Autowired GeneralConfigService generalConfigService;
    @Autowired RevenueUploadRepository revenueUploadRepository;
    @Autowired RevenueInvoiceRepository revenueInvoiceRepository;
    @Autowired RevenueCreditNoteRepository revenueCreditNoteRepository;

    private UUID invoiceMappingId;
    private UUID creditMappingId;

    @BeforeEach
    void setUp() {
        revenueCreditNoteRepository.deleteAll();
        revenueInvoiceRepository.deleteAll();
        revenueUploadRepository.deleteAll();

        if (customerService.findByCustomerCode("ACME").isEmpty()) {
            customerService.createCustomer("ACME", "Acme Corp", null, null, LifecycleStatus.ACTIVE, 30);
        }
        if (generalConfigService.findRateOnDate("USD_INR", LocalDate.of(2026, 6, 15)).isEmpty()) {
            generalConfigService.createFxRate(
                    "USD_INR", new BigDecimal("83.5000"), LocalDate.of(2026, 1, 1), "test");
        }

        invoiceMappingId = revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_INVOICES,
                "Invoices",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Invoice#", RevenueSystemAttribute.INVOICE_NUMBER),
                        new PeoplePayrollService.MappingLineInput("Customer Code", RevenueSystemAttribute.CUSTOMER_CODE),
                        new PeoplePayrollService.MappingLineInput("Customer Name", RevenueSystemAttribute.CUSTOMER_NAME),
                        new PeoplePayrollService.MappingLineInput("Invoice Date", RevenueSystemAttribute.INVOICE_DATE),
                        new PeoplePayrollService.MappingLineInput("Status", RevenueSystemAttribute.STATUS),
                        new PeoplePayrollService.MappingLineInput("Total", RevenueSystemAttribute.AMOUNT),
                        new PeoplePayrollService.MappingLineInput("Balance", RevenueSystemAttribute.BALANCE),
                        new PeoplePayrollService.MappingLineInput("Due Date", RevenueSystemAttribute.DUE_DATE),
                        new PeoplePayrollService.MappingLineInput("Currency", RevenueSystemAttribute.CURRENCY),
                        new PeoplePayrollService.MappingLineInput("Project-Code", RevenueSystemAttribute.PROJECT_CODE)
                )).id();

        creditMappingId = revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES,
                "Credit Notes",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Credit Note#", RevenueSystemAttribute.CREDIT_NOTE_NUMBER),
                        new PeoplePayrollService.MappingLineInput("Customer Code", RevenueSystemAttribute.CUSTOMER_CODE),
                        new PeoplePayrollService.MappingLineInput("Customer Name", RevenueSystemAttribute.CUSTOMER_NAME),
                        new PeoplePayrollService.MappingLineInput("Credit Note Date", RevenueSystemAttribute.CREDIT_NOTE_DATE),
                        new PeoplePayrollService.MappingLineInput("Status", RevenueSystemAttribute.STATUS),
                        new PeoplePayrollService.MappingLineInput("Total", RevenueSystemAttribute.AMOUNT),
                        new PeoplePayrollService.MappingLineInput("Currency", RevenueSystemAttribute.CURRENCY)
                )).id();
    }

    @Test
    void uploadInvoices_createsRecordsAndConvertsUsdToInr() throws Exception {
        var result = revenueService.uploadInvoices(
                6, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-1", "ACME", "Acme Corp", "2026-06-15", "Sent",
                                "1000.00", "1000.00", "2026-07-15", "USD", "PROJ1"))),
                invoiceMappingId,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(1);
        assertThat(result.unrecognizedCustomerCodes()).isEmpty();

        var invoices = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId());
        assertThat(invoices).hasSize(1);
        var inv = invoices.getFirst();
        assertThat(inv.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(inv.getAmountInr()).isEqualByComparingTo("83500.00");
        assertThat(inv.getFxRateId()).isNotNull();
        assertThat(inv.getCustomerId()).isEqualTo("ACME");
    }

    @Test
    void uploadCreditNotes_andNetRevenueEqualsInvoicesMinusCredits() throws Exception {
        revenueService.uploadInvoices(
                6, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-10", "ACME", "Acme Corp", "2026-06-10", "Paid",
                                "5000.00", "0", "2026-07-10", "USD", ""))),
                invoiceMappingId,
                "finance");

        revenueService.uploadCreditNotes(
                6, 2026,
                xlsx(
                        List.of("Credit Note#", "Customer Code", "Customer Name", "Credit Note Date",
                                "Status", "Total", "Currency"),
                        List.of(List.of("CN-1", "ACME", "Acme Corp", "2026-06-20", "Closed",
                                "500.00", "USD"))),
                creditMappingId,
                "finance");

        var summary = revenueService.getMonthlyRevenueSummary("ACME", 6, 2026);
        assertThat(summary.invoiceTotal()).isEqualByComparingTo("5000.00");
        assertThat(summary.creditNoteTotal()).isEqualByComparingTo("500.00");
        assertThat(summary.netRevenue()).isEqualByComparingTo("4500.00");
        assertThat(summary.netRevenueInr()).isEqualByComparingTo("375750.00"); // (5000-500)*83.5
    }

    @Test
    void reupload_supersedesPriorVersion() throws Exception {
        var first = revenueService.uploadInvoices(
                7, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-A", "ACME", "Acme Corp", "2026-07-01", "Sent",
                                "100.00", "100", "2026-08-01", "USD", ""))),
                invoiceMappingId,
                "finance");
        assertThat(first.versionNumber()).isEqualTo(1);

        var second = revenueService.uploadInvoices(
                7, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-B", "ACME", "Acme Corp", "2026-07-02", "Sent",
                                "200.00", "200", "2026-08-02", "USD", ""))),
                invoiceMappingId,
                "finance");
        assertThat(second.versionNumber()).isEqualTo(2);

        var uploads = revenueService.listUploadsForPeriod(7, 2026);
        assertThat(uploads).hasSize(2);
        assertThat(uploads.stream().filter(u -> u.status() == RevenueUploadStatus.SUPERSEDED)).hasSize(1);
        assertThat(uploads.stream().filter(u -> u.status() == RevenueUploadStatus.ACTIVE)).hasSize(1);

        var summary = revenueService.getMonthlyRevenueSummary("ACME", 7, 2026);
        assertThat(summary.invoiceTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void unrecognizedCustomerCode_flaggedAsWarningNotBlocking() throws Exception {
        var result = revenueService.uploadInvoices(
                8, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-X", "UNKNOWN_CLIENT", "Ghost Co", "2026-08-01", "Sent",
                                "50.00", "50", "2026-09-01", "USD", ""))),
                invoiceMappingId,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(1);
        assertThat(result.unrecognizedCustomerCodes()).containsExactly("UNKNOWN_CLIENT");
        assertThat(revenueInvoiceRepository.findByRevenueUploadId(result.uploadId())).hasSize(1);
    }

    private static MockMultipartFile xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            wb.write(out);
            return new MockMultipartFile(
                    "file", "revenue.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
