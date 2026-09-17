package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.MappingSource;
import com.cognologix.fpa.bankrecon.domain.ReconRunStatus;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.BatchMappingResponse;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.CreateLedgerRequest;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerMappingResult;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerResponse;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.RunResponse;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.TransactionResponse;
import com.cognologix.fpa.bankrecon.repository.LearnedMappingRepository;
import com.cognologix.fpa.bankrecon.repository.ReconTransactionRepository;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.people.PeoplePayrollService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
class BankReconServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired BankReconService bankReconService;
    @Autowired PeoplePayrollService peoplePayrollService;
    @Autowired ReconTransactionRepository transactionRepository;
    @Autowired LearnedMappingRepository learnedMappingRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean StructuredLlmClient structuredLlmClient;
    @MockBean NarrationEmbedder narrationEmbedder;

    @BeforeEach
    void stubs() {
        reset(structuredLlmClient, narrationEmbedder);
        when(narrationEmbedder.embed(anyString())).thenReturn(new float[768]);
        when(structuredLlmClient.mapBatch(anyString())).thenReturn(new BatchMappingResponse(List.of()));
        seedLedgers();
        seedHdfcMapping();
    }

    @Test
    void hdfcCsvParsingExtractsAccountPeriodAndCount() {
        RunResponse run = bankReconService.uploadStatement(sampleCsv(), "tester");
        assertThat(run.accountNumber()).isEqualTo("50100123456789");
        assertThat(run.statementPeriodStart()).hasToString("2026-08-01");
        assertThat(run.statementPeriodEnd()).hasToString("2026-08-31");
        assertThat(run.totalTransactions()).isEqualTo(3);
        assertThat(run.transactions()).hasSize(3);
    }

    @Test
    void contraDetectionIdentifiesInternalTransfers() {
        RunResponse run = bankReconService.uploadStatement(sampleCsv(), "tester");
        TransactionResponse contra = run.transactions().stream()
                .filter(t -> t.description().contains("NEFT"))
                .findFirst()
                .orElseThrow();
        assertThat(contra.voucherType()).isEqualTo(VoucherType.CONTRA);
        TransactionResponse payment = run.transactions().stream()
                .filter(t -> t.description().contains("UPI"))
                .findFirst()
                .orElseThrow();
        assertThat(payment.voucherType()).isEqualTo(VoucherType.PAYMENT);
        TransactionResponse receipt = run.transactions().stream()
                .filter(t -> t.description().contains("IMPS"))
                .findFirst()
                .orElseThrow();
        assertThat(receipt.voucherType()).isEqualTo(VoucherType.RECEIPT);
    }

    @Test
    void learnedMappingExactMatchSkipsLlm() {
        String narration = "UPI VENDOR PAYMENT XYZ";
        String normalised = NarrationNormalizer.normalise(narration);
        ensureLedger("Office Expenses", "Indirect Expenses");
        learnedMappingRepository.save(com.cognologix.fpa.bankrecon.domain.LearnedMapping.builder()
                .normalisedNarration(normalised)
                .voucherType(VoucherType.PAYMENT)
                .ledgerName("Office Expenses")
                .useCount(2)
                .build());

        RunResponse run = bankReconService.uploadStatement(csvWithSinglePayment(narration), "tester");
        TransactionResponse tx = run.transactions().getFirst();
        assertThat(tx.mappedLedger()).isEqualTo("Office Expenses");
        assertThat(tx.mappingSource()).isEqualTo(MappingSource.LEARNED);
        verify(structuredLlmClient, never()).mapBatch(anyString());
    }

    @Test
    void llmValidLedgerIsAssignedAndInvalidIsUnmapped() {
        ensureLedger("Internet Charges", "Indirect Expenses");
        when(structuredLlmClient.mapBatch(anyString())).thenAnswer(invocation -> {
            throw new IllegalStateException("captured later");
        });

        RunResponse first = bankReconService.uploadStatement(csvWithSinglePayment("CLOUD HOSTING AWS"), "tester");
        UUID txId = first.transactions().getFirst().id();

        when(structuredLlmClient.mapBatch(anyString())).thenReturn(new BatchMappingResponse(List.of(
                new LedgerMappingResult(txId.toString(), "Internet Charges"))));
        var tx = transactionRepository.findById(txId).orElseThrow();
        bankReconService.mapBatchWithLlm(List.of(tx));
        assertThat(transactionRepository.findById(txId).orElseThrow().getMappedLedger())
                .isEqualTo("Internet Charges");
        assertThat(transactionRepository.findById(txId).orElseThrow().getMappingSource())
                .isEqualTo(MappingSource.LLM);

        when(structuredLlmClient.mapBatch(anyString())).thenReturn(new BatchMappingResponse(List.of(
                new LedgerMappingResult(txId.toString(), "Not A Real Ledger"))));
        tx = transactionRepository.findById(txId).orElseThrow();
        tx.setMappedLedger(null);
        tx.setMappingSource(null);
        bankReconService.mapBatchWithLlm(List.of(tx));
        assertThat(tx.getMappedLedger()).isNull();
        assertThat(tx.getMappingSource()).isNull();
    }

    @Test
    void financeCorrectionCreatesLearnedMappingWithEmbedding() {
        ensureLedger("Software Licences", "Indirect Expenses");
        RunResponse run = bankReconService.uploadStatement(csvWithSinglePayment("ADOBE CREATIVE CLOUD"), "tester");
        UUID txId = run.transactions().getFirst().id();
        bankReconService.updateTransactionMapping(run.id(), txId, "Software Licences", "PAYMENT", false, "finance");
        var mapping = learnedMappingRepository
                .findByNormalisedNarrationAndVoucherType(
                        NarrationNormalizer.normalise("ADOBE CREATIVE CLOUD"), VoucherType.PAYMENT)
                .orElseThrow();
        assertThat(mapping.getLedgerName()).isEqualTo("Software Licences");
        verify(narrationEmbedder).embed(mapping.getNormalisedNarration());
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learned_mapping WHERE id = ? AND narration_embedding IS NOT NULL",
                Integer.class, mapping.getId());
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void ledgerXmlImportCleansNamesUnescapesEntitiesAndDefaultsEmptyParentToLiability() {
        jdbcTemplate.update("""
                INSERT INTO tally_ledger (ledger_name, group_name, accounting_nature, is_bank_account, is_active)
                VALUES (?, 'Sundry Debtors', 'Asset', false, true)
                """, "A1 Group\r\n");
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="A1 Group&#13;&#10;">
                    <PARENT>Sundry Debtors</PARENT>
                  </LEDGER>
                  <LEDGER NAME="P&amp;L Appropriation">
                    <PARENT>Reserves &amp; Surplus</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Orphan Ledger">
                    <PARENT/>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        var result = bankReconService.importLedgerMaster(file);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(2);
        String a1 = jdbcTemplate.queryForObject(
                "SELECT ledger_name FROM tally_ledger WHERE regexp_replace(ledger_name, E'[\\r\\n]', '', 'g') = 'A1 Group'",
                String.class);
        assertThat(a1).isEqualTo("A1 Group");
        var pl = bankReconService.listLedgers("P&L Appropriation", 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals("P&L Appropriation"))
                .findFirst()
                .orElseThrow();
        assertThat(pl.groupName()).isEqualTo("Reserves & Surplus");
        assertThat(pl.accountingNature()).isEqualTo("Liability");
        var orphan = bankReconService.listLedgers("Orphan Ledger", 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals("Orphan Ledger"))
                .findFirst()
                .orElseThrow();
        assertThat(orphan.groupName()).isEmpty();
        assertThat(orphan.accountingNature()).isEqualTo("Liability");
    }

    @Test
    void ledgerXmlImportUpsertsCustomGroupsAndDoesNotDropUnknownParents() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <GROUP NAME="Salary Payable">
                    <PARENT>Current Liabilities</PARENT>
                  </GROUP>
                  <LEDGER NAME="Salary Payable">
                    <PARENT>Salary Payable</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Mystery Ledger">
                    <PARENT>Not A Real Group</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Trade Receivable Desk">
                    <PARENT>Custom Receivables</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        var result = bankReconService.importLedgerMaster(file);

        assertThat(result.imported()).isEqualTo(3);
        String salaryGroupNature = jdbcTemplate.queryForObject(
                "SELECT accounting_nature FROM tally_ledger_group WHERE group_name = 'Salary Payable'",
                String.class);
        assertThat(salaryGroupNature).isEqualTo("Liability");
        var salary = bankReconService.listLedgers("Salary Payable", 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals("Salary Payable"))
                .findFirst()
                .orElseThrow();
        assertThat(salary.groupName()).isEqualTo("Salary Payable");
        assertThat(salary.accountingNature()).isEqualTo("Liability");
        var mystery = bankReconService.listLedgers("Mystery Ledger", 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals("Mystery Ledger"))
                .findFirst()
                .orElseThrow();
        assertThat(mystery.groupName()).isEqualTo("Not A Real Group");
        assertThat(mystery.accountingNature()).isEqualTo("Liability");
        var receivable = bankReconService.listLedgers("Trade Receivable Desk", 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals("Trade Receivable Desk"))
                .findFirst()
                .orElseThrow();
        assertThat(receivable.accountingNature()).isEqualTo("Asset");
    }

    @Test
    void listLedgersFiltersByVoucherType() {
        ensureLedger("Office Rent", "Indirect Expenses");
        ensureLedger("Client Receipts", "Indirect Income");

        var payments = bankReconService.listLedgers("Office", VoucherType.PAYMENT, 0, 20);
        assertThat(payments.content()).extracting(LedgerResponse::ledgerName).contains("Office Rent");
        assertThat(payments.content()).noneMatch((l) -> "HDFC Bank".equals(l.ledgerName()));

        var receipts = bankReconService.listLedgers("", VoucherType.RECEIPT, 0, 20);
        assertThat(receipts.content()).extracting(LedgerResponse::ledgerName)
                .contains("HDFC Bank", "Client Receipts");
        assertThat(receipts.content()).noneMatch((l) -> "Office Rent".equals(l.ledgerName()));

        var contra = bankReconService.listLedgers("", VoucherType.CONTRA, 0, 20);
        assertThat(contra.content()).allMatch(LedgerResponse::bankAccount);
        assertThat(contra.content()).extracting(LedgerResponse::ledgerName).contains("HDFC Bank");
    }

    @Test
    void closeRunPurgesTransactionsButKeepsSummary() {
        ensureLedger("Misc Expense", "Indirect Expenses");
        RunResponse run = bankReconService.uploadStatement(csvWithSinglePayment("RANDOM FEE"), "tester");
        UUID txId = run.transactions().getFirst().id();
        bankReconService.updateTransactionMapping(run.id(), txId, "Misc Expense", "PAYMENT", false, "finance");
        RunResponse closed = bankReconService.closeRun(run.id(), "finance");
        assertThat(closed.status()).isEqualTo(ReconRunStatus.CLOSED);
        assertThat(closed.mappedCount()).isEqualTo(1);
        assertThat(transactionRepository.findByRunIdOrderBySortOrderAsc(run.id())).isEmpty();
        assertThat(bankReconService.getRun(run.id()).totalTransactions()).isEqualTo(1);
    }

    @Test
    void tallyExportAssignsDrCrPerVoucherType() throws Exception {
        ensureLedger("HDFC Bank", "Bank Accounts");
        ensureLedger("Rent", "Indirect Expenses");
        ensureLedger("Client Receipts", "Indirect Income");
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,OFFICE RENT AUG,40000.00,D,N1,01/08/2026,PUNE,60000.00
                02/08/2026,CLIENT PAYMENT ACME,80000.00,C,I2,02/08/2026,PUNE,140000.00
                """;
        RunResponse run = bankReconService.uploadStatement(multipart("stmt.csv", csv), "tester");
        TransactionResponse payment = run.transactions().stream()
                .filter(t -> t.voucherType() == VoucherType.PAYMENT).findFirst().orElseThrow();
        TransactionResponse receipt = run.transactions().stream()
                .filter(t -> t.voucherType() == VoucherType.RECEIPT).findFirst().orElseThrow();
        bankReconService.updateTransactionMapping(run.id(), payment.id(), "Rent", "PAYMENT", false, "finance");
        bankReconService.updateTransactionMapping(run.id(), receipt.id(), "Client Receipts", "RECEIPT", false, "finance");

        BankReconService.ExportFile file = bankReconService.generateExport(
                run.id(), List.of(payment.id(), receipt.id()), "finance");
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
            Sheet pay = wb.getSheet("Payment");
            Row payRow = pay.getRow(1);
            assertThat(payRow.getCell(3).getStringCellValue()).isEqualTo("Rent");
            assertThat(payRow.getCell(4).getStringCellValue()).isEqualTo("HDFC Bank");
            assertThat(BigDecimal.valueOf(payRow.getCell(5).getNumericCellValue()))
                    .isEqualByComparingTo("40000.00");
            Sheet rec = wb.getSheet("Receipt");
            Row recRow = rec.getRow(1);
            assertThat(recRow.getCell(3).getStringCellValue()).isEqualTo("HDFC Bank");
            assertThat(recRow.getCell(4).getStringCellValue()).isEqualTo("Client Receipts");
        }
    }

    private void seedLedgers() {
        ensureLedger("HDFC Bank", "Bank Accounts");
    }

    private void seedHdfcMapping() {
        List<PeoplePayrollService.MappingLineInput> lines = HdfcStatementParserTest.defaultMapping()
                .entrySet().stream()
                .map(e -> new PeoplePayrollService.MappingLineInput(e.getKey(), e.getValue()))
                .toList();
        peoplePayrollService.saveMappingTemplateApi("HDFC_BANK_STATEMENT", "HDFC default", lines);
    }

    private void ensureLedger(String name, String group) {
        boolean exists = bankReconService.listLedgers(name, 0, 20).content().stream()
                .anyMatch(l -> l.ledgerName().equals(name));
        if (!exists) {
            bankReconService.addLedger(new CreateLedgerRequest(name, group));
        }
    }

    private static MockMultipartFile sampleCsv() {
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,NEFT COGNOLOGIX INTERNAL TO HDFC 2,10000.00,D,N1,01/08/2026,PUNE,90000.00
                01/08/2026,UPI-VENDOR PAYMENT XYZ,500.00,D,U2,01/08/2026,PUNE,89500.00
                01/08/2026,IMPS INWARD ACME LTD,25000.00,C,I3,01/08/2026,PUNE,114500.00
                """;
        return multipart("hdfc.csv", csv);
    }

    private static MockMultipartFile csvWithSinglePayment(String narration) {
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,%s,500.00,D,N1,01/08/2026,PUNE,100.00
                """.formatted(narration);
        return multipart("one.csv", csv);
    }

    private static MockMultipartFile multipart(String name, String csv) {
        return new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
