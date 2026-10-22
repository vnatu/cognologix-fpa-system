package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MappingResponseParserTest {

    @Test
    void completeArrayParsesNumericIds() {
        MappingResponseParser.Parsed parsed = MappingResponseParser.parse("""
                [{"transactionId":1,"ledgerName":"Rent"},{"transactionId":"2","ledgerName":"Tax"}]
                """);
        assertThat(parsed.incomplete()).isFalse();
        assertThat(parsed.mappings()).extracting(row -> row.transactionId()).containsExactly("1", "2");
        assertThat(parsed.mappings()).extracting(row -> row.ledgerName()).containsExactly("Rent", "Tax");
    }

    @Test
    void truncatedArrayKeepsCompleteRows() {
        MappingResponseParser.Parsed parsed = MappingResponseParser.parse("""
                [{"transactionId":"1","ledgerName":"Rent"},{"transactionId":"2","ledgerName":"Ta
                """);
        assertThat(parsed.incomplete()).isTrue();
        assertThat(parsed.mappings()).hasSize(1);
        assertThat(parsed.mappings().getFirst().transactionId()).isEqualTo("1");
        assertThat(parsed.mappings().getFirst().ledgerName()).isEqualTo("Rent");
    }

    @Test
    void mappingsWrapperIsRead() {
        MappingResponseParser.Parsed parsed = MappingResponseParser.parse("""
                {"mappings":[{"transactionId":1,"ledgerName":"Rent"}]}
                """);
        assertThat(parsed.incomplete()).isFalse();
        assertThat(parsed.mappings()).containsExactly(new com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerMappingResult("1", "Rent"));
    }

    @Test
    void idAndLedgerFieldNamesAreAccepted() {
        MappingResponseParser.Parsed parsed = MappingResponseParser.parse("""
                [{"id":7,"ledger":"Office Rent"},{"transactionId":8,"ledgerName":"Rent"}]
                """);
        assertThat(parsed.mappings()).extracting(row -> row.transactionId()).containsExactly("7", "8");
        assertThat(parsed.mappings()).extracting(row -> row.ledgerName()).containsExactly("Office Rent", "Rent");
    }
}
