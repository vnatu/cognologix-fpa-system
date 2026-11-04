package com.cognologix.fpa.revenue;

import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.general.GeneralConfigService;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.revenue.domain.MonthSource;
import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.domain.RevenueUploadStatus;
import com.cognologix.fpa.revenue.domain.RevenueSystemAttribute;
import com.cognologix.fpa.revenue.dto.RevenueDtos.ImportSaveOptions;
import com.cognologix.fpa.revenue.repository.RevenueCreditNoteRepository;
import com.cognologix.fpa.revenue.repository.RevenueInvoiceRepository;
import com.cognologix.fpa.revenue.repository.RevenueUploadRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RevenueServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

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
    void uploadInvoices_inrLeavesAmountUsdNullEvenIfMappedToTotal() throws Exception {
        UUID mappingWithUsd = revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_INVOICES,
                "Invoices INR with USD column",
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
                        // INR Total 2L (200000) wrongly also present in USD Amount — must stay null for INR.
                        List.of(List.of("INV-INR", "ACME", "Acme Corp", "2026-06-15", "Sent",
                                "200000.00", "200000.00", "200000.00", "2026-07-15", "INR", "PROJ1"))),
                mappingWithUsd,
                "finance");

        assertThat(result.rowsImported()).isEqualTo(1);
        var inv = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId()).getFirst();
        assertThat(inv.getAmount()).isEqualByComparingTo("2.000");
        assertThat(inv.getCurrency().name()).isEqualTo("INR");
        assertThat(inv.getAmountUsd()).isNull();
    }

    @Test
    void uploadInvoices_usdWithoutAmountUsdMappedLeavesAmountUsdNull() throws Exception {
        var result = revenueService.uploadInvoices(
                6, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-1", "ACME", "Acme Corp", "2026-06-15", "Sent",
                                "100000000.00", "100000000.00", "2026-07-15", "USD", "PROJ1"))),
                invoiceMappingId,
                "finance");

        var inv = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId()).getFirst();
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
                4, 2026, "MONTHLY", null, (m, y) -> List.of());
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
                4, 2026, "QUARTERLY", 1, (m, y) -> List.of());
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
                4, 2026, "ANNUAL", null, (m, y) -> List.of());
        assertThat(annual.granularity()).isEqualTo("ANNUAL");
        assertThat(annual.periodLabel()).isEqualTo("FY2627");
        assertThat(annual.monthsCovered()).hasSize(2);
        assertThat(annual.revenueVsPlan().getFirst().actualNetRevenueInr())
                .isEqualByComparingTo("30.00");
        assertThat(annual.actualsCoverageNote()).contains("2 of 12");
        assertThat(annual.dso()).isNotEmpty();
    }

    @Test
    void dashboard_revenueVsPlan_includesPlanOnlyAndActualOnlyClients() throws Exception {
        if (customerService.findByCustomerCode("BETA").isEmpty()) {
            customerService.createCustomer("BETA", "Beta Inc", null, null, LifecycleStatus.ACTIVE, 30);
        }
        if (customerService.findByCustomerCode("GAMMA").isEmpty()) {
            customerService.createCustomer("GAMMA", "Gamma Ltd", null, null, LifecycleStatus.ACTIVE, 30);
        }
        var acme = customerService.findByCustomerCode("ACME").orElseThrow();
        var beta = customerService.findByCustomerCode("BETA").orElseThrow();
        var gamma = customerService.findByCustomerCode("GAMMA").orElseThrow();

        // Only ACME invoiced in April; BETA and GAMMA are plan-only
        revenueService.uploadInvoices(
                4, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-APR-ACME", "ACME", "Acme Corp", "2026-04-10", "Sent",
                                "1000000.00", "1000000.00", "2026-05-10", "INR", ""))),
                invoiceMappingId,
                "finance");

        RevenueService.PlannedRevenueLookup planLookup = (month, year) -> {
            if (month != 4 || year != 2026) {
                return List.of();
            }
            return List.of(
                    new RevenueService.PlannedRevenueLookup.PlannedClientRow(
                            beta.getId(), new BigDecimal("50.00")),
                    new RevenueService.PlannedRevenueLookup.PlannedClientRow(
                            gamma.getId(), new BigDecimal("20.00")),
                    new RevenueService.PlannedRevenueLookup.PlannedClientRow(
                            acme.getId(), new BigDecimal("8.00")));
        };

        var dashboard = revenueService.getDashboard(4, 2026, "MONTHLY", null, planLookup);
        assertThat(dashboard.revenueVsPlan()).hasSize(3);
        assertThat(dashboard.revenueVsPlan())
                .extracting(r -> r.customerId())
                .containsExactly("BETA", "GAMMA", "ACME"); // planned DESC

        var betaRow = dashboard.revenueVsPlan().stream()
                .filter(r -> "BETA".equals(r.customerId())).findFirst().orElseThrow();
        assertThat(betaRow.plannedRevenue()).isEqualByComparingTo("50.00");
        assertThat(betaRow.actualNetRevenueInr()).isEqualByComparingTo("0");
        assertThat(betaRow.varianceInr()).isEqualByComparingTo("-50.00");
        assertThat(betaRow.actualAmountUsd()).isNull();

        var acmeRow = dashboard.revenueVsPlan().stream()
                .filter(r -> "ACME".equals(r.customerId())).findFirst().orElseThrow();
        assertThat(acmeRow.plannedRevenue()).isEqualByComparingTo("8.00");
        assertThat(acmeRow.actualNetRevenueInr()).isEqualByComparingTo("10.00");
        assertThat(acmeRow.varianceInr()).isEqualByComparingTo("2.00");

        // Unplanned invoice client (plan absent)
        revenueService.uploadInvoices(
                5, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-MAY-ACME", "ACME", "Acme Corp", "2026-05-10", "Sent",
                                "500000.00", "500000.00", "2026-06-10", "INR", ""))),
                invoiceMappingId,
                "finance");
        var may = revenueService.getDashboard(5, 2026, "MONTHLY", null, (m, y) -> List.of());
        assertThat(may.revenueVsPlan()).hasSize(1);
        assertThat(may.revenueVsPlan().getFirst().customerId()).isEqualTo("ACME");
        assertThat(may.revenueVsPlan().getFirst().plannedRevenue()).isEqualByComparingTo("0");
        assertThat(may.revenueVsPlan().getFirst().actualNetRevenueInr()).isEqualByComparingTo("5.00");
        assertThat(may.revenueVsPlan().getFirst().varianceInr()).isEqualByComparingTo("5.00");
    }

    @Test
    void uploadWithoutServiceColumns_keepsTagPeriodAndTotals() throws Exception {
        var result = revenueService.uploadInvoices(
                9, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-TAG", "ACME", "Acme Corp", "2026-09-10", "Paid",
                                "1000000.00", "0", "2026-10-10", "INR", ""))),
                invoiceMappingId,
                "finance");

        var inv = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId()).getFirst();
        assertThat(inv.getMonthSource()).isEqualTo(MonthSource.TAG);
        assertThat(inv.getServiceMonth()).isNull();
        assertThat(inv.getServiceYear()).isNull();
        assertThat(inv.getPeriodMonth()).isEqualTo(9);
        assertThat(inv.getPeriodYear()).isEqualTo(2026);

        var summary = revenueService.getMonthlyRevenueSummary("ACME", 9, 2026);
        assertThat(summary.invoiceTotal()).isEqualByComparingTo(inv.getAmount());
        assertThat(summary.netRevenueInr()).isEqualByComparingTo("10.000");

        var preview = revenueService.previewInvoices(
                9, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-TAG-2", "ACME", "Acme Corp", "2026-09-11", "Paid",
                                "1000000.00", "0", "2026-10-11", "INR", ""))),
                invoiceMappingId);
        assertThat(preview.tagFallbackBlankCount()).isZero();
        assertThat(preview.servicePeriodWarnings()).isEmpty();
        assertThat(preview.otherMonthConfirmationRequired()).isFalse();
    }

    @Test
    void mixedServiceMonths_blankAndJunkWarnAndUseTheTag() throws Exception {
        UUID mappingId = invoiceMappingWithServicePeriod();
        var file = xlsx(
                invoiceHeadersWithService(),
                List.of(
                        invoiceRow("INV-NUM", "8", "2026", "1000000.00"),
                        invoiceRow("INV-SHORT", "Aug", "2026", "1000000.00"),
                        invoiceRow("INV-FULL", "August", "2026", "1000000.00"),
                        invoiceRow("INV-BLANK", "", "", "1000000.00"),
                        invoiceRow("INV-JUNK", "nope", "2026", "1000000.00")));

        var preview = revenueService.previewInvoices(8, 2026, file, mappingId);
        assertThat(preview.tagFallbackBlankCount()).isEqualTo(1);
        assertThat(preview.servicePeriodWarnings()).anyMatch(w -> w.contains("INV-BLANK") && w.contains("blank"));
        assertThat(preview.servicePeriodWarnings()).anyMatch(w -> w.contains("INV-JUNK") && w.contains("not a month"));
        assertThat(preview.otherMonthConfirmationRequired()).isFalse();
        assertThat(preview.months()).singleElement().satisfies(month -> {
            assertThat(month.month()).isEqualTo(8);
            assertThat(month.year()).isEqualTo(2026);
            assertThat(month.invoiceCount()).isEqualTo(5);
            assertThat(month.amount()).isEqualByComparingTo("50.000");
        });

        var result = revenueService.uploadInvoices(8, 2026, file, mappingId, "finance");
        assertThat(result.rowsImported()).isEqualTo(5);
        var invoices = revenueInvoiceRepository.findByRevenueUploadId(result.uploadId());
        assertThat(invoices).allSatisfy(inv -> {
            assertThat(inv.getPeriodMonth()).isEqualTo(8);
            assertThat(inv.getPeriodYear()).isEqualTo(2026);
        });
        assertThat(invoices).filteredOn(inv -> inv.getInvoiceNumber().equals("INV-NUM")
                        || inv.getInvoiceNumber().equals("INV-SHORT")
                        || inv.getInvoiceNumber().equals("INV-FULL"))
                .allSatisfy(inv -> {
                    assertThat(inv.getMonthSource()).isEqualTo(MonthSource.SERVICE);
                    assertThat(inv.getServiceMonth()).isEqualTo(8);
                    assertThat(inv.getServiceYear()).isEqualTo(2026);
                });
        var blank = invoices.stream().filter(inv -> inv.getInvoiceNumber().equals("INV-BLANK")).findFirst().orElseThrow();
        assertThat(blank.getMonthSource()).isEqualTo(MonthSource.TAG);
        assertThat(blank.getServiceMonth()).isNull();
        var junk = invoices.stream().filter(inv -> inv.getInvoiceNumber().equals("INV-JUNK")).findFirst().orElseThrow();
        assertThat(junk.getMonthSource()).isEqualTo(MonthSource.TAG);
        assertThat(junk.getServiceMonth()).isNull();
        assertThat(junk.getServiceYear()).isEqualTo(2026);

        var summary = revenueService.getMonthlyRevenueSummary("ACME", 8, 2026);
        assertThat(summary.netRevenueInr()).isEqualByComparingTo("50.000");
    }

    @Test
    void effectiveMonth_drivesRevenueVsPlanAndDashboard_andLeavesStatusOnTheUploadTag() throws Exception {
        UUID mappingId = invoiceMappingWithServicePeriod();
        revenueService.uploadInvoices(
                7, 2026,
                xlsx(invoiceHeadersWithService(),
                        List.of(invoiceRow("INV-SEP", "September", "2026", "1000000.00"))),
                mappingId,
                "finance",
                new ImportSaveOptions(false, true, false));

        assertThat(revenueService.getAllClientsMonthlyRevenue(7, 2026)).isNull();
        var septemberClients = revenueService.getAllClientsMonthlyRevenue(9, 2026);
        assertThat(septemberClients).hasSize(1);
        assertThat(septemberClients.getFirst().netRevenueInr()).isEqualByComparingTo("10.000");

        assertThat(revenueService.listPeriodsWithData())
                .extracting(p -> p.month() + "-" + p.year())
                .contains("9-2026")
                .doesNotContain("7-2026");

        var july = revenueService.getDashboard(7, 2026, "MONTHLY", null, (m, y) -> List.of());
        assertThat(july.revenueVsPlan()).isEmpty();
        assertThat(july.invoiceStatusSummary()).anySatisfy(bucket -> {
            assertThat(bucket.status()).isEqualTo("Sent");
            assertThat(bucket.count()).isEqualTo(1);
        });
        assertThat(july.dso()).isNotEmpty();

        var september = revenueService.getDashboard(9, 2026, "MONTHLY", null, (m, y) -> List.of());
        assertThat(september.revenueVsPlan()).singleElement().satisfies(row ->
                assertThat(row.actualNetRevenueInr()).isEqualByComparingTo("10.000"));
        assertThat(september.invoiceStatusSummary()).isEmpty();
        assertThat(september.dso()).isEmpty();

        var byServicePeriod = revenueService.getInvoiceList(null, null, null, 9, 2026, null, null, 0, 20);
        assertThat(byServicePeriod.content()).extracting(item -> item.documentNumber()).containsExactly("INV-SEP");
        var byTagMonth = revenueService.getInvoiceList(null, 7, 2026, null, null, null, null, 0, 20);
        assertThat(byTagMonth.content()).isEmpty();

        UUID creditMapping = creditMappingWithServicePeriod();
        revenueService.uploadCreditNotes(
                7, 2026,
                xlsx(
                        List.of("Credit Note#", "Customer Code", "Customer Name", "Credit Note Date",
                                "Status", "Total", "Currency", "Service Month", "Service Year"),
                        List.of(List.of("CN-SEP", "ACME", "Acme Corp", "2026-09-12", "Closed",
                                "200000.00", "INR", "Sep", "2026"))),
                creditMapping,
                "finance",
                new ImportSaveOptions(false, true, false));
        var net = revenueService.getMonthlyRevenueSummary("ACME", 9, 2026);
        assertThat(net.invoiceTotal()).isEqualByComparingTo("10.000");
        assertThat(net.creditNoteTotal()).isEqualByComparingTo("2.000");
        assertThat(net.netRevenueInr()).isEqualByComparingTo("8.000");
    }

    @Test
    void laterUpload_doesNotChangeAnOlderUpload() throws Exception {
        var first = revenueService.uploadInvoices(
                6, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(List.of("INV-OLD", "ACME", "Acme Corp", "2026-06-10", "Paid",
                                "3000000.00", "0", "2026-07-10", "INR", ""))),
                invoiceMappingId,
                "finance");
        var before = revenueInvoiceRepository.findByRevenueUploadId(first.uploadId()).getFirst();
        assertThat(revenueService.getMonthlyRevenueSummary("ACME", 6, 2026).netRevenueInr())
                .isEqualByComparingTo("30.000");

        revenueService.uploadInvoices(
                7, 2026,
                xlsx(invoiceHeadersWithService(),
                        List.of(invoiceRow("INV-NEW", "August", "2026", "1000000.00"))),
                invoiceMappingWithServicePeriod(),
                "finance",
                new ImportSaveOptions(false, true, false));

        var after = revenueInvoiceRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getPeriodMonth()).isEqualTo(6);
        assertThat(after.getPeriodYear()).isEqualTo(2026);
        assertThat(after.getMonthSource()).isEqualTo(MonthSource.TAG);
        assertThat(after.getServiceMonth()).isNull();
        assertThat(after.getServiceYear()).isNull();
        assertThat(after.getAmount()).isEqualByComparingTo(before.getAmount());
        assertThat(revenueUploadRepository.findById(first.uploadId()).orElseThrow().getStatus())
                .isEqualTo(RevenueUploadStatus.ACTIVE);
        assertThat(revenueService.getMonthlyRevenueSummary("ACME", 6, 2026).netRevenueInr())
                .isEqualByComparingTo("30.000");
    }

    @Test
    void versionShrink_requiresConfirmationWhenUnderHalf() throws Exception {
        var rows = new java.util.ArrayList<List<String>>();
        for (int i = 1; i <= 4; i++) {
            rows.add(List.of("INV-" + i, "ACME", "Acme Corp", "2026-02-0" + i, "Sent",
                    "1000000.00", "1000000.00", "2026-03-0" + i, "INR", ""));
        }
        revenueService.uploadInvoices(
                2, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        rows),
                invoiceMappingId,
                "finance");

        var replacement = xlsx(
                List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                        "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                List.of(List.of("INV-ONLY", "ACME", "Acme Corp", "2026-02-15", "Sent",
                        "1000000.00", "1000000.00", "2026-03-15", "INR", "")));
        var preview = revenueService.previewInvoices(2, 2026, replacement, invoiceMappingId);
        assertThat(preview.previousVersionCount()).isEqualTo(4);
        assertThat(preview.thisVersionCount()).isEqualTo(1);
        assertThat(preview.versionShrinkConfirmationRequired()).isTrue();

        assertThatThrownBy(() -> revenueService.uploadInvoices(2, 2026, replacement, invoiceMappingId, "finance"))
                .isInstanceOf(RevenueBadRequestException.class)
                .hasMessageContaining("previous version: 4 invoices, this version: 1");
        assertThat(revenueService.listUploadsForPeriod(2, 2026))
                .filteredOn(upload -> upload.status() == RevenueUploadStatus.ACTIVE)
                .singleElement()
                .satisfies(upload -> assertThat(upload.rowCount()).isEqualTo(4));

        var saved = revenueService.uploadInvoices(
                2, 2026, replacement, invoiceMappingId, "finance",
                new ImportSaveOptions(true, false, false));
        assertThat(saved.versionNumber()).isEqualTo(2);
        assertThat(saved.rowsImported()).isEqualTo(1);
        assertThat(revenueService.getMonthlyRevenueSummary("ACME", 2, 2026).invoiceTotal())
                .isEqualByComparingTo("10.000");
    }

    @Test
    void crossPeriodDuplicate_skipOrCancel_neverKeepsBoth() throws Exception {
        revenueService.uploadInvoices(
                3, 2026,
                xlsx(
                        List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                                "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                        List.of(
                                List.of("INV-DUP", "ACME", "Acme Corp", "2026-03-01", "Sent",
                                        "1000000.00", "1000000.00", "2026-04-01", "INR", ""),
                                List.of("INV-KEEP", "ACME", "Acme Corp", "2026-03-02", "Sent",
                                        "1000000.00", "1000000.00", "2026-04-02", "INR", ""))),
                invoiceMappingId,
                "finance");

        var incoming = xlsx(
                List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                        "Total", "Balance", "Due Date", "Currency", "Project-Code"),
                List.of(
                        List.of("INV-DUP", "ACME", "Acme Corp", "2026-04-01", "Sent",
                                "5000000.00", "5000000.00", "2026-05-01", "INR", ""),
                        List.of("INV-NEW", "ACME", "Acme Corp", "2026-04-02", "Sent",
                                "1000000.00", "1000000.00", "2026-05-02", "INR", "")));
        var preview = revenueService.previewInvoices(4, 2026, incoming, invoiceMappingId);
        assertThat(preview.crossPeriodDuplicates()).singleElement().satisfies(duplicate -> {
            assertThat(duplicate.documentNumber()).isEqualTo("INV-DUP");
            assertThat(duplicate.periodMonth()).isEqualTo(3);
            assertThat(duplicate.periodYear()).isEqualTo(2026);
            assertThat(duplicate.versionNumber()).isEqualTo(1);
            assertThat(duplicate.originalFilename()).isEqualTo("revenue.xlsx");
        });

        assertThatThrownBy(() -> revenueService.uploadInvoices(4, 2026, incoming, invoiceMappingId, "finance"))
                .isInstanceOf(RevenueBadRequestException.class)
                .hasMessageContaining("Skip them in this upload, or cancel");
        assertThat(revenueUploadRepository.findByImportTypeAndPeriodMonthAndPeriodYearAndStatus(
                RevenueImportType.ZOHO_BOOKS_INVOICES, 4, 2026, RevenueUploadStatus.ACTIVE)).isEmpty();
        assertThat(revenueService.getMonthlyRevenueSummary("ACME", 3, 2026).invoiceTotal())
                .isEqualByComparingTo("20.000");

        var saved = revenueService.uploadInvoices(
                4, 2026, incoming, invoiceMappingId, "finance",
                new ImportSaveOptions(false, false, true));
        assertThat(saved.rowsImported()).isEqualTo(1);
        assertThat(revenueInvoiceRepository.findByRevenueUploadId(saved.uploadId()))
                .extracting(inv -> inv.getInvoiceNumber())
                .containsExactly("INV-NEW");
        assertThat(revenueInvoiceRepository.findAll())
                .filteredOn(inv -> "INV-DUP".equals(inv.getInvoiceNumber()))
                .hasSize(1);
        assertThat(revenueService.getMonthlyRevenueSummary("ACME", 3, 2026).invoiceTotal())
                .isEqualByComparingTo("20.000");
        assertThat(revenueService.getMonthlyRevenueSummary("ACME", 4, 2026).invoiceTotal())
                .isEqualByComparingTo("10.000");
    }

    private UUID invoiceMappingWithServicePeriod() {
        return revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_INVOICES,
                "Invoices with service period",
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
                        new PeoplePayrollService.MappingLineInput("Project-Code", RevenueSystemAttribute.PROJECT_CODE),
                        new PeoplePayrollService.MappingLineInput("Service Month", RevenueSystemAttribute.SERVICE_MONTH),
                        new PeoplePayrollService.MappingLineInput("Service Year", RevenueSystemAttribute.SERVICE_YEAR)
                )).id();
    }

    private UUID creditMappingWithServicePeriod() {
        return revenueService.saveMappingTemplate(
                RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES,
                "Credit notes with service period",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Credit Note#", RevenueSystemAttribute.CREDIT_NOTE_NUMBER),
                        new PeoplePayrollService.MappingLineInput("Customer Code", RevenueSystemAttribute.CUSTOMER_CODE),
                        new PeoplePayrollService.MappingLineInput("Customer Name", RevenueSystemAttribute.CUSTOMER_NAME),
                        new PeoplePayrollService.MappingLineInput("Credit Note Date", RevenueSystemAttribute.CREDIT_NOTE_DATE),
                        new PeoplePayrollService.MappingLineInput("Status", RevenueSystemAttribute.STATUS),
                        new PeoplePayrollService.MappingLineInput("Total", RevenueSystemAttribute.AMOUNT),
                        new PeoplePayrollService.MappingLineInput("Currency", RevenueSystemAttribute.CURRENCY),
                        new PeoplePayrollService.MappingLineInput("Service Month", RevenueSystemAttribute.SERVICE_MONTH),
                        new PeoplePayrollService.MappingLineInput("Service Year", RevenueSystemAttribute.SERVICE_YEAR)
                )).id();
    }

    private static List<String> invoiceHeadersWithService() {
        return List.of("Invoice#", "Customer Code", "Customer Name", "Invoice Date", "Status",
                "Total", "Balance", "Due Date", "Currency", "Project-Code", "Service Month", "Service Year");
    }

    private static List<String> invoiceRow(String number, String serviceMonth, String serviceYear, String total) {
        return List.of(number, "ACME", "Acme Corp", "2026-08-10", "Sent",
                total, "0", "2026-09-10", "INR", "", serviceMonth, serviceYear);
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
