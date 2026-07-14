package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.people.domain.PayrollSnapshot;
import com.cognologix.fpa.people.domain.PeopleSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ImportPreviewResponse(
        List<PeopleSnapshotPreview> peopleRows,
        List<PayrollSnapshotPreview> payrollRows,
        List<String> unmappedColumns,
        List<String> missingColumns,
        List<String> unrecognizedBuCodes
) {
    public static ImportPreviewResponse from(PeoplePayrollService.ImportPreview preview) {
        return new ImportPreviewResponse(
                preview.peoplePreview().stream().map(PeopleSnapshotPreview::from).toList(),
                preview.payrollPreview().stream().map(PayrollSnapshotPreview::from).toList(),
                preview.unmappedColumns(),
                preview.missingColumns(),
                preview.unrecognizedBuCodes());
    }

    public record PeopleSnapshotPreview(
            UUID id,
            String employeeId,
            String fullName,
            String practiceUnit,
            String businessUnit,
            String buCode,
            String billableStatus,
            String jobLevel,
            LocalDate dateOfJoining
    ) {
        static PeopleSnapshotPreview from(PeopleSnapshot s) {
            return new PeopleSnapshotPreview(
                    s.getId(), s.getEmployeeId(), s.getFullName(), s.getPracticeUnit(),
                    s.getBusinessUnit(), s.getBuCode(), s.getBillableStatus(),
                    s.getJobLevel(), s.getDateOfJoining());
        }
    }

    public record PayrollSnapshotPreview(
            UUID id,
            String employeeNo,
            String fullName,
            BigDecimal grossPay,
            BigDecimal netPay
    ) {
        static PayrollSnapshotPreview from(PayrollSnapshot s) {
            return new PayrollSnapshotPreview(
                    s.getId(), s.getEmployeeNo(), s.getFullName(), s.getGrossPay(), s.getNetPay());
        }
    }
}
