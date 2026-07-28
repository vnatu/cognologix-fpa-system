package com.cognologix.fpa.general;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelParserUtilsTest {

    @Test
    void normalizeHeader_unifiesCaseSpacesHyphensAndUnderscores() {
        assertThat(ExcelParserUtils.normalizeHeader("Customer Code"))
                .isEqualTo("customer_code");
        assertThat(ExcelParserUtils.normalizeHeader("customer_code"))
                .isEqualTo("customer_code");
        assertThat(ExcelParserUtils.normalizeHeader("Customer-Code"))
                .isEqualTo("customer_code");
        assertThat(ExcelParserUtils.normalizeHeader("  Customer   Code  "))
                .isEqualTo("customer_code");
        assertThat(ExcelParserUtils.normalizeHeader("Invoice#"))
                .isEqualTo("invoice");
        assertThat(ExcelParserUtils.normalizeHeader("Invoice Number"))
                .isEqualTo("invoice_number");
    }

    @Test
    void normalizeHeader_nullAndBlank() {
        assertThat(ExcelParserUtils.normalizeHeader(null)).isEmpty();
        assertThat(ExcelParserUtils.normalizeHeader("")).isEmpty();
        assertThat(ExcelParserUtils.normalizeHeader("   ")).isEmpty();
    }
}
