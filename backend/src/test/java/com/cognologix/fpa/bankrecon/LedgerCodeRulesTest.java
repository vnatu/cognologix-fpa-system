package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCodeRulesTest {

    @Test
    void acceptsLettersDigitsAndMixedCodesAndRejectsTheReservedList() {
        assertThat(LedgerCodeRules.canonical(" sky9 ")).isEqualTo("SKY9");
        assertThat(LedgerCodeRules.canonical("  ")).isNull();
        assertThat(LedgerCodeRules.rejection("AB")).isNull();
        assertThat(LedgerCodeRules.rejection("445566")).isNull();
        assertThat(LedgerCodeRules.rejection("SKY2AB")).isNull();
        assertThat(LedgerCodeRules.rejection("A")).isEqualTo("Code must be 2 to 10 letters and digits");
        assertThat(LedgerCodeRules.rejection("SKY-1")).isEqualTo("Code must be 2 to 10 letters and digits");
        assertThat(LedgerCodeRules.rejection("TOOLONGCODE")).isEqualTo("Code must be 2 to 10 letters and digits");
        assertThat(LedgerCodeRules.RESERVED).containsExactlyInAnyOrder(
                "FT", "NEFT", "RTGS", "IMPS", "UPI", "ATM", "ACH", "NACH", "CBDT", "TDS", "GST", "SALARY", "REFUND");
        for (String reserved : LedgerCodeRules.RESERVED) {
            assertThat(LedgerCodeRules.rejection(reserved)).isEqualTo("Code " + reserved + " is reserved");
        }
    }
}
