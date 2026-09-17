package com.cognologix.fpa.bankrecon;

import java.util.Set;

/**
 * HDFC_BANK_STATEMENT system attributes (ADR-019). Header-block fields are
 * mapped from the statement preamble; transaction columns from the grid header.
 */
public final class BankStatementSystemAttribute {

    public static final String ACCOUNT_NUMBER = "AccountNumber";
    public static final String CUSTOMER_NAME = "CustomerName";
    public static final String FROM_DATE = "FromDate";
    public static final String TO_DATE = "ToDate";
    public static final String OPENING_BALANCE = "OpeningBalance";
    public static final String CLOSING_BALANCE = "ClosingBalance";

    public static final String TRANSACTION_DATE = "TransactionDate";
    public static final String TRANSACTION_DESCRIPTION = "TransactionDescription";
    public static final String TRANSACTION_AMOUNT = "TransactionAmount";
    public static final String DEBIT_CREDIT = "DebitCredit";
    public static final String REFERENCE_NO = "ReferenceNo";
    public static final String VALUE_DATE = "ValueDate";
    public static final String TRANSACTION_BRANCH = "TransactionBranch";
    public static final String RUNNING_BALANCE = "RunningBalance";

    public static final Set<String> HEADER_ATTRIBUTES = Set.of(
            ACCOUNT_NUMBER, CUSTOMER_NAME, FROM_DATE, TO_DATE, OPENING_BALANCE, CLOSING_BALANCE);

    public static final Set<String> TRANSACTION_ATTRIBUTES = Set.of(
            TRANSACTION_DATE, TRANSACTION_DESCRIPTION, TRANSACTION_AMOUNT, DEBIT_CREDIT,
            REFERENCE_NO, VALUE_DATE, TRANSACTION_BRANCH, RUNNING_BALANCE);

    public static final Set<String> REQUIRED_HEADER = Set.of(ACCOUNT_NUMBER, FROM_DATE, TO_DATE);

    public static final Set<String> REQUIRED_TRANSACTION = Set.of(
            TRANSACTION_DATE, TRANSACTION_DESCRIPTION, TRANSACTION_AMOUNT, DEBIT_CREDIT);

    public static final String[] DEFAULT_HEADER_LABELS = {
            "Account Number", "Customer Name", "From Date", "To Date",
            "Opening Balance", "Closing Balance"
    };

    public static final String[] DEFAULT_TRANSACTION_HEADERS = {
            "Transaction Date", "Transaction Description", "Transaction Amount", "Debit/Credit",
            "Reference No", "Value Date", "Transaction Branch", "Running Balance"
    };

    private BankStatementSystemAttribute() {}
}
