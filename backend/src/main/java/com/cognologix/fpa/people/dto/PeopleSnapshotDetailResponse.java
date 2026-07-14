package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.PeopleSnapshot;

import java.time.LocalDate;
import java.util.UUID;

public record PeopleSnapshotDetailResponse(
        UUID id,
        String employeeId,
        String fullName,
        String practiceUnit,
        String businessUnit,
        String buCode,
        String projectCode,
        String billableStatus,
        String jobLevel,
        String jobSubLevel,
        String title,
        LocalDate dateOfJoining
) {
    public static PeopleSnapshotDetailResponse from(PeopleSnapshot s) {
        return new PeopleSnapshotDetailResponse(
                s.getId(),
                s.getEmployeeId(),
                s.getFullName(),
                s.getPracticeUnit(),
                s.getBusinessUnit(),
                s.getBuCode(),
                s.getProjectCode(),
                s.getBillableStatus(),
                s.getJobLevel(),
                s.getJobSubLevel(),
                s.getTitle(),
                s.getDateOfJoining());
    }
}
