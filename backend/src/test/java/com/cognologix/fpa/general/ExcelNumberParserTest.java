package com.cognologix.fpa.general;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelNumberParserTest {

    @Test
    void parseAmount_handlesUsdDollarFormat() {
        assertThat(ExcelNumberParser.parseAmount("$20,640.00"))
                .isEqualByComparingTo("20640.00");
        assertThat(ExcelNumberParser.parseAmount("$314,750.00"))
                .isEqualByComparingTo("314750.00");
        assertThat(ExcelNumberParser.parseAmount("$")).isNull();
    }

    @Test
    void parseAmount_handlesIndianRupeeFormat() {
        assertThat(ExcelNumberParser.parseAmount("₹1,14,47,529.60"))
                .isEqualByComparingTo("11447529.60");
    }

    @Test
    void parseAmount_handlesPlainGroupedAndBlank() {
        assertThat(ExcelNumberParser.parseAmount("1,234.50")).isEqualByComparingTo("1234.50");
        assertThat(ExcelNumberParser.parseAmount("  5000  ")).isEqualByComparingTo("5000");
        assertThat(ExcelNumberParser.parseAmount(null)).isNull();
        assertThat(ExcelNumberParser.parseAmount("")).isNull();
        assertThat(ExcelNumberParser.parseAmount("   ")).isNull();
        assertThat(ExcelNumberParser.parseAmount("₹")).isNull();
    }

    @Test
    void parseAmount_rejectsGarbage() {
        assertThatThrownBy(() -> ExcelNumberParser.parseAmount("₹abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot parse amount value");
    }

    @Test
    void toRsLakhs_dividesByOneLakh() {
        assertThat(ExcelNumberParser.toRsLakhs(new BigDecimal("11447529.60")))
                .isEqualByComparingTo("114.475");
        assertThat(ExcelNumberParser.toRsLakhs(new BigDecimal("100000")))
                .isEqualByComparingTo("1.000");
        assertThat(ExcelNumberParser.toRsLakhs(new BigDecimal("50000")))
                .isEqualByComparingTo("0.500");
        assertThat(ExcelNumberParser.toRsLakhs(null)).isNull();
    }

    @Test
    void parseInteger_stripsCurrencyAndCommas() {
        assertThat(ExcelNumberParser.parseInteger("₹1,250")).isEqualTo(1250);
        assertThat(ExcelNumberParser.parseInteger("50")).isEqualTo(50);
        assertThat(ExcelNumberParser.parseInteger("12.0")).isEqualTo(12);
        assertThat(ExcelNumberParser.parseInteger(null)).isNull();
        assertThat(ExcelNumberParser.parseInteger("")).isNull();
    }

    @Test
    void parseInteger_rejectsGarbage() {
        assertThatThrownBy(() -> ExcelNumberParser.parseInteger("₹x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot parse integer value");
    }
}
