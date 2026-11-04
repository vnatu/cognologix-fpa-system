package com.cognologix.fpa.revenue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePeriodParserTest {

    @Test
    void parseMonth_acceptsNumberShortNameAndFullName() {
        assertThat(ServicePeriodParser.parseMonth("8").month()).isEqualTo(8);
        assertThat(ServicePeriodParser.parseMonth("08").month()).isEqualTo(8);
        assertThat(ServicePeriodParser.parseMonth("8.0").month()).isEqualTo(8);
        assertThat(ServicePeriodParser.parseMonth("Aug").month()).isEqualTo(8);
        assertThat(ServicePeriodParser.parseMonth("aug.").month()).isEqualTo(8);
        assertThat(ServicePeriodParser.parseMonth("August").month()).isEqualTo(8);
        assertThat(ServicePeriodParser.parseMonth("AUGUST").month()).isEqualTo(8);
    }

    @Test
    void parseMonth_blankAndJunkAreNotErrors() {
        assertThat(ServicePeriodParser.parseMonth(null).blank()).isTrue();
        assertThat(ServicePeriodParser.parseMonth("  ").blank()).isTrue();
        assertThat(ServicePeriodParser.parseMonth("").month()).isNull();

        var junk = ServicePeriodParser.parseMonth("nope");
        assertThat(junk.unreadable()).isTrue();
        assertThat(junk.month()).isNull();
        assertThat(ServicePeriodParser.parseMonth("13").unreadable()).isTrue();
        assertThat(ServicePeriodParser.parseMonth("0").unreadable()).isTrue();
        assertThat(ServicePeriodParser.parseMonth("8.5").unreadable()).isTrue();
    }

    @Test
    void parseYear_acceptsAYearAndRejectsBlankOrJunk() {
        assertThat(ServicePeriodParser.parseYear("2026").year()).isEqualTo(2026);
        assertThat(ServicePeriodParser.parseYear("2026.0").year()).isEqualTo(2026);
        assertThat(ServicePeriodParser.parseYear(null).blank()).isTrue();
        assertThat(ServicePeriodParser.parseYear("").blank()).isTrue();
        assertThat(ServicePeriodParser.parseYear("26").unreadable()).isTrue();
        assertThat(ServicePeriodParser.parseYear("FY26").unreadable()).isTrue();
        assertThat(ServicePeriodParser.parseYear("1999").unreadable()).isTrue();
    }
}
