package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCodeScanTest {

    @Test
    void ignoresAnAllDigitCodeInTheBankAppendedTail() {
        assertThat(LedgerCodeScan.words("NEFT VENDOR 123456789012 445566"))
                .extracting(LedgerCodeScan.Word::text)
                .containsExactly("NEFT", "VENDOR");
        assertThat(LedgerCodeScan.words("NEFT 445566 ACME 123456789012"))
                .containsExactly(
                        new LedgerCodeScan.Word("NEFT", 1),
                        new LedgerCodeScan.Word("445566", 2),
                        new LedgerCodeScan.Word("ACME", 3));
        assertThat(LedgerCodeScan.words("UPI/SKY9/VENDOR"))
                .containsExactly(
                        new LedgerCodeScan.Word("UPI", 1),
                        new LedgerCodeScan.Word("SKY9", 2),
                        new LedgerCodeScan.Word("VENDOR", 3));
    }
}
