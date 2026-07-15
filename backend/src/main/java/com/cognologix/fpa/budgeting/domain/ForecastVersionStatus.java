package com.cognologix.fpa.budgeting.domain;

/**
 * Forecast version lifecycle (ADR-037).
 * Only one ACTIVE and at most one DRAFT per forecast type at a time.
 * SUPERSEDED versions are retained permanently — never deleted.
 */
public enum ForecastVersionStatus {
    DRAFT,
    ACTIVE,
    SUPERSEDED
}
