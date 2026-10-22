package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.MappingSource;
import com.cognologix.fpa.bankrecon.domain.ReconRunStatus;
import com.cognologix.fpa.bankrecon.domain.ReconTransaction;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.AccountMappingRequest;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.CreateLedgerRequest;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerCodeImportPreview;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerHintRequest;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerMappingResult;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerResponse;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.RunResponse;
import com.cognologix.fpa.bankrecon.dto.BankReconDtos.TransactionResponse;
import com.cognologix.fpa.bankrecon.repository.LearnedMappingRepository;
import com.cognologix.fpa.bankrecon.repository.ReconTransactionRepository;
import com.cognologix.fpa.general.UserRole;
import com.cognologix.fpa.general.UserService;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.security.JwtTokenProvider;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BankReconServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired BankReconService bankReconService;
    @Autowired PeoplePayrollService peoplePayrollService;
    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ReconTransactionRepository transactionRepository;
    @Autowired LearnedMappingRepository learnedMappingRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean StructuredLlmClient structuredLlmClient;
    @MockitoBean NarrationEmbedder narrationEmbedder;

    @BeforeEach
    void stubs() {
        reset(structuredLlmClient, narrationEmbedder);
        when(narrationEmbedder.embed(anyString())).thenReturn(new float[768]);
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.complete(List.of()));
        seedLedgers();
        seedHdfcMapping();
        seedAccountMapping();
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
        verify(structuredLlmClient, never()).mapBatch(anyString(), anyInt());
    }

    @Test
    void llmValidLedgerIsAssignedAndInvalidIsUnmapped() {
        ensureLedger("Internet Charges", "Indirect Expenses");
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(
                LlmBatchResult.complete(List.of(new LedgerMappingResult("1", "Internet Charges"))),
                LlmBatchResult.complete(List.of(new LedgerMappingResult("1", "Not A Real Ledger"))));

        RunResponse first = bankReconService.uploadStatement(csvWithSinglePayment("CLOUD HOSTING AWS"), "tester");
        UUID txId = first.transactions().getFirst().id();
        var tx = transactionRepository.findById(txId).orElseThrow();
        assertThat(tx.getMappedLedger()).isEqualTo("Internet Charges");
        assertThat(tx.getMappingSource()).isEqualTo(MappingSource.LLM);

        tx.setMappedLedger(null);
        tx.setMappingSource(null);
        bankReconService.mapBatchWithLlm(List.of(tx));
        assertThat(tx.getMappedLedger()).isNull();
        assertThat(tx.getMappingSource()).isNull();
    }

    @Test
    void paymentRowSeesAnAssetLedgerAndOneCatalogIsShared() {
        jdbcTemplate.update("""
                INSERT INTO tally_ledger_group (group_name, accounting_nature)
                VALUES ('Loans and Advances', 'Asset')
                ON CONFLICT (group_name) DO NOTHING
                """);
        ensureLedger("Salary Advance - Rahul", "Loans and Advances");
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(
                LlmBatchResult.complete(List.of(new LedgerMappingResult("1", "Salary Advance - Rahul"))));

        RunResponse run = bankReconService.uploadStatement(
                csvWithSinglePayment("SALARY ADVANCE RAHUL UNIQUE"), "tester");
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> tokens = ArgumentCaptor.forClass(Integer.class);
        verify(structuredLlmClient).mapBatch(prompts.capture(), tokens.capture());
        assertThat(prompts.getValue()).contains("Salary Advance - Rahul");
        assertThat(prompts.getValue()).contains("type=PAYMENT");
        assertThat(tokens.getValue()).isEqualTo(540);
        TransactionResponse tx = run.transactions().getFirst();
        assertThat(tx.mappedLedger()).isEqualTo("Salary Advance - Rahul");
        assertThat(tx.llmNameResolution()).isEqualTo("EXACT");

        String payment = bankReconService.buildPrompt(List.of(promptTransaction("PAY CATALOG ROW")));
        ReconTransaction receipt = ReconTransaction.builder()
                .id(UUID.randomUUID())
                .description("RECEIPT CATALOG ROW")
                .normalisedDescription(NarrationNormalizer.normalise("RECEIPT CATALOG ROW"))
                .amount(new BigDecimal("100.00"))
                .debitCredit(DebitCredit.C)
                .voucherType(VoucherType.RECEIPT)
                .build();
        String receiptPrompt = bankReconService.buildPrompt(List.of(receipt));
        assertThat(MappingPrompt.catalogSection(payment)).isEqualTo(MappingPrompt.catalogSection(receiptPrompt));
        assertThat(MappingPrompt.catalogSection(payment)).contains("Salary Advance - Rahul");
        assertThat(payment).contains("type=PAYMENT");
        assertThat(receiptPrompt).contains("type=RECEIPT");
    }

    @Test
    void truncatedReplyRetriesTheRestOnceAtDoubleTheCeiling() {
        ensureLedger("Internet Charges", "Indirect Expenses");
        ensureLedger("Office Rent Retry", "Indirect Expenses");
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenAnswer(invocation -> {
            int maxTokens = invocation.getArgument(1);
            if (maxTokens == 580) {
                return new LlmBatchResult(
                        List.of(new LedgerMappingResult("1", "Internet Charges")),
                        true,
                        "[{\"transactionId\":1,\"ledgerName\":\"Internet Charges\"}",
                        1,
                        1,
                        true,
                        "length",
                        null);
            }
            return LlmBatchResult.complete(List.of(new LedgerMappingResult("1", "Office Rent Retry")));
        });

        RunResponse run = bankReconService.uploadStatement(
                csvWithPayments("TRUNC ROW ONE UNIQUE", "TRUNC ROW TWO UNIQUE"), "tester");
        ArgumentCaptor<Integer> tokens = ArgumentCaptor.forClass(Integer.class);
        verify(structuredLlmClient, times(2)).mapBatch(anyString(), tokens.capture());
        assertThat(tokens.getAllValues()).containsExactly(580, 1160);
        assertThat(run.transactions()).extracting(TransactionResponse::mappedLedger)
                .containsExactly("Internet Charges", "Office Rent Retry");
        assertThat(run.processingError()).isNull();
    }

    @Test
    void aSecondTruncatedReplyRecordsTheRunError() {
        ensureLedger("Internet Charges", "Indirect Expenses");
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(
                new LlmBatchResult(
                        List.of(new LedgerMappingResult("1", "Internet Charges")),
                        true, "partial", 1, 1, true, "length", null),
                new LlmBatchResult(List.of(), true, "", 0, 0, true, "length", null));

        RunResponse run = bankReconService.uploadStatement(
                csvWithPayments("TRUNC FAIL ONE UNIQUE", "TRUNC FAIL TWO UNIQUE"), "tester");
        assertThat(run.transactions().get(0).mappedLedger()).isEqualTo("Internet Charges");
        assertThat(run.transactions().get(1).mappedLedger()).isNull();
        assertThat(run.processingError()).isEqualTo("LLM response truncated");
    }

    @Test
    void aFailedBatchLeavesOnlyItsOwnRowsUnmapped() {
        ensureLedger("Internet Charges", "Indirect Expenses");
        jdbcTemplate.update(
                "UPDATE general_config SET config_value = '1' WHERE config_key = 'llm_batch_size'");
        try {
            when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(
                    LlmBatchResult.complete(List.of(new LedgerMappingResult("1", "Internet Charges"))),
                    LlmBatchResult.failed("connection refused"));
            RunResponse run = bankReconService.uploadStatement(
                    csvWithPayments("BATCH OK UNIQUE", "BATCH FAIL UNIQUE"), "tester");
            assertThat(run.transactions().get(0).mappedLedger()).isEqualTo("Internet Charges");
            assertThat(run.transactions().get(1).mappedLedger()).isNull();
            assertThat(run.processingError()).contains("connection refused");
            verify(structuredLlmClient, times(2)).mapBatch(anyString(), anyInt());
        } finally {
            jdbcTemplate.update(
                    "UPDATE general_config SET config_value = '20' WHERE config_key = 'llm_batch_size'");
        }
    }

    @Test
    void ambiguousAndResolvedNamesAreRecordedOnTheRow() {
        ensureLedger("Sky Enterprises", "Indirect Expenses");
        ensureLedger("Acme Pvt Ltd", "Indirect Expenses");
        ensureLedger("Acme Private Limited", "Indirect Expenses");
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.complete(List.of(
                new LedgerMappingResult("1", "Sky Enterprises"),
                new LedgerMappingResult("2", "Sky Enterprises 2"),
                new LedgerMappingResult("3", "acme pvt ltd"))));

        RunResponse run = bankReconService.uploadStatement(
                csvWithPayments("SKY EXACT UNIQUE", "SKY CLOSE UNIQUE", "ACME AMBIGUOUS UNIQUE"), "tester");
        assertThat(run.transactions()).hasSize(3);
        assertThat(run.transactions().get(0).mappedLedger()).isEqualTo("Sky Enterprises");
        assertThat(run.transactions().get(0).llmNameResolution()).isEqualTo("EXACT");
        assertThat(run.transactions().get(1).mappedLedger()).isEqualTo("Sky Enterprises");
        assertThat(run.transactions().get(1).llmNameResolution()).isEqualTo("RESOLVED");
        assertThat(run.transactions().get(2).mappedLedger()).isNull();
        assertThat(run.transactions().get(2).reviewTag()).isEqualTo("AMBIGUOUS");
        assertThat(run.processingError()).isNull();
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
    void ledgerMasterExportAppliesFiltersAndStoresFormulaLikeNamesAsText() throws Exception {
        ensureLedger("=LMXPORT9 Alpha", "Indirect Expenses");
        ensureLedger("LMXPORT9 Beta", "Indirect Expenses");
        UUID alphaId = bankReconService.listLedgers("=LMXPORT9 Alpha", 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals("=LMXPORT9 Alpha"))
                .findFirst()
                .orElseThrow()
                .id();
        bankReconService.saveLedgerHint(alphaId, new com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerHintRequest(
                "Pay vendors", "NEFT, vendor", "small, under 5000", "Not the other ledger", null), "tester");

        BankReconService.ExportFile both = bankReconService.exportLedgers("LMXPORT9", null, null, null);
        assertThat(both.filename()).matches("ledger-master-\\d{4}-\\d{2}-\\d{2}\\.xlsx");
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(both.bytes()))) {
            XSSFSheet sheet = (XSSFSheet) wb.getSheet("Ledger Master");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(java.util.stream.StreamSupport.stream(sheet.getRow(0).spliterator(), false)
                    .map(cell -> cell.getStringCellValue()).toList()).containsExactly(
                            "Ledger Name", "Group", "Accounting Nature", "Bank Account", "Active",
                            "Ledger Code", "In Tally XML", "Purpose", "Keywords", "Typical Amount",
                            "Disambiguation Note");
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:K3");
            byte[] headerRgb = ((XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle())
                    .getFillForegroundXSSFColor().getRGB();
            assertThat(headerRgb).containsExactly((byte) 0xF6, (byte) 0x8C, (byte) 0x45);

            Row alpha = sheet.getRow(1);
            Row beta = sheet.getRow(2);
            assertThat(alpha.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(alpha.getCell(0).getStringCellValue()).isEqualTo("=LMXPORT9 Alpha");
            assertThat(alpha.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(alpha.getCell(5).getStringCellValue()).isEmpty();
            assertThat(alpha.getCell(6).getStringCellValue()).isEqualTo("No");
            assertThat(alpha.getCell(7).getStringCellValue()).isEqualTo("Pay vendors");
            assertThat(alpha.getCell(8).getStringCellValue()).isEqualTo("NEFT, vendor");
            assertThat(alpha.getCell(9).getStringCellValue()).isEqualTo("small, under 5000");
            assertThat(alpha.getCell(10).getStringCellValue()).isEqualTo("Not the other ledger");
            assertThat(alpha.getCell(7).getCellType()).isEqualTo(CellType.STRING);

            assertThat(beta.getCell(0).getStringCellValue()).isEqualTo("LMXPORT9 Beta");
            assertThat(beta.getCell(5).getStringCellValue()).isEmpty();
            assertThat(beta.getCell(6).getStringCellValue()).isEqualTo("No");
            assertThat(beta.getCell(7).getStringCellValue()).isEmpty();
            assertThat(beta.getCell(8).getStringCellValue()).isEmpty();
            assertThat(beta.getCell(9).getStringCellValue()).isEmpty();
            assertThat(beta.getCell(10).getStringCellValue()).isEmpty();
        }

        BankReconService.ExportFile hintedOnly = bankReconService.exportLedgers("LMXPORT9", null, null, true);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(hintedOnly.bytes()))) {
            Sheet sheet = wb.getSheet("Ledger Master");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("=LMXPORT9 Alpha");
        }

        BankReconService.ExportFile searched = bankReconService.exportLedgers("LMXPORT9 Beta", null, null, null);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(searched.bytes()))) {
            Sheet sheet = wb.getSheet("Ledger Master");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("LMXPORT9 Beta");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("No");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void viewerCanExportLedgerMaster() throws Exception {
        if (userService.findByEmail("ledger-export-viewer@cognologix.com").isEmpty()) {
            userService.createUser(
                    "ledger-export-viewer@cognologix.com", "Ledger Viewer", UserRole.VIEWER, "Secret123!", "tester");
        }
        String token = jwtTokenProvider.generateToken("ledger-export-viewer@cognologix.com", "VIEWER", false);
        mockMvc.perform(get("/api/bank-recon/ledgers/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"ledger-master-")))
                .andExpect(header().string("Content-Disposition", containsString(".xlsx\"")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
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

    @Test
    void uploadBlockedWhenAccountHasNoMapping() {
        jdbcTemplate.update("DELETE FROM recon_account_mapping WHERE identifier = ?", "50100123456789");
        assertThatThrownBy(() -> bankReconService.uploadStatement(sampleCsv(), "tester"))
                .isInstanceOf(UnmappedAccountException.class)
                .hasMessage("No ledger is mapped for account 50100123456789. Add it in Configuration → Account Mapping.");
    }

    @Test
    void exportUsesBankLedgerNameForPaymentReceiptAndContra() throws Exception {
        ensureLedger("Deutsche Bank", "Bank Accounts");
        ensureLedger("HDFC Operating", "Bank Accounts");
        ensureLedger("Rent", "Indirect Expenses");
        ensureLedger("Client Receipts", "Indirect Income");
        bankReconService.createAccountMapping(
                new AccountMappingRequest("HDFC_BANK", "88880000111122", "HDFC Operating", true), "tester");
        String csv = """
                Account Number,88880000111122
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,OFFICE RENT AUG,40000.00,D,N1,01/08/2026,PUNE,60000.00
                02/08/2026,CLIENT PAYMENT ACME,80000.00,C,I2,02/08/2026,PUNE,140000.00
                03/08/2026,NEFT COGNOLOGIX TO OTHER BANK,1500.00,D,C3,03/08/2026,PUNE,138500.00
                """;
        RunResponse run = bankReconService.uploadStatement(multipart("operating.csv", csv), "tester");
        assertThat(run.bankLedgerName()).isEqualTo("HDFC Operating");
        TransactionResponse payment = run.transactions().stream()
                .filter(t -> t.voucherType() == VoucherType.PAYMENT).findFirst().orElseThrow();
        TransactionResponse receipt = run.transactions().stream()
                .filter(t -> t.voucherType() == VoucherType.RECEIPT).findFirst().orElseThrow();
        TransactionResponse contra = run.transactions().stream()
                .filter(t -> t.voucherType() == VoucherType.CONTRA).findFirst().orElseThrow();
        assertThat(contra.mappedLedger()).isNull();
        bankReconService.updateTransactionMapping(run.id(), payment.id(), "Rent", "PAYMENT", false, "finance");
        bankReconService.updateTransactionMapping(run.id(), receipt.id(), "Client Receipts", "RECEIPT", false, "finance");
        bankReconService.updateTransactionMapping(run.id(), contra.id(), "Deutsche Bank", "CONTRA", false, "finance");

        BankReconService.ExportFile file = bankReconService.generateExport(
                run.id(), List.of(payment.id(), receipt.id(), contra.id()), "finance");
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
            Row payRow = wb.getSheet("Payment").getRow(1);
            assertThat(payRow.getCell(3).getStringCellValue()).isEqualTo("Rent");
            assertThat(payRow.getCell(4).getStringCellValue()).isEqualTo("HDFC Operating");
            Row recRow = wb.getSheet("Receipt").getRow(1);
            assertThat(recRow.getCell(3).getStringCellValue()).isEqualTo("HDFC Operating");
            assertThat(recRow.getCell(4).getStringCellValue()).isEqualTo("Client Receipts");
            Row contraRow = wb.getSheet("Contra").getRow(1);
            assertThat(contraRow.getCell(3).getStringCellValue()).isEqualTo("Deutsche Bank");
            assertThat(contraRow.getCell(4).getStringCellValue()).isEqualTo("HDFC Operating");
        }
    }

    @Test
    void contraRowsStayUnmappedAndAreNotSentToTheLlm() {
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        RunResponse run = bankReconService.uploadStatement(csvWithContraMarker(), "tester");
        TransactionResponse contra = run.transactions().stream()
                .filter(t -> t.description().contains("ZZCONTRAMARKER"))
                .findFirst()
                .orElseThrow();
        assertThat(contra.voucherType()).isEqualTo(VoucherType.CONTRA);
        assertThat(contra.mappedLedger()).isNull();
        assertThat(contra.mappingSource()).isNull();
        verify(structuredLlmClient, atLeastOnce()).mapBatch(prompts.capture(), anyInt());
        String contraId = contra.id().toString();
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> {
            int section = prompt.indexOf("TRANSACTIONS\n");
            assertThat(section).isGreaterThanOrEqualTo(0);
            String sent = prompt.substring(section);
            assertThat(sent).doesNotContain("ZZCONTRAMARKER");
            assertThat(sent).doesNotContain(contraId);
        });
    }

    @Test
    void ledgerReimportKeepsHintsAndDoesNotDeleteTheLedger() {
        String name = "Hinted Vendor Ledger";
        MockMultipartFile first = xmlFile("""
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="Hinted Vendor Ledger">
                    <PARENT>Sundry Creditors</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """);
        bankReconService.importLedgerMaster(first);
        UUID ledgerId = bankReconService.listLedgers(name, 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
        bankReconService.saveLedgerHint(ledgerId, new LedgerHintRequest(
                "Pay vendors", "NEFT, vendor", "small, under ₹5,000", "Not the 10% Prof Fees ledger", null), "finance");
        jdbcTemplate.update("UPDATE tally_ledger SET is_bank_account = true WHERE id = ?", ledgerId);

        bankReconService.importLedgerMaster(xmlFile("""
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="Hinted Vendor Ledger">
                    <PARENT>Indirect Expenses</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """));

        var reloaded = bankReconService.listLedgers(name, 0, 20).content().stream()
                .filter(l -> l.ledgerName().equals(name))
                .findFirst()
                .orElseThrow();
        assertThat(reloaded.groupName()).isEqualTo("Indirect Expenses");
        assertThat(reloaded.accountingNature()).isEqualTo("Expense");
        assertThat(reloaded.hasHint()).isTrue();
        Boolean stillBank = jdbcTemplate.queryForObject(
                "SELECT is_bank_account FROM tally_ledger WHERE id = ?", Boolean.class, ledgerId);
        assertThat(stillBank).isTrue();
        var hint = bankReconService.getLedgerHint(ledgerId);
        assertThat(hint.purpose()).isEqualTo("Pay vendors");
        assertThat(hint.keywords()).isEqualTo("NEFT, vendor");
        assertThat(hint.typicalAmount()).isEqualTo("small, under ₹5,000");
        assertThat(hint.disambiguationNote()).isEqualTo("Not the 10% Prof Fees ledger");

        bankReconService.importLedgerMaster(xmlFile("""
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="Some Other Vendor">
                    <PARENT>Sundry Creditors</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """));
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tally_ledger WHERE id = ?", Integer.class, ledgerId);
        assertThat(remaining).isEqualTo(1);
        assertThat(bankReconService.getLedgerHint(ledgerId).present()).isTrue();
    }

    @Test
    void catalogExcludesBankGroupsAndMappedLedgersAndStaysIdentical() {
        ensureLedger("Rent Payable Test", "Indirect Expenses");
        ensureLedger("Deutsche Bank", "Bank Accounts");
        ensureLedger("OD Line Test", "Bank OD Accounts");
        jdbcTemplate.update("""
                INSERT INTO tally_ledger_group (group_name, accounting_nature)
                VALUES ('Credit Cards', 'Liability')
                ON CONFLICT (group_name) DO NOTHING
                """);
        ensureLedger("Amex Corporate 4242", "Credit Cards");
        ensureLedger("Mapped Side Ledger", "Indirect Expenses");
        if (bankReconService.listAccountMappings().stream()
                .noneMatch(m -> "mapped-side-ledger".equals(m.identifier()))) {
            bankReconService.createAccountMapping(
                    new AccountMappingRequest("HDFC_BANK", "mapped-side-ledger", "Mapped Side Ledger", true),
                    "tester");
        }

        String first = bankReconService.buildPrompt(List.of(promptTransaction("ALPHA UNIQUE NARRATION ONE")));
        String second = bankReconService.buildPrompt(List.of(promptTransaction("BETA UNIQUE NARRATION TWO")));
        assertThat(MappingPrompt.staticPrefix(first)).isEqualTo(MappingPrompt.staticPrefix(second));
        assertThat(first).isNotEqualTo(second);
        String catalog = MappingPrompt.catalogSection(first);
        assertThat(catalog).contains("Rent Payable Test");
        assertThat(catalog).doesNotContain("HDFC Bank");
        assertThat(catalog).doesNotContain("Deutsche Bank");
        assertThat(catalog).doesNotContain("Amex Corporate 4242");
        assertThat(catalog).doesNotContain("OD Line Test");
        assertThat(catalog).doesNotContain("Mapped Side Ledger");
    }

    @Test
    void confirmedMappingStoresLastAmountAndExamplesIncludeIt() {
        ensureLedger("Software Subscriptions", "Indirect Expenses");
        RunResponse run = bankReconService.uploadStatement(
                csvWithAmount("ADOBE CREATIVE CLOUD", "4321.50"), "tester");
        UUID txId = run.transactions().getFirst().id();
        bankReconService.updateTransactionMapping(
                run.id(), txId, "Software Subscriptions", "PAYMENT", false, "finance");
        var mapping = learnedMappingRepository
                .findByNormalisedNarrationAndVoucherType(
                        NarrationNormalizer.normalise("ADOBE CREATIVE CLOUD"), VoucherType.PAYMENT)
                .orElseThrow();
        assertThat(mapping.getLastAmount()).isEqualByComparingTo("4321.50");

        String prompt = bankReconService.buildPrompt(List.of(promptTransaction("ADOBE CREATIVE CLOUD RENEWAL")));
        assertThat(prompt).contains("amount=4321.50");
        assertThat(prompt).contains("ledger=Software Subscriptions");

        RunResponse again = bankReconService.uploadStatement(
                csvWithAmount("ADOBE CREATIVE CLOUD", "10.00"), "tester");
        assertThat(again.transactions().getFirst().mappingSource()).isEqualTo(MappingSource.LEARNED);
        assertThat(learnedMappingRepository.findById(mapping.getId()).orElseThrow().getLastAmount())
                .isEqualByComparingTo("4321.50");
    }

    private void seedLedgers() {
        ensureLedger("HDFC Bank", "Bank Accounts");
    }

    private void seedAccountMapping() {
        boolean exists = bankReconService.listAccountMappings().stream()
                .anyMatch(m -> "HDFC_BANK".equals(m.statementType()) && "50100123456789".equals(m.identifier()));
        if (!exists) {
            bankReconService.createAccountMapping(
                    new AccountMappingRequest("HDFC_BANK", "50100123456789", "HDFC Bank", true), "tester");
        }
    }

    @Test
    void ledgerCodeRulesRejectReservedAndDuplicateCodes() {
        ensureLedger("Code Rules Alpha", "Indirect Expenses");
        ensureLedger("Code Rules Beta", "Indirect Expenses");
        UUID alpha = ledgerId("Code Rules Alpha");
        UUID beta = ledgerId("Code Rules Beta");
        bankReconService.saveLedgerHint(alpha, new LedgerHintRequest(null, null, null, null, "ab12"), "tester");
        assertThat(ledger("Code Rules Alpha").ledgerCode()).isEqualTo("AB12");

        assertThatThrownBy(() -> bankReconService.saveLedgerHint(
                beta, new LedgerHintRequest(null, null, null, null, "neft"), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Code NEFT is reserved");
        assertThatThrownBy(() -> bankReconService.saveLedgerHint(
                beta, new LedgerHintRequest(null, null, null, null, "A"), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Code must be 2 to 10 letters and digits");
        assertThatThrownBy(() -> bankReconService.saveLedgerHint(
                beta, new LedgerHintRequest(null, null, null, null, "ab12"), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Code AB12 is already used by Code Rules Alpha");
        assertThat(ledger("Code Rules Beta").ledgerCode()).isNull();
    }

    @Test
    void tallyReimportKeepsCodesAndAppAddedLedgers() {
        bankReconService.importLedgerMaster(xmlFile("""
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="Xml Kept Vendor 42"><PARENT>Sundry Creditors</PARENT></LEDGER>
                </ENVELOPE>
                """));
        bankReconService.saveLedgerHint(
                ledgerId("Xml Kept Vendor 42"), new LedgerHintRequest(null, null, null, null, "KEEP9"), "tester");
        bankReconService.addLedger(new CreateLedgerRequest("App Then Xml 42", "Indirect Expenses"));
        bankReconService.saveLedgerHint(
                ledgerId("App Then Xml 42"), new LedgerHintRequest(null, null, null, null, "APPX"), "tester");
        bankReconService.addLedger(new CreateLedgerRequest("App Only Vendor 42", "Indirect Expenses"));

        bankReconService.importLedgerMaster(xmlFile("""
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="App Then Xml 42"><PARENT>Indirect Expenses</PARENT></LEDGER>
                </ENVELOPE>
                """));

        LedgerResponse kept = ledger("Xml Kept Vendor 42");
        assertThat(kept.ledgerCode()).isEqualTo("KEEP9");
        assertThat(kept.addedInApp()).isFalse();
        LedgerResponse seen = ledger("App Then Xml 42");
        assertThat(seen.ledgerCode()).isEqualTo("APPX");
        assertThat(seen.addedInApp()).isFalse();
        LedgerResponse only = ledger("App Only Vendor 42");
        assertThat(only.addedInApp()).isTrue();
        assertThat(only.ledgerCode()).isNull();
    }

    @Test
    void ledgerCodeMatchUsesRawNarrationAndSkipsTheLlm() {
        ensureLedger("Sky Payee Co", "Indirect Expenses");
        ensureLedger("Sky Raw Co", "Indirect Expenses");
        ensureLedger("Digit Tail Co", "Indirect Expenses");
        ensureLedger("Sky Conflict Co", "Indirect Expenses");
        ensureLedger("Acme Conflict Co", "Indirect Expenses");
        bankReconService.saveLedgerHint(ledgerId("Sky Payee Co"), new LedgerHintRequest(null, null, null, null, "SKY9"), "tester");
        bankReconService.saveLedgerHint(ledgerId("Sky Raw Co"), new LedgerHintRequest(null, null, null, null, "SKY2AB"), "tester");
        bankReconService.saveLedgerHint(ledgerId("Digit Tail Co"), new LedgerHintRequest(null, null, null, null, "445566"), "tester");
        bankReconService.saveLedgerHint(ledgerId("Sky Conflict Co"), new LedgerHintRequest(null, null, null, null, "SKYC"), "tester");
        bankReconService.saveLedgerHint(ledgerId("Acme Conflict Co"), new LedgerHintRequest(null, null, null, null, "ACMEC"), "tester");

        RunResponse matched = bankReconService.uploadStatement(
                csvWithSinglePayment("UPI/SKY9/VENDOR 998877665544"), "tester");
        TransactionResponse payee = matched.transactions().get(0);
        assertThat(payee.mappingSource()).isEqualTo(MappingSource.CODE);
        assertThat(payee.mappedLedger()).isEqualTo("Sky Payee Co");
        assertThat(payee.note()).isEqualTo("SKY9 at position 2");
        assertThat(payee.notInTally()).isTrue();
        verify(structuredLlmClient, never()).mapBatch(anyString(), anyInt());
        assertThat(learnedMappingRepository.findByNormalisedNarrationAndVoucherType(
                payee.normalisedDescription(), VoucherType.PAYMENT)).isEmpty();

        TransactionResponse same = bankReconService.updateTransactionMapping(
                matched.id(), payee.id(), "Sky Payee Co", null, null, "tester");
        assertThat(same.mappingSource()).isEqualTo(MappingSource.CODE);
        assertThat(learnedMappingRepository.findByNormalisedNarrationAndVoucherType(
                payee.normalisedDescription(), VoucherType.PAYMENT)).isEmpty();

        TransactionResponse corrected = bankReconService.updateTransactionMapping(
                matched.id(), payee.id(), "Sky Raw Co", null, null, "tester");
        assertThat(corrected.mappingSource()).isEqualTo(MappingSource.MANUAL);
        assertThat(corrected.note()).isNull();
        assertThat(learnedMappingRepository.findByNormalisedNarrationAndVoucherType(
                payee.normalisedDescription(), VoucherType.PAYMENT)).isPresent();

        reset(structuredLlmClient);
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.complete(List.of()));
        RunResponse raw = bankReconService.uploadStatement(
                csvWithSinglePayment("NEFT-SKY2AB PAY 111222333444"), "tester");
        TransactionResponse rawRow = raw.transactions().get(0);
        assertThat(rawRow.mappingSource()).isEqualTo(MappingSource.CODE);
        assertThat(rawRow.mappedLedger()).isEqualTo("Sky Raw Co");
        assertThat(rawRow.normalisedDescription()).doesNotContain("SKY2AB");
        verify(structuredLlmClient, never()).mapBatch(anyString(), anyInt());

        reset(structuredLlmClient);
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.complete(List.of()));
        RunResponse tail = bankReconService.uploadStatement(
                csvWithSinglePayment("NEFT VENDOR 123456789012 445566"), "tester");
        assertThat(tail.transactions().get(0).mappingSource()).isNotEqualTo(MappingSource.CODE);
        assertThat(tail.transactions().get(0).mappedLedger()).isNull();
        verify(structuredLlmClient).mapBatch(anyString(), anyInt());

        reset(structuredLlmClient);
        when(structuredLlmClient.mapBatch(anyString(), anyInt())).thenReturn(LlmBatchResult.complete(List.of()));
        RunResponse conflict = bankReconService.uploadStatement(
                csvWithSinglePayment("NEFT SKYC ACMEC CODECONFLICT99 998877665544"), "tester");
        TransactionResponse conflicted = conflict.transactions().get(0);
        assertThat(conflicted.mappingSource()).isNotEqualTo(MappingSource.CODE);
        assertThat(conflicted.mappedLedger()).isNull();
        assertThat(conflicted.note()).isEqualTo("conflict: Acme Conflict Co, Sky Conflict Co");
        verify(structuredLlmClient).mapBatch(anyString(), anyInt());
    }

    @Test
    void ledgerCodeImportPreviewsErrorsAndSavesANewLedger() throws Exception {
        String existing = "Sky Enterprises CODEIMP77";
        ensureLedger(existing, "Indirect Expenses");
        bankReconService.saveLedgerHint(
                ledgerId(existing),
                new LedgerHintRequest("Keep me", "old keyword", null, null, "SKY77"),
                "tester");

        MockMultipartFile errors = codeWorkbook(new String[][] {
                {"sky enterprises codeimp77", "ACME77", "", "", "", "", ""},
                {"Brand New CODEIMP77", "NEFT", "Indirect Expenses", "", "", "", ""},
                {"No Group CODEIMP77", "NOG77", "", "", "", "", ""},
                {"Unknown Group CODEIMP77", "UNK77", "Not A Group", "", "", "", ""},
                {"Short Code CODEIMP77", "A", "Indirect Expenses", "", "", "", ""},
                {"Dup One CODEIMP77", "DUP77", "Indirect Expenses", "", "", "", ""},
                {"Dup Two CODEIMP77", "DUP77", "Indirect Expenses", "", "", "", ""},
                {"Taken CODEIMP77", "SKY77", "Indirect Expenses", "", "", "", ""}
        });
        LedgerCodeImportPreview preview = bankReconService.importLedgerCodes(errors, true, "tester");
        assertThat(preview.saved()).isFalse();
        assertThat(preview.errors()).extracting(issue -> issue.message()).anySatisfy(message ->
                assertThat(message).isEqualTo("possible match: " + existing));
        assertThat(preview.errors()).extracting(issue -> issue.message())
                .contains(
                        "Code NEFT is reserved",
                        "Group is required for a new ledger",
                        "Unknown group: Not A Group",
                        "Code must be 2 to 10 letters and digits",
                        "Code DUP77 is duplicated in this file",
                        "Code SKY77 is already used by " + existing);
        assertThat(ledger(existing).ledgerCode()).isEqualTo("SKY77");
        assertThat(bankReconService.listLedgers("Brand New CODEIMP77", 0, 10).content()).isEmpty();

        MockMultipartFile clean = codeWorkbook(new String[][] {
                {existing, "SKY78", "", "", "", "", ""},
                {"New Vendor CODEIMP77", "NV77", "Indirect Expenses", "New purpose", "", "", ""}
        });
        LedgerCodeImportPreview dry = bankReconService.importLedgerCodes(clean, false, "tester");
        assertThat(dry.saved()).isFalse();
        assertThat(dry.errors()).isEmpty();
        assertThat(dry.toUpdate()).extracting(line -> line.ledgerName()).containsExactly(existing);
        assertThat(dry.toAdd()).extracting(line -> line.ledgerName()).containsExactly("New Vendor CODEIMP77");
        assertThat(ledger(existing).ledgerCode()).isEqualTo("SKY77");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ledger Name");
            header.createCell(1).setCellValue("Ledger Code");
            header.createCell(2).setCellValue("Group");
            Row numeric = sheet.createRow(1);
            numeric.createCell(0).setCellValue("Numeric Code CODEIMP77");
            numeric.createCell(1).setCellValue(778899);
            numeric.createCell(2).setCellValue("Indirect Expenses");
            ByteArrayOutputStream numericOut = new ByteArrayOutputStream();
            wb.write(numericOut);
            MockMultipartFile numericFile = new MockMultipartFile(
                    "file", "numeric.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    numericOut.toByteArray());
            LedgerCodeImportPreview numericPreview = bankReconService.importLedgerCodes(numericFile, false, "tester");
            assertThat(numericPreview.errors()).isEmpty();
            assertThat(numericPreview.toAdd()).extracting(line -> line.ledgerCode()).containsExactly("778899");
        }

        LedgerCodeImportPreview saved = bankReconService.importLedgerCodes(clean, true, "tester");
        assertThat(saved.saved()).isTrue();
        assertThat(saved.errors()).isEmpty();
        LedgerResponse updated = ledger(existing);
        assertThat(updated.ledgerCode()).isEqualTo("SKY78");
        assertThat(bankReconService.getLedgerHint(updated.id()).purpose()).isEqualTo("Keep me");
        assertThat(bankReconService.getLedgerHint(updated.id()).keywords()).isEqualTo("old keyword");
        LedgerResponse created = ledger("New Vendor CODEIMP77");
        assertThat(created.ledgerCode()).isEqualTo("NV77");
        assertThat(created.groupName()).isEqualTo("Indirect Expenses");
        assertThat(created.accountingNature()).isEqualTo("Expense");
        assertThat(created.bankAccount()).isFalse();
        assertThat(created.addedInApp()).isTrue();
        assertThat(bankReconService.getLedgerHint(created.id()).purpose()).isEqualTo("New purpose");
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recon_audit_log WHERE entity_type = 'tally_ledger' AND action = 'IMPORT' AND new_value = ?",
                Integer.class,
                "updated=1 added=1");
        assertThat(audits).isEqualTo(1);
    }

    private LedgerResponse ledger(String name) {
        return bankReconService.listLedgers(name, 0, 20).content().stream()
                .filter(row -> row.ledgerName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private UUID ledgerId(String name) {
        return ledger(name).id();
    }

    private static MockMultipartFile codeWorkbook(String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Ledger Master");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Ledger Name", "Ledger Code", "Group", "Purpose", "Keywords", "Typical Amount", "Disambiguation Note"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Cell cell = row.createCell(c);
                    if (rows[r][c] != null) {
                        cell.setCellValue(rows[r][c]);
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile(
                    "file", "codes.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
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

    private static MockMultipartFile csvWithContraMarker() {
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,NEFT COGNOLOGIX ZZCONTRAMARKER,10000.00,D,N1,01/08/2026,PUNE,90000.00
                01/08/2026,UPI-VENDOR PAYMENT XYZ,500.00,D,U2,01/08/2026,PUNE,89500.00
                01/08/2026,IMPS INWARD ACME LTD,25000.00,C,I3,01/08/2026,PUNE,114500.00
                """;
        return multipart("hdfc-contra.csv", csv);
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
        return csvWithAmount(narration, "500.00");
    }

    private static MockMultipartFile csvWithPayments(String... narrations) {
        StringBuilder rows = new StringBuilder();
        int balance = 1000;
        for (int i = 0; i < narrations.length; i++) {
            balance -= 10;
            rows.append("01/08/2026,").append(narrations[i]).append(",10.00,D,N")
                    .append(i + 1).append(",01/08/2026,PUNE,").append(balance).append(".00\n");
        }
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                """ + rows;
        return multipart("many.csv", csv);
    }

    private static MockMultipartFile csvWithAmount(String narration, String amount) {
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,%s,%s,D,N1,01/08/2026,PUNE,100.00
                """.formatted(narration, amount);
        return multipart("one.csv", csv);
    }

    private static ReconTransaction promptTransaction(String narration) {
        return ReconTransaction.builder()
                .id(UUID.randomUUID())
                .description(narration)
                .normalisedDescription(NarrationNormalizer.normalise(narration))
                .amount(new BigDecimal("100.00"))
                .debitCredit(DebitCredit.D)
                .voucherType(VoucherType.PAYMENT)
                .build();
    }

    private static MockMultipartFile xmlFile(String xml) {
        return new MockMultipartFile("file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile multipart(String name, String csv) {
        return new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
