package com.cognologix.fpa.revenue.domain;

/**
 * Canonical system_attribute values for Zoho Books column mapping (ADR-019, ADR-039, ADR-040).
 */
public final class RevenueSystemAttribute {

    private RevenueSystemAttribute() {}

    // ZOHO_BOOKS_INVOICES
    public static final String INVOICE_NUMBER = "InvoiceNumber";
    public static final String CUSTOMER_CODE = "CustomerCode";
    public static final String CUSTOMER_NAME = "CustomerName";
    public static final String INVOICE_DATE = "InvoiceDate";
    public static final String STATUS = "Status";
    public static final String AMOUNT = "Amount";
    public static final String BALANCE = "Balance";
    public static final String DUE_DATE = "DueDate";
    public static final String CURRENCY = "Currency";
    public static final String PROJECT_CODE = "ProjectCode";

    // ZOHO_BOOKS_CREDIT_NOTES
    public static final String CREDIT_NOTE_NUMBER = "CreditNoteNumber";
    public static final String CREDIT_NOTE_DATE = "CreditNoteDate";
}
