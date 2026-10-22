package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerNameResolverTest {

    @Test
    void exactNameWins() {
        LedgerNameResolver.Resolution resolution = LedgerNameResolver.resolve(
                "Sky Enterprises", List.of("Sky Enterprises", "Sky"));
        assertThat(resolution.kind()).isEqualTo(LedgerNameResolver.Kind.EXACT);
        assertThat(resolution.catalogName()).isEqualTo("Sky Enterprises");
    }

    @Test
    void casePunctuationAndSpacesResolveToTheCatalogSpelling() {
        LedgerNameResolver.Resolution resolution = LedgerNameResolver.resolve(
                "sky   enterprises.", List.of("Sky Enterprises"));
        assertThat(resolution.kind()).isEqualTo(LedgerNameResolver.Kind.RESOLVED);
        assertThat(resolution.catalogName()).isEqualTo("Sky Enterprises");
    }

    @Test
    void limitedAndPrivateFoldToTheCatalogSpelling() {
        LedgerNameResolver.Resolution resolution = LedgerNameResolver.resolve(
                "Acme Pvt Ltd", List.of("Acme Private Limited"));
        assertThat(resolution.kind()).isEqualTo(LedgerNameResolver.Kind.RESOLVED);
        assertThat(resolution.catalogName()).isEqualTo("Acme Private Limited");
    }

    @Test
    void aSingleCloseCandidateIsAccepted() {
        LedgerNameResolver.Resolution resolution = LedgerNameResolver.resolve(
                "Sky Enterprises 2", List.of("Sky Enterprises", "Office Rent"));
        assertThat(resolution.kind()).isEqualTo(LedgerNameResolver.Kind.RESOLVED);
        assertThat(resolution.catalogName()).isEqualTo("Sky Enterprises");
    }

    @Test
    void severalLedgersThatNormaliseTheSameStayAmbiguous() {
        LedgerNameResolver.Resolution resolution = LedgerNameResolver.resolve(
                "acme pvt ltd", List.of("Acme Pvt Ltd", "Acme Private Limited", "Office Rent"));
        assertThat(resolution.kind()).isEqualTo(LedgerNameResolver.Kind.AMBIGUOUS);
        assertThat(resolution.catalogName()).isNull();
    }
}
