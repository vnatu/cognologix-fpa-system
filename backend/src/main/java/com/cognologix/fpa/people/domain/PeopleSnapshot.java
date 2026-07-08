package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "people_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_version_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeopleSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_upload_id", nullable = false)
    private SnapshotUpload snapshotUpload;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_version_id", nullable = false)
    private PeriodVersion periodVersion;

    @Column(name = "employee_id", nullable = false, length = 100)
    private String employeeId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "practice_unit", nullable = false)
    private String practiceUnit;

    @Column(name = "business_unit", nullable = false)
    private String businessUnit;

    @Column(name = "bu_code", length = 100)
    private String buCode;

    @Column(name = "project_code", length = 100)
    private String projectCode;

    @Column(name = "billable_status", nullable = false, length = 1)
    private String billableStatus;

    @Column(name = "job_level", length = 100)
    private String jobLevel;

    @Column(name = "job_sub_level", length = 100)
    private String jobSubLevel;

    @Column(name = "title")
    private String title;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;
}
