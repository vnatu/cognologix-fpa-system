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
                        // Zoho exports full currency units; stored as Rs Lakhs (÷100000).
                        List.of(List.of("INV-1", "ACME", "Acme Corp", "2026-06-15", "Sent",
                                "100000000.00", "100000000.00", "2026-07-15", "USD", "PROJ1"))),
                invoiceMappingId,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(1);
        assertThat(result.unrecognizedCustomerCodes()).isEmpty();

        var invoices = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId());
        assertThat(invoices).hasSize(1);
        var inv = invoices.getFirst();
        assertThat(inv.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(inv.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(inv.getAmountInr()).isEqualByComparingTo("83500.00");
        assertThat(inv.getFxRateId()).isNotNull();
        assertThat(inv.getCustomerId()).isEqualTo("ACME");
        assertThat(inv.getAmountUsd()).isNull();
    }

    @Test
    void uploadInvoices_storesRawAmountUsdWithoutLakhsConversion() throws Exception {
        UUID mappingWithUsd = revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_INVOICES,
                "Invoices With USD",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Invoice#", RevenueSystemAttribute.INVOICE_NUMBER),
                        new PeoplePayrollService.MappingLineInput("Customer Code", RevenueSystemAttribute.CUSTOMER_CODE),
                        new PeoplePayrollService.MappingLineInput("Customer Name", RevenueSystemAttribute.CUSTOMER_NAME),
                        new PeoplePayrollService.MappingLineInput("Invoice Date", RevenueSystemAttribute.INVOICE_DATE),
                        new PeoplePayrollService.MappingLineInput("Status", RevenueSystemAttribute.STATUS),
                        new PeoplePayrollService.MappingLineInput("Total", RevenueSystemAttribute.AMOUNT),
                        new PeoplePayrollService.MappingLineInput("USD Amount", RevenueSystemAttribute.AMOUNT_USD),
                        new PeoplePayrollService.MappingLineInput("Balance", RevenueSystemAttribute.BALANCE),
                        new PeoplePayrollService.MappingLineInput("Due Date", RevenueSystemAttribute.DUE_DATE),
                        new PeoplePayrollService.MappingLineInput("Currency", RevenueSystemAttribute.CURRENCY),
                        new PeoplePayrollService.MappingLineInput("Project-Code", RevenueSystemAttribute.PROJECT_CODE)
                )).id();

        var result = revenueService.uploadInvoices(
                6, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "USD Amount", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-USD", "ACME", "Acme Corp", "2026-06-15", "Sent",
                                "100000000.00", "$37,250.00", "100000000.00", "2026-07-15", "USD", "PROJ1"))),
                mappingWithUsd,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(1);
        var inv = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId()).getFirst();
        assertThat(inv.getAmount()).isEqualByComparingTo("1000.000");
        assertThat(inv.getAmountUsd()).isEqualByComparingTo("37250.00");
    }

    @Test
    void uploadCreditNotes_andNetRevenueEqualsInvoicesMinusCredits() throws Exception {
        revenueService.uploadInvoices(
                6, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-10", "ACME", "Acme Corp", "2026-06-10", "Paid",
                                "500000000.00", "0", "2026-07-10", "USD", ""))),
                invoiceMappingId,
                "finance");

        revenueService.uploadCreditNotes(
                6, 2026,
                xlsx(
                        List.of("Credit Note#", "Customer Code", "Customer Name", "Credit Note Date",
                                "Status", "Total", "Currency"),
                        List.of(List.of("CN-1", "ACME", "Acme Corp", "2026-06-20", "Closed",
                                "50000000.00", "USD"))),
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
                                "10000000.00", "10000000", "2026-08-01", "USD", ""))),
                invoiceMappingId,
                "finance");
        assertThat(first.versionNumber()).isEqualTo(1);

        var second = revenueService.uploadInvoices(
                7, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-B", "ACME", "Acme Corp", "2026-07-02", "Sent",
                                "20000000.00", "20000000", "2026-08-02", "USD", ""))),
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
                                "5000000.00", "5000000", "2026-09-01", "USD", ""))),
                invoiceMappingId,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(1);
        assertThat(result.unrecognizedCustomerCodes()).containsExactly("UNKNOWN_CLIENT");
        assertThat(revenueInvoiceRepository.findByRevenueUploadId(result.uploadId())).hasSize(1);
    }

    @Test
    void uploadInvoices_inrAmountInrEqualsAmount_noFx() throws Exception {
        // Three Icertis-style INR invoices (full rupees → Rs Lakhs). amount_inr must equal amount.
        var result = revenueService.uploadInvoices(
                4, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(
                                List.of("INV-INR-1", "ACME", "Acme Corp", "2026-04-10", "Paid",
                                        "956146.24", "0", "2026-05-10", "INR", ""),
                                List.of("INV-INR-2", "ACME", "Acme Corp", "2026-05-10", "Paid",
                                        "894603.60", "0", "2026-06-10", "INR", ""),
                                List.of("INV-INR-3", "ACME", "Acme Corp", "2026-06-10", "Paid",
                                        "1265571.60", "0", "2026-07-10", "INR", ""))),
                invoiceMappingId,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(3);
        var invoices = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId());
        assertThat(invoices).hasSize(3);

        BigDecimal amountSum = invoices.stream().map(i -> i.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amountInrSum = invoices.stream().map(i -> i.getAmountInr()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(amountSum).isEqualByComparingTo("31.163");
        assertThat(amountInrSum).isEqualByComparingTo(amountSum);
        assertThat(invoices).allSatisfy(inv -> {
            assertThat(inv.getCurrency().name()).isEqualTo("INR");
            assertThat(inv.getAmountInr()).isEqualByComparingTo(inv.getAmount());
            assertThat(inv.getFxRateId()).isNull();
        });
    }

    @Test
    void uploadInvoices_nullCurrencyDefaultsToInr_noFx() throws Exception {
        UUID mappingWithoutCurrency = revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_INVOICES,
                "Invoices No Currency",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Invoice#", RevenueSystemAttribute.INVOICE_NUMBER),
                        new PeoplePayrollService.MappingLineInput("Customer Code", RevenueSystemAttribute.CUSTOMER_CODE),
                        new PeoplePayrollService.MappingLineInput("Customer Name", RevenueSystemAttribute.CUSTOMER_NAME),
                        new PeoplePayrollService.MappingLineInput("Invoice Date", RevenueSystemAttribute.INVOICE_DATE),
                        new PeoplePayrollService.MappingLineInput("Status", RevenueSystemAttribute.STATUS),
                        new PeoplePayrollService.MappingLineInput("Total", RevenueSystemAttribute.AMOUNT),
                        new PeoplePayrollService.MappingLineInput("Balance", RevenueSystemAttribute.BALANCE),
                        new PeoplePayrollService.MappingLineInput("Due Date", RevenueSystemAttribute.DUE_DATE)
                )).id();

        var result = revenueService.uploadInvoices(
                5, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date"),
                        List.of(List.of("INV-DEF", "ACME", "Acme Corp", "2026-05-15", "Sent",
                                "1000000.00", "1000000.00", "2026-06-15"))),
                mappingWithoutCurrency,
                "finance");

        var inv = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId()).getFirst();
        assertThat(inv.getCurrency().name()).isEqualTo("INR");
        assertThat(inv.getAmount()).isEqualByComparingTo("10.00");
        assertThat(inv.getAmountInr()).isEqualByComparingTo("10.00");
        assertThat(inv.getFxRateId()).isNull();
    }

    @Test
    void uploadCreditNotes_inrAmountInrEqualsAmount_noFx() throws Exception {
        revenueService.uploadCreditNotes(
                6, 2026,
                xlsx(
                        List.of("Credit Note#", "Customer Code", "Customer Name", "Credit Note Date",
                                "Status", "Total", "Currency"),
                        List.of(List.of("CN-INR", "ACME", "Acme Corp", "2026-06-20", "Closed",
                                "250000.00", "INR"))),
                creditMappingId,
                "finance");

        var notes = revenueCreditNoteRepository.findAll().stream()
                .filter(n -> "CN-INR".equals(n.getCreditNoteNumber()))
                .toList();
        assertThat(notes).hasSize(1);
        var cn = notes.getFirst();
        assertThat(cn.getCurrency().name()).isEqualTo("INR");
        assertThat(cn.getAmount()).isEqualByComparingTo("2.50");
        assertThat(cn.getAmountInr()).isEqualByComparingTo("2.50");
        assertThat(cn.getFxRateId()).isNull();
    }

    @Test
    void dashboard_supportsMonthlyQuarterlyAnnualGranularity() throws Exception {
        // Apr + May 2026 invoices (Q1 FY2627)
        revenueService.uploadInvoices(
                4, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-APR", "ACME", "Acme Corp", "2026-04-10", "Sent",
                                "1000000.00", "1000000.00", "2026-05-10", "INR", ""))),
                invoiceMappingId,
                "finance");
        revenueService.uploadInvoices(
                5, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-MAY", "ACME", "Acme Corp", "2026-05-10", "Sent",
                                "2000000.00", "2000000.00", "2026-06-10", "INR", ""))),
                invoiceMappingId,
                "finance");

        var periods = revenueService.listPeriodsWithData();
        assertThat(periods).extracting(p -> p.month() + "-" + p.year())
                .contains("4-2026", "5-2026");

        var monthly = revenueService.getDashboard(
                4, 2026, "MONTHLY", null, (id, m, y) -> BigDecimal.ZERO);
        assertThat(monthly.granularity()).isEqualTo("MONTHLY");
        assertThat(monthly.periodLabel()).isEqualTo("April 2026");
        assertThat(monthly.monthsCovered()).hasSize(1);
        assertThat(monthly.revenueVsPlan()).hasSize(1);
        assertThat(monthly.revenueVsPlan().getFirst().actualNetRevenueInr())
                .isEqualByComparingTo("10.00");
        assertThat(monthly.invoiceStatusSummary()).anySatisfy(b -> {
            assertThat(b.status()).isEqualTo("Sent");
            assertThat(b.count()).isEqualTo(1);
        });

        var quarterly = revenueService.getDashboard(
                4, 2026, "QUARTERLY", 1, (id, m, y) -> BigDecimal.ZERO);
        assertThat(quarterly.granularity()).isEqualTo("QUARTERLY");
        assertThat(quarterly.quarter()).isEqualTo(1);
        assertThat(quarterly.periodLabel()).isEqualTo("Q1 FY2627");
        assertThat(quarterly.monthsCovered()).hasSize(2);
        assertThat(quarterly.revenueVsPlan().getFirst().actualNetRevenueInr())
                .isEqualByComparingTo("30.00"); // 10 + 20
        assertThat(quarterly.invoiceStatusSummary()).anySatisfy(b -> {
            assertThat(b.status()).isEqualTo("Sent");
            assertThat(b.count()).isEqualTo(2);
        });
        assertThat(quarterly.actualsCoverageNote()).contains("2 of 3");

        var annual = revenueService.getDashboard(
                4, 2026, "ANNUAL", null, (id, m, y) -> BigDecimal.ZERO);
        assertThat(annual.granularity()).isEqualTo("ANNUAL");
        assertThat(annual.periodLabel()).isEqualTo("FY2627");
        assertThat(annual.monthsCovered()).hasSize(2);
        assertThat(annual.revenueVsPlan().getFirst().actualNetRevenueInr())
                .isEqualByComparingTo("30.00");
        assertThat(annual.actualsCoverageNote()).contains("2 of 12");
        assertThat(annual.dso()).isNotEmpty();
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
