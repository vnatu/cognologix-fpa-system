package com.cognologix.fpa.revenue.domain;

/**
 * Where an invoice's effective period came from.
 * SERVICE — both service month and service year were present and valid.
 * TAG — the upload's period tag was copied onto the row.
 */
public enum MonthSource {
    SERVICE,
    TAG
}
