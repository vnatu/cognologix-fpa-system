package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HdfcStatementParserTest {

    private final HdfcStatementParser parser = new HdfcStatementParser();

    @Test
    void parseHeadersReturnsHeaderFieldsAndTransactionColumns() {
        HdfcStatementParser.ParseHeadersResult result = parser.parseHeaders(sampleCsv());
        assertThat(result.headerFields()).contains("Account Number", "From Date", "To Date");
        assertThat(result.transactionColumns()).contains(
                "Transaction Date", "Transaction Description", "Transaction Amount", "Debit/Credit");
        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.headers()).contains("Account Number", "Transaction Date");
        assertThat(result.headerFieldValues()).containsEntry("Account Number", "50100123456789");
    }

    @Test
    void parseUsesMappingNotHardcodedColumnNames() {
        HdfcStatementParser.ParsedStatement parsed = parser.parse(sampleCsv(), defaultMapping());
        assertThat(parsed.accountNumber()).isEqualTo("50100123456789");
        assertThat(parsed.periodStart()).hasToString("2026-08-01");
        assertThat(parsed.periodEnd()).hasToString("2026-08-31");
        assertThat(parsed.rows()).hasSize(3);
        assertThat(parsed.rows().get(0).debitCredit()).isEqualTo(DebitCredit.D);
        assertThat(parsed.rows().get(0).amount()).isEqualByComparingTo("10000.00");
        assertThat(parsed.rows().get(0).transactionDate()).isNotNull();
        assertThat(parsed.rows().get(0).transactionDate().toString()).startsWith("2026-08-01");
        assertThat(parsed.rows().get(2).debitCredit()).isEqualTo(DebitCredit.C);
        assertThat(parsed.rows().get(2).amount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void parseMatchesNormalizedHeaderSpellings() {
        String csv = """
                account_number,50100123456789
                from-date,01/08/2026
                to date,31/08/2026

                txn date,narration,amt,d/c
                01/08/2026,FEE,10.00,D
                """;
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("Account Number", BankStatementSystemAttribute.ACCOUNT_NUMBER);
        mapping.put("From Date", BankStatementSystemAttribute.FROM_DATE);
        mapping.put("To Date", BankStatementSystemAttribute.TO_DATE);
        mapping.put("Txn Date", BankStatementSystemAttribute.TRANSACTION_DATE);
        mapping.put("Narration", BankStatementSystemAttribute.TRANSACTION_DESCRIPTION);
        mapping.put("Amt", BankStatementSystemAttribute.TRANSACTION_AMOUNT);
        mapping.put("D/C", BankStatementSystemAttribute.DEBIT_CREDIT);
        HdfcStatementParser.ParsedStatement parsed = parser.parse(multipart(csv), mapping);
        assertThat(parsed.accountNumber()).isEqualTo("50100123456789");
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().description()).isEqualTo("FEE");
        assertThat(parsed.rows().getFirst().amount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void parseExcelNumericDateAndBalanceCells() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            Sheet sheet = wb.createSheet();
            headerPair(sheet, 0, "Account Number", "50100123456789");
            headerPair(sheet, 1, "Customer Name", "Cognologix");
            Row from = sheet.createRow(2);
            from.createCell(0).setCellValue("From Date");
            Cell fromVal = from.createCell(1);
            fromVal.setCellValue(LocalDate.of(2026, 8, 1));
            fromVal.setCellStyle(dateStyle);
            Row to = sheet.createRow(3);
            to.createCell(0).setCellValue("To Date");
            Cell toVal = to.createCell(1);
            toVal.setCellValue(LocalDate.of(2026, 8, 31));
            toVal.setCellStyle(dateStyle);
            Row open = sheet.createRow(4);
            open.createCell(0).setCellValue("Opening Balance");
            open.createCell(1).setCellValue(100000.50);
            Row close = sheet.createRow(5);
            close.createCell(0).setCellValue("Closing Balance");
            close.createCell(1).setCellValue(114500.00);

            Row headers = sheet.createRow(7);
            String[] cols = {
                    "Transaction Date", "Transaction Description", "Transaction Amount", "Debit/Credit",
                    "Value Date", "Running Balance"
            };
            for (int i = 0; i < cols.length; i++) {
                headers.createCell(i).setCellValue(cols[i]);
            }
            Row tx = sheet.createRow(8);
            Cell txnDate = tx.createCell(0);
            txnDate.setCellValue(LocalDateTime.of(2026, 8, 1, 10, 15, 30));
            txnDate.setCellStyle(dateStyle);
            tx.createCell(1).setCellValue("UPI FEE");
            tx.createCell(2).setCellValue(10.00);
            tx.createCell(3).setCellValue("D");
            Cell valueDate = tx.createCell(4);
            valueDate.setCellValue(LocalDate.of(2026, 8, 1));
            valueDate.setCellStyle(dateStyle);
            tx.createCell(5).setCellValue(99990.00);
            wb.write(out);
            xlsx = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "hdfc.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
        Map<String, String> mapping = defaultMapping();
        mapping.put("Customer Name", BankStatementSystemAttribute.CUSTOMER_NAME);
        mapping.put("Opening Balance", BankStatementSystemAttribute.OPENING_BALANCE);
        mapping.put("Closing Balance", BankStatementSystemAttribute.CLOSING_BALANCE);

        HdfcStatementParser.ParsedStatement parsed = parser.parse(file, mapping);
        assertThat(parsed.customerName()).isEqualTo("Cognologix");
        assertThat(parsed.periodStart()).hasToString("2026-08-01");
        assertThat(parsed.periodEnd()).hasToString("2026-08-31");
        assertThat(parsed.openingBalance()).isEqualByComparingTo("100000.50");
        assertThat(parsed.closingBalance()).isEqualByComparingTo("114500.00");
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().transactionDate()).isEqualTo(
                LocalDateTime.of(2026, 8, 1, 10, 15, 30).toInstant(ZoneOffset.UTC));
        assertThat(parsed.rows().getFirst().valueDate()).hasToString("2026-08-01");
        assertThat(parsed.rows().getFirst().amount()).isEqualByComparingTo("10.00");
        assertThat(parsed.rows().getFirst().runningBalance()).isEqualByComparingTo("99990.00");
    }

    @Test
    void parseExcelUnformattedDateSerial() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            headerPair(sheet, 0, "Account Number", "1");
            headerPair(sheet, 1, "From Date", "01/08/2026");
            headerPair(sheet, 2, "To Date", "31/08/2026");
            Row headers = sheet.createRow(4);
            headers.createCell(0).setCellValue("Transaction Date");
            headers.createCell(1).setCellValue("Transaction Description");
            headers.createCell(2).setCellValue("Transaction Amount");
            headers.createCell(3).setCellValue("Debit/Credit");
            Row tx = sheet.createRow(5);
            tx.createCell(0).setCellValue(DateUtil.getExcelDate(
                    java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 8, 16, 9, 0, 0))));
            tx.createCell(1).setCellValue("SERIAL DATE");
            tx.createCell(2).setCellValue(1.00);
            tx.createCell(3).setCellValue("D");
            wb.write(out);
            xlsx = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "serial.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
        HdfcStatementParser.ParsedStatement parsed = parser.parse(file, defaultMapping());
        assertThat(parsed.rows().getFirst().transactionDate()).isNotNull();
        assertThat(parsed.rows().getFirst().transactionDate().toString()).startsWith("2026-08-16");
    }

    private static void headerPair(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    static MockMultipartFile sampleCsv() {
        String csv = """
                Account Number,50100123456789
                From Date,01/08/2026
                To Date,31/08/2026

                Transaction Date,Transaction Description,Transaction Amount,Debit/Credit,Reference No,Value Date,Transaction Branch,Running Balance
                01/08/2026,NEFT COGNOLOGIX INTERNAL TO HDFC 2,10000.00,D,N1,01/08/2026,PUNE,90000.00
                01/08/2026,UPI-VENDOR PAYMENT XYZ,500.00,D,U2,01/08/2026,PUNE,89500.00
                01/08/2026,IMPS INWARD ACME LTD,25000.00,C,I3,01/08/2026,PUNE,114500.00
                """;
        return multipart(csv);
    }

    static Map<String, String> defaultMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("Account Number", BankStatementSystemAttribute.ACCOUNT_NUMBER);
        mapping.put("Customer Name", BankStatementSystemAttribute.CUSTOMER_NAME);
        mapping.put("From Date", BankStatementSystemAttribute.FROM_DATE);
        mapping.put("To Date", BankStatementSystemAttribute.TO_DATE);
        mapping.put("Opening Balance", BankStatementSystemAttribute.OPENING_BALANCE);
        mapping.put("Closing Balance", BankStatementSystemAttribute.CLOSING_BALANCE);
        mapping.put("Transaction Date", BankStatementSystemAttribute.TRANSACTION_DATE);
        mapping.put("Transaction Description", BankStatementSystemAttribute.TRANSACTION_DESCRIPTION);
        mapping.put("Transaction Amount", BankStatementSystemAttribute.TRANSACTION_AMOUNT);
        mapping.put("Debit/Credit", BankStatementSystemAttribute.DEBIT_CREDIT);
        mapping.put("Reference No", BankStatementSystemAttribute.REFERENCE_NO);
        mapping.put("Value Date", BankStatementSystemAttribute.VALUE_DATE);
        mapping.put("Transaction Branch", BankStatementSystemAttribute.TRANSACTION_BRANCH);
        mapping.put("Running Balance", BankStatementSystemAttribute.RUNNING_BALANCE);
        return mapping;
    }

    private static MockMultipartFile multipart(String csv) {
        return new MockMultipartFile("file", "hdfc.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
