package com.cognologix.fpa.people.domain;

/**
 * Canonical system_attribute values stored on {@link ImportColumnMappingLine}.
 * Finance maps Excel headers to these names (ADR-019).
 */
public final class SystemAttribute {

    private SystemAttribute() {}

    public static final String EMPLOYEE_ID = "EmployeeID";
    public static final String EMPLOYEE_NO = "EmployeeNo";
    public static final String FULL_NAME = "FullName";
    public static final String PRACTICE_UNIT = "PracticeUnit";
    public static final String BUSINESS_UNIT = "BusinessUnit";
    public static final String BU_CODE = "BUCode";
    public static final String PROJECT_CODE = "ProjectCode";
    public static final String BILLABLE_STATUS = "BillableStatus";
    public static final String JOB_LEVEL = "JobLevel";
    public static final String JOB_SUB_LEVEL = "JobSubLevel";
    public static final String TITLE = "Title";
    public static final String DATE_OF_JOINING = "DateOfJoining";
    public static final String GROSS_PAY = "GrossPay";
    public static final String NET_PAY = "NetPay";
    public static final String CTC_PER_ANNUM = "CtcPerAnnum";
    public static final String LAST_WORKING_DAY = "LastWorkingDay";
}
