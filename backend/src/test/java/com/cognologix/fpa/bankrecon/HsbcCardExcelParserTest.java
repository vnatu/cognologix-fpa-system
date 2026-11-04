package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.ReconTransaction;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HsbcCardExcelParserTest {

    private static final String[] HEADERS = {
            "Masked Card Number", "Name On Card", "Transaction Date", "Posting Date", "Merchant Name",
            "Merchant City", "Net Amount", "Tax Amount", "Amount", "Currency", "Foreign Amount",
            "Foreign Currency", "Statement Date", "Merchant Category", "Merchant Country",
            "Bank Reference", "Transaction Type", "Employee ID", "Cost Center"
    };

    private final HsbcCardExcelParser parser = new HsbcCardExcelParser();

    @Test
    void parsesAPurchaseInTheHsbcLayout() {
        Map<String, String> row = sample();
        HsbcCardExcelParser.ParsedRow parsed = only(parser.parse(workbook(List.of(row)), "Purchase", "CREDIT CARD PAYMENT"));

        assertThat(parsed.transactionDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(parsed.statementDate()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(parsed.debitCredit()).isEqualTo(DebitCredit.D);
        assertThat(parsed.voucherType()).isEqualTo(VoucherType.PAYMENT);
        assertThat(parsed.amount()).isEqualByComparingTo("100.50");
        assertThat(parsed.narration()).isEqualTo("NORTHWIND CAFE PUNE");
        assertThat(parsed.narration()).doesNotContain("OFFICE");
        assertThat(parsed.cardLast4()).isEqualTo("1111");
        assertThat(parsed.nameOnCard()).isEqualTo("NORTHWIND OFFICE");
        assertThat(parsed.merchantCategory()).isEqualTo("EATING PLACES");
        assertThat(parsed.bankReference()).isEqualTo("REF-100");
        assertThat(parsed.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(parsed.employeeId()).isEqualTo("E-1");
        assertThat(parsed.costCenter()).isEqualTo("CC-1");
        assertThat(HsbcCardVoucherRules.learnedKey(parsed.merchantName())).isEqualTo("NORTHWIND CAFE");
        assertThat(HsbcCardVoucherRules.learnedKey(parsed.merchantName())).doesNotContain("PUNE");
    }

    @Test
    void appendsForeignAmountAndCountryWhenTheCountryIsNotInd() {
        Map<String, String> row = sample();
        row.put("Merchant Name", "ATLAS SOFTWARE");
        row.put("Merchant City", "AUSTIN");
        row.put("Merchant Country", "USA");
        row.put("Foreign Amount", "12.00");
        row.put("Foreign Currency", "USD");
        row.put("Amount", "1000.00");
        row.put("Bank Reference", "REF-FX");

        HsbcCardExcelParser.ParsedRow parsed = only(parser.parse(workbook(List.of(row)), "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(parsed.narration()).isEqualTo("ATLAS SOFTWARE AUSTIN USA USD 12.00");
        assertThat(parsed.amount()).isEqualByComparingTo("1000.00");
        assertThat(parsed.importFlag()).isNull();
    }

    @Test
    void refundIsACreditReceiptAndACardPaymentIsContra() {
        Map<String, String> refund = sample();
        refund.put("Transaction Type", "Refund");
        refund.put("Amount", "40.00");
        refund.put("Bank Reference", "REF-REFUND");
        refund.put("Merchant Name", "NORTHWIND CAFE");

        Map<String, String> payment = sample();
        payment.put("Transaction Type", "Payment");
        payment.put("Merchant Name", "CREDIT CARD PAYMENT");
        payment.put("Merchant City", "");
        payment.put("Amount", "500.00");
        payment.put("Bank Reference", "REF-PAY");

        HsbcCardExcelParser.ParsedFile parsed = parser.parse(
                workbook(List.of(refund, payment)), "Purchase", "CREDIT CARD PAYMENT");
        HsbcCardExcelParser.ParsedRow refundRow = parsed.rows().get(0);
        assertThat(refundRow.debitCredit()).isEqualTo(DebitCredit.C);
        assertThat(refundRow.voucherType()).isEqualTo(VoucherType.RECEIPT);
        assertThat(refundRow.cardPayment()).isFalse();
        assertThat(refundRow.amount()).isEqualByComparingTo("40.00");

        HsbcCardExcelParser.ParsedRow paymentRow = parsed.rows().get(1);
        assertThat(paymentRow.cardPayment()).isTrue();
        assertThat(paymentRow.voucherType()).isEqualTo(VoucherType.CONTRA);
        assertThat(paymentRow.debitCredit()).isEqualTo(DebitCredit.C);

        String hdfc = HsbcCardVoucherRules.defaultHdfcLedger(List.of("Deutsche Bank", "HDFC Operating"));
        ReconTransaction tx = new ReconTransaction();
        tx.setDebitCredit(paymentRow.debitCredit());
        tx.setVoucherType(paymentRow.voucherType());
        tx.setMappedLedger(hdfc);
        tx.setStatementLedger("Card ledger");
        tx.setAmount(paymentRow.amount());
        TallyPrimeExcelExporter.Sides sides = TallyPrimeExcelExporter.sides(tx, "Card ledger");
        assertThat(sides.debit()).isEqualTo("Card ledger");
        assertThat(sides.credit()).isEqualTo("HDFC Operating");
    }

    @Test
    void negativeAmountIsACreditStoredAsAPositiveNumber() {
        Map<String, String> row = sample();
        row.put("Amount", "-25.50");
        row.put("Transaction Type", "Purchase");
        row.put("Bank Reference", "REF-NEG");

        HsbcCardExcelParser.ParsedRow parsed = only(parser.parse(workbook(List.of(row)), "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(parsed.debitCredit()).isEqualTo(DebitCredit.C);
        assertThat(parsed.amount()).isEqualByComparingTo("25.50");
        assertThat(parsed.voucherType()).isEqualTo(VoucherType.RECEIPT);
    }

    @Test
    void readsADayFirstTextDateAndARealExcelDate() throws Exception {
        Map<String, String> text = sample();
        text.put("Transaction Date", "1/10/2026");
        text.put("Bank Reference", "REF-TEXT");
        HsbcCardExcelParser.ParsedRow fromText = only(parser.parse(workbook(List.of(text)), "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(fromText.transactionDate()).isEqualTo(LocalDate.of(2026, 10, 1));

        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("d/m/yyyy"));
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            Row data = sheet.createRow(1);
            Map<String, String> values = sample();
            values.put("Bank Reference", "REF-EXCEL");
            for (int i = 0; i < HEADERS.length; i++) {
                if ("Transaction Date".equals(HEADERS[i])) {
                    Cell cell = data.createCell(i);
                    cell.setCellValue(LocalDate.of(2026, 10, 1));
                    cell.setCellStyle(dateStyle);
                } else {
                    data.createCell(i).setCellValue(values.getOrDefault(HEADERS[i], ""));
                }
            }
            wb.write(out);
            xlsx = out.toByteArray();
        }
        HsbcCardExcelParser.ParsedRow fromExcel = only(parser.parse(xlsx, "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(fromExcel.transactionDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void missingRequiredColumnNamesTheColumnAndUnknownColumnsAreIgnored() {
        assertThatThrownBy(() -> parser.parse(workbookWithout("Bank Reference"), "Purchase", "CREDIT CARD PAYMENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bank Reference");

        Map<String, String> row = sample();
        row.put("Mystery Column", "ignore me");
        HsbcCardExcelParser.ParsedFile parsed = parser.parse(
                workbook(List.of(row), true), "Purchase", "CREDIT CARD PAYMENT");
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().narration()).doesNotContain("ignore");
    }

    @Test
    void duplicateBankReferenceInTheFileIsCountedAndTheSecondRowIsDropped() {
        Map<String, String> first = sample();
        Map<String, String> second = sample();
        second.put("Amount", "9.00");
        HsbcCardExcelParser.ParsedFile parsed = parser.parse(
                workbook(List.of(first, second)), "Purchase", "CREDIT CARD PAYMENT");
        assertThat(parsed.inFileDuplicates()).isEqualTo(1);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().amount()).isEqualByComparingTo("100.50");
    }

    @Test
    void bankReferenceAlreadyImportedIsFlagged() {
        HsbcCardExcelParser.ParsedRow row = only(parser.parse(workbook(List.of(sample())), "Purchase", "CREDIT CARD PAYMENT"));
        List<HsbcCardExcelParser.ParsedRow> marked =
                HsbcCardExcelParser.markAlreadyImported(List.of(row), Set.of("REF-100"));
        assertThat(marked.getFirst().importFlag()).isEqualTo("Already imported");
    }

    @Test
    void unmappedCardWarningNamesTheLastFour() {
        assertThat(HsbcCardVoucherRules.unmappedCardWarnings(List.of("1111"), Set.of()))
                .containsExactly("No ledger is mapped for card 1111. Add it in Configuration → Account Mapping.");
        assertThat(HsbcCardVoucherRules.unmappedCardWarnings(List.of("1111"), Set.of("1111"))).isEmpty();
    }

    @Test
    void nonInrAmountIsFlaggedUnlessConsolidatedAmountIsPresent() {
        Map<String, String> usd = sample();
        usd.put("Currency", "USD");
        usd.put("Amount", "20.00");
        usd.put("Bank Reference", "REF-USD");
        HsbcCardExcelParser.ParsedRow flagged = only(parser.parse(workbook(List.of(usd)), "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(flagged.importFlag()).isEqualTo("Check currency");
        assertThat(flagged.amount()).isEqualByComparingTo("20.00");

        Map<String, String> consolidated = sample();
        consolidated.put("Currency", "USD");
        consolidated.put("Amount", "20.00");
        consolidated.put("Consolidated Amount", "1,640.00");
        consolidated.put("Bank Reference", "REF-CONS");
        HsbcCardExcelParser.ParsedRow used = only(
                parser.parse(workbook(List.of(consolidated), false, true), "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(used.importFlag()).isNull();
        assertThat(used.amount()).isEqualByComparingTo("1640.00");
    }

    @Test
    void promptCarriesMerchantCategoryAndNotTheCardholderName() {
        String prompt = MappingPrompt.build(List.of(), List.of(), List.of(
                new MappingPrompt.TransactionLine(
                        "1", "NORTHWIND CAFE PUNE", "100.50", "PAYMENT", List.of(), "EATING PLACES")));
        assertThat(prompt).contains("category=\"EATING PLACES\"");
        assertThat(prompt).doesNotContain("OFFICE");
    }

    @Test
    void thankYouPaymentIsCreditContraAgainstTheHdfcLedger() {
        Map<String, String> row = sample();
        row.put("Masked Card Number", "0000-00XX-XXXX-XXX4242");
        row.put("Name On Card", "NORTHWIND TECHNOLOG\u00A0");
        row.put("Merchant Name", "CREDIT CARD PAYMENT");
        row.put("Merchant City", "\u00A0  ");
        row.put("Merchant Category", "BANKING CHARGES AND ADJUSTMENTS");
        row.put("Amount", "-558323.29");
        row.put("Currency", "INR");
        row.put("Transaction Type", "PAYMENT - THANK YOU");
        row.put("Bank Reference", "\u00A0 REF-THANKYOU");
        row.put("First Name", "NIL");
        row.put("Last Name", " ");
        row.put("Email", "NIL");

        HsbcCardExcelParser.ParsedRow parsed = only(parser.parse(
                workbook(List.of(row), List.of("First Name", "Last Name", "Email")),
                null,
                null));

        assertThat(parsed.debitCredit()).isEqualTo(DebitCredit.C);
        assertThat(parsed.amount()).isEqualByComparingTo("558323.29");
        assertThat(parsed.cardLast4()).isEqualTo("4242");
        assertThat(parsed.voucherType()).isEqualTo(VoucherType.CONTRA);
        assertThat(parsed.cardPayment()).isTrue();
        assertThat(parsed.bankReference()).isEqualTo("REF-THANKYOU");
        assertThat(parsed.nameOnCard()).isEqualTo("NORTHWIND TECHNOLOG");
        assertThat(parsed.narration()).isEqualTo("CREDIT CARD PAYMENT");
        assertThat(HsbcCardVoucherRules.last4("0000-00XX-XXXX-1111")).isEqualTo("1111");
        assertThat(HsbcCardVoucherRules.direction(
                "PAYMENT - THANK YOU", new BigDecimal("10.00"), "Purchase")).isEqualTo(DebitCredit.C);

        String hdfc = HsbcCardVoucherRules.defaultHdfcLedger(List.of("ICICI Current", "HDFC Operating"));
        ReconTransaction tx = new ReconTransaction();
        tx.setDebitCredit(parsed.debitCredit());
        tx.setVoucherType(parsed.voucherType());
        tx.setCardPayment(parsed.cardPayment());
        tx.setMappedLedger(hdfc);
        tx.setStatementLedger("HSBC card ledger");
        tx.setAmount(parsed.amount());
        TallyPrimeExcelExporter.Sides sides = TallyPrimeExcelExporter.sides(tx, "HSBC card ledger");
        assertThat(sides.debit()).isEqualTo("HSBC card ledger");
        assertThat(sides.credit()).isEqualTo("HDFC Operating");
        assertThat(tx.isCardPayment() ? HsbcCardVoucherRules.DOUBLE_POST_WARNING : null)
                .isEqualTo("Also appears on the bank statement: post from one side only.");
    }

    @Test
    void leadingSpaceOnBankReferenceIsTrimmedBeforeTheDuplicateCheck() {
        Map<String, String> first = sample();
        first.put("Bank Reference", " REF-100");
        Map<String, String> second = sample();
        second.put("Bank Reference", "REF-100");
        second.put("Amount", "9.00");

        HsbcCardExcelParser.ParsedFile parsed = parser.parse(
                workbook(List.of(first, second)), "Purchase", HsbcCardVoucherRules.DEFAULT_PAYMENT_PATTERN);
        assertThat(parsed.inFileDuplicates()).isEqualTo(1);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().bankReference()).isEqualTo("REF-100");
        assertThat(parsed.rows().getFirst().amount()).isEqualByComparingTo("100.50");
    }

    @Test
    void commaAmountAndFallsBackToPostingDate() {
        Map<String, String> row = sample();
        row.put("Amount", "6,338.60");
        row.put("Transaction Date", "");
        row.put("Posting Date", "2/10/2026");
        row.put("Bank Reference", "REF-POST");
        HsbcCardExcelParser.ParsedRow parsed = only(parser.parse(workbook(List.of(row)), "Purchase", "CREDIT CARD PAYMENT"));
        assertThat(parsed.transactionDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("6338.60"));
    }

    private static HsbcCardExcelParser.ParsedRow only(HsbcCardExcelParser.ParsedFile file) {
        assertThat(file.rows()).hasSize(1);
        return file.rows().getFirst();
    }

    private static Map<String, String> sample() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Masked Card Number", "0000-00XX-XXXX-1111");
        row.put("Name On Card", "NORTHWIND OFFICE");
        row.put("Transaction Date", "1/10/2026");
        row.put("Posting Date", "2/10/2026");
        row.put("Merchant Name", "NORTHWIND CAFE");
        row.put("Merchant City", "PUNE");
        row.put("Net Amount", "100.50");
        row.put("Tax Amount", "0.00");
        row.put("Amount", "100.50");
        row.put("Currency", "INR");
        row.put("Foreign Amount", "");
        row.put("Foreign Currency", "");
        row.put("Statement Date", "24/10/2026");
        row.put("Merchant Category", "EATING PLACES");
        row.put("Merchant Country", "IND");
        row.put("Bank Reference", "REF-100");
        row.put("Transaction Type", "Purchase");
        row.put("Employee ID", "E-1");
        row.put("Cost Center", "CC-1");
        return row;
    }

    private static byte[] workbook(List<Map<String, String>> rows) {
        return workbook(rows, false, false);
    }

    private static byte[] workbook(List<Map<String, String>> rows, boolean extraColumn) {
        return workbook(rows, extraColumn, false);
    }

    private static byte[] workbook(List<Map<String, String>> rows, List<String> extraHeaders) {
        return workbook(rows, false, false, extraHeaders);
    }

    private static byte[] workbook(List<Map<String, String>> rows, boolean extraColumn, boolean consolidated) {
        return workbook(rows, extraColumn, consolidated, List.of());
    }

    private static byte[] workbook(
            List<Map<String, String>> rows, boolean extraColumn, boolean consolidated, List<String> extraHeaders) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            List<String> headers = new java.util.ArrayList<>(List.of(HEADERS));
            if (extraColumn) {
                headers.add("Mystery Column");
            }
            if (consolidated) {
                headers.add("Consolidated Amount");
            }
            headers.addAll(extraHeaders);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            int r = 1;
            for (Map<String, String> values : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < headers.size(); i++) {
                    row.createCell(i).setCellValue(values.getOrDefault(headers.get(i), ""));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] workbookWithout(String headerName) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            int col = 0;
            for (String name : HEADERS) {
                if (!name.equals(headerName)) {
                    header.createCell(col++).setCellValue(name);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
