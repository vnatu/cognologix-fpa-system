package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.MasterRecord;
import com.cognologix.fpa.people.domain.ReconciliationStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record MasterRecordResponse(
        UUID id,
        UUID employeeRegistryId,
        UUID payrollSnapshotId,
        String employeeId,
        String fullName,
        String practiceUnit,
        String businessUnit,
        String billableStatus,
        String jobLevel,
        BigDecimal grossPay,
        boolean isDeliveryPu,
        boolean isBillable,
        boolean isBench,
        boolean isSupport,
        boolean isLeadership,
        boolean isManagement,
        ReconciliationStatus reconciliationStatus,
        String billingCustomerCode,
        String dataQualityFlags,
        boolean hasWarnings
) {
    public static MasterRecordResponse from(MasterRecord m) {
        String flags = m.getDataQualityFlags();
        boolean hasWarnings = flags != null && !flags.isBlank();
        return new MasterRecordResponse(
                m.getId(),
                m.getEmployeeRegistry().getId(),
                m.getPayrollSnapshot() != null ? m.getPayrollSnapshot().getId() : null,
                m.getEmployeeRegistry().getEmployeeId(),
                m.getEmployeeRegistry().getFullName(),
                m.getPracticeUnit(),
                m.getBusinessUnit(),
                m.getBillableStatus(),
                m.getJobLevel(),
                m.getGrossPay(),
                m.isDeliveryPu(),
                m.isBillable(),
                m.isBench(),
                m.isSupport(),
                m.isLeadership(),
                m.isManagement(),
                m.getReconciliationStatus(),
                m.getBillingCustomerCode(),
                flags,
                hasWarnings);
    }
}
