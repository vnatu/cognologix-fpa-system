package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TallyAccountingNatureTest {

    @Test
    void infersNatureFromCommonParentPatterns() {
        assertThat(TallyAccountingNature.fromUnknownParent("Salary Payable")).isEqualTo("Liability");
        assertThat(TallyAccountingNature.fromUnknownParent("Trade Receivable")).isEqualTo("Asset");
        assertThat(TallyAccountingNature.fromUnknownParent("Sundry Debtors-Custom")).isEqualTo("Asset");
        assertThat(TallyAccountingNature.fromUnknownParent("Other Income Heads")).isEqualTo("Income");
        assertThat(TallyAccountingNature.fromUnknownParent("Project Revenue")).isEqualTo("Income");
        assertThat(TallyAccountingNature.fromUnknownParent("Staff Expense")).isEqualTo("Expense");
        assertThat(TallyAccountingNature.fromUnknownParent("Project Cost")).isEqualTo("Expense");
        assertThat(TallyAccountingNature.fromUnknownParent("Mystery Bucket")).isEqualTo("Liability");
        assertThat(TallyAccountingNature.fromUnknownParent(null)).isEqualTo("Liability");
    }

    @Test
    void resolveGroupNatureWalksParentChainToSeededGroup() {
        Map<String, String> parents = new HashMap<>();
        parents.put("Salary Payable", "Current Liabilities");
        parents.put("Contract Salaries", "Salary Payable");
        Map<String, String> natures = new HashMap<>();
        natures.put("Current Liabilities", "Liability");

        assertThat(BankReconService.resolveGroupNature("Contract Salaries", parents, natures))
                .isEqualTo("Liability");
        assertThat(BankReconService.resolveGroupNature("Salary Payable", parents, natures))
                .isEqualTo("Liability");
    }

    @Test
    void resolveGroupNatureUsesPatternWhenChainDoesNotReachSeed() {
        Map<String, String> parents = Map.of("Odd Receivables", "Primary");
        Map<String, String> natures = Map.of();

        assertThat(BankReconService.resolveGroupNature("Odd Receivables", parents, natures))
                .isEqualTo("Asset");
    }
}
