package com.cognologix.fpa.people;

import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.people.domain.*;
import com.cognologix.fpa.people.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API surface for the People &amp; Payroll module.
 * Controllers and other modules call this class only — never sub-packages directly (ADR-008).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeoplePayrollService {

    private final PeriodRepository periodRepository;
    private final PeriodVersionRepository periodVersionRepository;
    private final SnapshotUploadRepository snapshotUploadRepository;
    private final ImportColumnMappingRepository importColumnMappingRepository;
    private final EmployeeRegistryRepository employeeRegistryRepository;
    private final AlternateIdLinkRepository alternateIdLinkRepository;
    private final PeopleSnapshotRepository peopleSnapshotRepository;
    private final PayrollSnapshotRepository payrollSnapshotRepository;
    private final MasterRecordRepository masterRecordRepository;
    private final ClassificationConfigRepository classificationConfigRepository;
    private final CustomerService customerService;
    private final ApplicationEventPublisher eventPublisher;

    // ── Period lifecycle ─────────────────────────────────────────────────────

    /**
     * Creates a reporting period and its initial version (version_number=1, status=OPEN).
     * Full lifecycle transitions beyond the first version remain deferred.
     */
    @Transactional
    public Period createPeriod(int periodMonth, int periodYear) {
        if (periodMonth < 1 || periodMonth > 12) {
            throw new IllegalArgumentException("periodMonth must be between 1 and 12");
        }
        if (periodRepository.findByPeriodMonthAndPeriodYear(periodMonth, periodYear).isPresent()) {
            throw new IllegalArgumentException(
                    "Period already exists: " + periodMonth + "/" + periodYear);
        }
        var period = periodRepository.save(Period.builder()
                .periodMonth(periodMonth)
                .periodYear(periodYear)
                .build());
        periodVersionRepository.save(PeriodVersion.builder()
                .period(period)
                .versionNumber(1)
                .status(PeriodStatus.OPEN)
                .latestFinalised(false)
                .build());
        return period;
    }

    @Transactional
    public PeriodVersion createPeriodVersion(UUID periodId, String createdBy) {
        throw new UnsupportedOperationException("createPeriodVersion — not yet implemented");
    }

    @Transactional
    public SnapshotUpload uploadSnapshot(UUID periodVersionId,
                                         ImportType importType,
                                         String uploadedBy,
                                         String originalFilename,
                                         int rowCount) {
        throw new UnsupportedOperationException("uploadSnapshot — not yet implemented");
    }

    /**
     * Builds master records for a period version using classification rules from
     * {@link ClassificationConfigRepository#findByConfigType}.
     */
    @Transactional
    public List<MasterRecord> buildMasterRecords(UUID periodVersionId) {
        throw new UnsupportedOperationException("buildMasterRecords — not yet implemented");
    }

    /**
     * Finalises a period version and publishes {@link PeriodFinalisedEvent} for downstream
     * consumers (Budgeting &amp; Forecasting). Aggregation logic deferred — event carries
     * placeholder summary until build/finalise is fully implemented.
     */
    @Transactional
    public void finalisePeriod(UUID periodVersionId) {
        periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Period version not found: " + periodVersionId));
        // TODO: set status FINALISED, is_latest_finalised, compute summary from master records
        eventPublisher.publishEvent(PeriodFinalisedEvent.empty(periodVersionId));
    }

    // ── Employee Registry ──────────────────────────────────────────────────────

    public Optional<EmployeeRegistry> findEmployeeByEmployeeId(String employeeId) {
        return employeeRegistryRepository.findByEmployeeId(employeeId);
    }

    @Transactional
    public EmployeeRegistry registerEmployee(String employeeId, String fullName) {
        if (employeeRegistryRepository.existsByEmployeeId(employeeId)) {
            throw new IllegalArgumentException("Employee already registered: " + employeeId);
        }
        return employeeRegistryRepository.save(EmployeeRegistry.builder()
                .employeeId(employeeId)
                .fullName(fullName)
                .exitStatus(ExitStatus.ACTIVE)
                .build());
    }

    // ── Classification config ──────────────────────────────────────────────────

    public List<ClassificationConfig> findClassificationByType(ClassificationConfigType configType) {
        return classificationConfigRepository.findByConfigType(configType);
    }

    public List<ClassificationConfig> findAllClassificationConfig() {
        return classificationConfigRepository.findAll();
    }

    // ── Cross-module validation (Module 1 §9.4 → Customer Management per ADR-010) ─

    /**
     * Validates a BU code / business unit value against Customer Management's master.
     * Application-layer call only — no FK or repository cross-reference (ADR-008).
     */
    public boolean isKnownCustomer(String buCode) {
        return customerService.isKnownCustomer(buCode);
    }
}
