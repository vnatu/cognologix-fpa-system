package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.PayrollSnapshot;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollSnapshotDetailResponse(
        UUID id,
        ImportType importType,
        String employeeNo,
        String fullName,
        BigDecimal grossPay,
        BigDecimal netPay,
        BigDecimal ctcPerAnnum
) {
    public static PayrollSnapshotDetailResponse from(PayrollSnapshot s) {
        return new PayrollSnapshotDetailResponse(
                s.getId(),
                s.getImportType(),
                s.getEmployeeNo(),
                s.getFullName(),
                s.getGrossPay(),
                s.getNetPay(),
                s.getCtcPerAnnum());
    }
}
