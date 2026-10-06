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
}
