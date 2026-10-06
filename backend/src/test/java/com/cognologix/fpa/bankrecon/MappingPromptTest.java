package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MappingPromptTest {

    @Test
    void catalogExcludesBankAndCardGroupsAndMappedLedgers() {
        Set<String> mapped = Set.of("Mapped Side Ledger");
        assertThat(MappingPrompt.excludedFromCatalog("Bank Accounts", "HDFC Bank", mapped)).isTrue();
        assertThat(MappingPrompt.excludedFromCatalog("Credit Cards", "Amex Corporate", mapped)).isTrue();
        assertThat(MappingPrompt.excludedFromCatalog("Bank OD", "OD Line", mapped)).isTrue();
        assertThat(MappingPrompt.excludedFromCatalog("Bank OD Accounts", "Cash Credit", mapped)).isTrue();
        assertThat(MappingPrompt.excludedFromCatalog("Indirect Expenses", "Mapped Side Ledger", mapped)).isTrue();
        assertThat(MappingPrompt.excludedFromCatalog("Indirect Expenses", "Rent", mapped)).isFalse();
        assertThat(MappingPrompt.excludedFromCatalog("bank accounts", "HDFC Bank", Set.of())).isTrue();
        assertThat(MappingPrompt.excludedFromCatalog("CREDIT CARDS", "Amex Corporate", Set.of())).isTrue();
    }

    @Test
    void bankOdAcGroupIsExcludedFromTheCatalog() {
        String catalog = catalogOf(
                new MappingPrompt.LedgerLine("Bank OD A/c", "HDFC Cash Credit", null, null, null, null),
                Set.of());
        assertThat(catalog).doesNotContain("Bank OD A/c");
        assertThat(catalog).doesNotContain("HDFC Cash Credit");
    }

    @Test
    void creditCardEmiStaysInTheCatalogUnlessTheLedgerIsMapped() {
        MappingPrompt.LedgerLine emi = new MappingPrompt.LedgerLine(
                "Credit Card EMI", "Card EMI Payable", null, null, null, null);
        String catalog = catalogOf(emi, Set.of());
        assertThat(catalog).contains("Credit Card EMI");
        assertThat(catalog).contains("Card EMI Payable");

        String mapped = catalogOf(emi, Set.of("Card EMI Payable"));
        assertThat(mapped).doesNotContain("Card EMI Payable");
    }

    @Test
    void catalogIsIdenticalWhenOnlyTransactionsChange() {
        List<MappingPrompt.LedgerLine> payment = List.of(
                new MappingPrompt.LedgerLine("Indirect Expenses", "Rent", "rent, office", "Office costs", null, null));
        List<MappingPrompt.LedgerLine> receipt = List.of(
                new MappingPrompt.LedgerLine("Indirect Income", "Client Receipts", null, null, null, null));
        String first = MappingPrompt.build(List.of(), payment, receipt, List.of(tx("tx-1", "ALPHA")));
        String second = MappingPrompt.build(List.of(), payment, receipt, List.of(tx("tx-2", "BETA")));
        assertThat(MappingPrompt.staticPrefix(first)).isEqualTo(MappingPrompt.staticPrefix(second));
        assertThat(first).isNotEqualTo(second);
        assertThat(MappingPrompt.catalogSection(first)).contains("Rent | rent, office | Office costs");
        assertThat(MappingPrompt.catalogSection(first)).contains("Client Receipts");
        assertThat(MappingPrompt.catalogSection(first)).doesNotContain("Client Receipts |");
    }

    @Test
    void completionBudgetIsFortyTokensPerRowPlusMargin() {
        assertThat(MappingPrompt.completionTokenBudget(1)).isEqualTo(540);
        assertThat(MappingPrompt.completionTokenBudget(10)).isEqualTo(900);
        assertThat(MappingPrompt.completionTokenBudget(0)).isEqualTo(500);
    }

    @Test
    void examplesIncludeAmountsAndEmptyExamplesStayEmpty() {
        String prompt = MappingPrompt.build(
                List.of(new MappingPrompt.HintLine("PAYMENT", "Prefer the vendor ledger")),
                List.of(new MappingPrompt.LedgerLine("Indirect Expenses", "Rent", null, null, null, null)),
                List.of(),
                List.of(
                        new MappingPrompt.TransactionLine(
                                "tx-1",
                                "OFFICE RENT",
                                "40000.00",
                                "PAYMENT",
                                List.of(new MappingPrompt.ExampleLine("OFFICE RENT AUG", "4321.50", "Rent"))),
                        new MappingPrompt.TransactionLine("tx-2", "UNKNOWN", "10.00", "PAYMENT", List.of())));
        assertThat(prompt).contains("amount=4321.50");
        assertThat(prompt).contains("ledger=Rent");
        assertThat(prompt).contains("examples: []");
        assertThat(prompt.indexOf("LEDGER CATALOG")).isLessThan(prompt.indexOf("TRANSACTIONS"));
        assertThat(prompt).contains("Use UNMAPPED when unsure.");
    }

    @Test
    void ledgerWithNoHintIsJustItsName() {
        assertThat(MappingPrompt.formatLedger(
                new MappingPrompt.LedgerLine("Indirect Expenses", "Internet Charges", " ", null, "", null)))
                .isEqualTo("Internet Charges");
    }

    private static MappingPrompt.TransactionLine tx(String id, String narration) {
        return new MappingPrompt.TransactionLine(id, narration, "1.00", "PAYMENT", List.of());
    }

    private static String catalogOf(MappingPrompt.LedgerLine line, Set<String> mappedLedgerNames) {
        List<MappingPrompt.LedgerLine> kept = MappingPrompt.excludedFromCatalog(
                line.groupName(), line.ledgerName(), mappedLedgerNames)
                ? List.of()
                : List.of(line);
        return MappingPrompt.catalogSection(MappingPrompt.build(List.of(), kept, List.of(), List.of()));
    }
}
