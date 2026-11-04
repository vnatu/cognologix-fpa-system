package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.TallyLedger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCodeImportTest {

    private static final String DOUBLE = "Depreciation on  Car";
    private static final String TRIPLE = "Depreciation on   Car";
    private static final String SINGLE = "Depreciation on Car";

    @Test
    void oneSpaceInTheSheetUpdatesTheStoredDoubleSpaceName() {
        TallyLedger stored = ledger(DOUBLE, "OLD1");
        LedgerCodeImport.Planned plan = LedgerCodeImport.plan(
                List.of(row(2, SINGLE, "DEP1")),
                List.of(stored),
                Set.of("Indirect Expenses"),
                Map.of());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.toUpdate()).singleElement().satisfies(line -> {
            assertThat(line.ledgerName()).isEqualTo(DOUBLE);
            assertThat(line.ledgerCode()).isEqualTo("DEP1");
        });
        assertThat(stored.getLedgerName()).isEqualTo(DOUBLE);
        assertThat(stored.getLedgerCode()).isEqualTo("OLD1");
    }

    @Test
    void doubleSpaceInTheSheetIsAnExactMatch() {
        TallyLedger stored = ledger(DOUBLE, null);
        LedgerCodeImport.Planned plan = LedgerCodeImport.plan(
                List.of(row(2, DOUBLE, "DEP2")),
                List.of(stored),
                Set.of("Indirect Expenses"),
                Map.of());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.toUpdate()).singleElement().extracting(LedgerCodeImport.Line::ledgerName).isEqualTo(DOUBLE);
        assertThat(stored.getLedgerName()).isEqualTo(DOUBLE);
    }

    @Test
    void twoLedgersThatDifferOnlyBySpacingAreAmbiguous() {
        TallyLedger two = ledger(DOUBLE, null);
        TallyLedger three = ledger(TRIPLE, null);
        LedgerCodeImport.Planned plan = LedgerCodeImport.plan(
                List.of(row(2, SINGLE, "DEP3")),
                List.of(two, three),
                Set.of("Indirect Expenses"),
                Map.of());

        assertThat(plan.toUpdate()).isEmpty();
        assertThat(plan.errors()).extracting(LedgerCodeImport.Issue::message)
                .containsExactly("ambiguous: matches 2 ledgers: \"" + DOUBLE + "\", \"" + TRIPLE + "\"");
        assertThat(two.getLedgerName()).isEqualTo(DOUBLE);
        assertThat(three.getLedgerName()).isEqualTo(TRIPLE);
    }

    @Test
    void caseDifferenceStaysAPossibleMatchAndQuotesTheStoredName() {
        LedgerCodeImport.Planned plan = LedgerCodeImport.plan(
                List.of(row(2, "depreciation on car", "DEP4")),
                List.of(ledger(SINGLE, null)),
                Set.of("Indirect Expenses"),
                Map.of());

        assertThat(plan.toUpdate()).isEmpty();
        assertThat(plan.errors()).extracting(LedgerCodeImport.Issue::message)
                .contains("possible match: \"" + SINGLE + "\"");
    }

    @Test
    void workbookKeepsInternalSpacesAndTheExportWritesTheStoredName() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ledger Name");
            header.createCell(1).setCellValue("Ledger Code");
            var data = sheet.createRow(1);
            data.createCell(0).setCellValue("  " + DOUBLE + "\u00A0");
            data.createCell(1).setCellValue("DEP5");
            wb.write(out);
            xlsx = out.toByteArray();
        }
        List<LedgerCodeImport.ParsedRow> rows = LedgerCodeImport.read(new ByteArrayInputStream(xlsx));
        assertThat(rows).singleElement().extracting(LedgerCodeImport.ParsedRow::ledgerName).isEqualTo(DOUBLE);

        TallyLedger stored = ledger(DOUBLE, null);
        byte[] exported = LedgerMasterExcel.write(List.of(stored), Map.of());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(DOUBLE);
        }
        LedgerNameResolver.Resolution resolved = LedgerNameResolver.resolve(SINGLE, List.of(DOUBLE));
        assertThat(resolved.catalogName()).isEqualTo(DOUBLE);
    }

    private static LedgerCodeImport.ParsedRow row(int number, String name, String code) {
        return new LedgerCodeImport.ParsedRow(number, name, code, "", "", "", "", "");
    }

    private static TallyLedger ledger(String name, String code) {
        return TallyLedger.builder()
                .id(UUID.randomUUID())
                .ledgerName(name)
                .groupName("Indirect Expenses")
                .accountingNature("Expense")
                .ledgerCode(code)
                .build();
    }
}
