package com.cognologix.fpa.people;

import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.people.domain.*;
import com.cognologix.fpa.people.dto.*;
import com.cognologix.fpa.people.repository.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ExcelSnapshotParser excelSnapshotParser;

    // ── Period lifecycle ─────────────────────────────────────────────────────

    public List<Period> findAllPeriods() {
        return periodRepository.findAll();
    }

    public List<PeriodVersion> findVersionsForPeriod(UUID periodId) {
        return periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(periodId);
    }

    public PeriodVersion getPeriodVersion(UUID periodId, UUID versionId) {
        PeriodVersion version = periodVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + versionId));
        Hibernate.initialize(version.getPeriod());
        if (!version.getPeriod().getId().equals(periodId)) {
            throw new NotFoundException("Period version " + versionId + " does not belong to period " + periodId);
        }
        return version;
    }

    public List<SnapshotUpload> findUploadsForVersion(UUID periodVersionId) {
        return snapshotUploadRepository.findByPeriodVersionId(periodVersionId);
    }

    public Map<ReconciliationStatus, Long> countMasterByReconciliationStatus(UUID periodVersionId) {
        return masterRecordRepository.findByPeriodVersionId(periodVersionId).stream()
                .collect(Collectors.groupingBy(MasterRecord::getReconciliationStatus, Collectors.counting()));
    }

    /**
     * Creates a reporting period and its initial version (version_number=1, status=OPEN).
     */
    @Transactional
    public Period createPeriod(int periodMonth, int periodYear) {
        if (periodMonth < 1 || periodMonth > 12) {
            throw new BadRequestException("periodMonth must be between 1 and 12");
        }
        if (periodRepository.findByPeriodMonthAndPeriodYear(periodMonth, periodYear).isPresent()) {
            throw new ConflictException("Period already exists: " + periodMonth + "/" + periodYear);
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

    /**
     * Post-finalisation correction flow (ADR-018): new version only when the latest is FINALISED.
     */
    @Transactional
    public PeriodVersion createPeriodVersion(UUID periodId, String createdBy) {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new NotFoundException("Period not found: " + periodId));
        List<PeriodVersion> versions =
                periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(periodId);
        if (versions.isEmpty()) {
            throw new BadRequestException("Period has no versions: " + periodId);
        }
        PeriodVersion latest = latestNonSupersededVersion(versions);
        if (latest.getStatus() != PeriodStatus.FINALISED) {
            throw new BadRequestException(
                    "Cannot create a new version unless the latest version is FINALISED");
        }
        return periodVersionRepository.save(PeriodVersion.builder()
                .period(period)
                .versionNumber(latest.getVersionNumber() + 1)
                .status(PeriodStatus.OPEN)
                .latestFinalised(false)
                .createdBy(createdBy)
                .build());
    }

    /**
     * Builds master records for a period version. Only allowed when status is SNAPSHOTS_UPLOADED.
     */
    @Transactional
    public List<MasterRecord> buildMasterRecords(UUID periodVersionId) {
        PeriodVersion version = periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        if (version.getStatus() != PeriodStatus.SNAPSHOTS_UPLOADED) {
            throw new BadRequestException(
                    "build-master only allowed when status is SNAPSHOTS_UPLOADED, was " + version.getStatus());
        }

        Set<String> deliveryPus = configValues(ClassificationConfigType.DELIVERY_PU);
        Set<String> managementBus = configValues(ClassificationConfigType.MANAGEMENT_BU);
        Set<String> leadershipBus = configValues(ClassificationConfigType.LEADERSHIP_BU);

        List<PeopleSnapshot> peopleRows = peopleSnapshotRepository.findByPeriodVersionId(periodVersionId);
        List<PayrollSnapshot> payrollRows = payrollSnapshotRepository.findByPeriodVersionId(periodVersionId);

        Map<String, PayrollSnapshot> payrollByEmployeeNo = payrollRows.stream()
                .collect(Collectors.toMap(
                        PayrollSnapshot::getEmployeeNo,
                        Function.identity(),
                        PeoplePayrollService::preferRegularPayroll));

        Map<String, String> alternateToCanonical = alternateIdLinkRepository.findAll().stream()
                .collect(Collectors.toMap(
                        AlternateIdLink::getAlternateEmployeeNo,
                        link -> link.getEmployeeRegistry().getEmployeeId(),
                        (a, b) -> a));

        masterRecordRepository.deleteByPeriodVersionId(periodVersionId);

        Set<UUID> matchedPayrollIds = new HashSet<>();
        Set<UUID> matchedRegistryIds = new HashSet<>();
        List<MasterRecord> built = new ArrayList<>();

        for (PeopleSnapshot people : peopleRows) {
            EmployeeRegistry registry = upsertRegistryFromPeople(people);
            PayrollSnapshot payroll = findPayrollForEmployee(
                    people.getEmployeeId(), payrollByEmployeeNo, alternateToCanonical);
            if (payroll != null) {
                matchedPayrollIds.add(payroll.getId());
            }
            matchedRegistryIds.add(registry.getId());

            ClassificationFlags flags = classify(
                    people.getPracticeUnit(), people.getBusinessUnit(), people.getBillableStatus(),
                    deliveryPus, managementBus, leadershipBus);

            ReconciliationStatus status = payroll != null
                    ? ReconciliationStatus.MATCHED
                    : ReconciliationStatus.PAYROLL_PENDING;

            String billingCustomerCode = resolveBillingCustomerCode(
                    flags.billable(), people.getProjectCode());
            String dataQualityFlags = deriveDataQualityFlags(
                    flags.billable(),
                    people.getBusinessUnit(),
                    people.getBuCode(),
                    people.getProjectCode(),
                    billingCustomerCode);

            built.add(masterRecordRepository.save(MasterRecord.builder()
                    .periodVersion(version)
                    .employeeRegistry(registry)
                    .peopleSnapshot(people)
                    .payrollSnapshot(payroll)
                    .practiceUnit(people.getPracticeUnit())
                    .businessUnit(people.getBusinessUnit())
                    .billableStatus(people.getBillableStatus())
                    .jobLevel(people.getJobLevel())
                    .grossPay(payroll != null ? payroll.getGrossPay() : null)
                    .deliveryPu(flags.deliveryPu())
                    .billable(flags.billable())
                    .bench(flags.bench())
                    .support(flags.support())
                    .leadership(flags.leadership())
                    .management(flags.management())
                    .billingCustomerCode(billingCustomerCode)
                    .dataQualityFlags(dataQualityFlags)
                    .reconciliationStatus(status)
                    .build()));
        }

        for (PayrollSnapshot payroll : payrollRows) {
            if (matchedPayrollIds.contains(payroll.getId())) {
                continue;
            }
            // Resolve via alternate link or direct employee_id match in registry
            String resolvedEmployeeId = alternateToCanonical.getOrDefault(
                    payroll.getEmployeeNo(), payroll.getEmployeeNo());
            Optional<EmployeeRegistry> registryOpt =
                    employeeRegistryRepository.findByEmployeeId(resolvedEmployeeId);

            if (registryOpt.isPresent() && matchedRegistryIds.contains(registryOpt.get().getId())) {
                // Employee already has a master from people matching — additional payroll
                // rows (e.g. F&F) remain in payroll_snapshot for the combined detail view.
                continue;
            }

            if (registryOpt.isPresent() && registryOpt.get().getExitStatus() == ExitStatus.EXITED) {
                EmployeeRegistry registry = registryOpt.get();
                matchedRegistryIds.add(registry.getId());
                built.add(masterRecordRepository.save(MasterRecord.builder()
                        .periodVersion(version)
                        .employeeRegistry(registry)
                        .payrollSnapshot(payroll)
                        .grossPay(payroll.getGrossPay())
                        .reconciliationStatus(ReconciliationStatus.AUTO_MATCHED_EXITED)
                        .build()));
            } else if (payroll.getImportType() == ImportType.ZOHO_PAYROLL_FNF) {
                if (registryOpt.isPresent()) {
                    EmployeeRegistry registry = registryOpt.get();
                    built.add(masterRecordRepository.save(MasterRecord.builder()
                            .periodVersion(version)
                            .employeeRegistry(registry)
                            .payrollSnapshot(payroll)
                            .grossPay(payroll.getGrossPay())
                            .reconciliationStatus(ReconciliationStatus.UNMATCHED)
                            .build()));
                } else {
                    EmployeeRegistry placeholder = employeeRegistryRepository
                            .findByEmployeeId(payroll.getEmployeeNo())
                            .orElseGet(() -> employeeRegistryRepository.save(EmployeeRegistry.builder()
                                    .employeeId(payroll.getEmployeeNo())
                                    .fullName(payroll.getFullName())
                                    .exitStatus(ExitStatus.ACTIVE)
                                    .build()));
                    built.add(masterRecordRepository.save(MasterRecord.builder()
                            .periodVersion(version)
                            .employeeRegistry(placeholder)
                            .payrollSnapshot(payroll)
                            .grossPay(payroll.getGrossPay())
                            .reconciliationStatus(ReconciliationStatus.UNMATCHED)
                            .build()));
                }
            } else if (registryOpt.isEmpty()) {
                // Create a placeholder registry entry so we can store the unmatched payroll row
                EmployeeRegistry placeholder = employeeRegistryRepository
                        .findByEmployeeId(payroll.getEmployeeNo())
                        .orElseGet(() -> employeeRegistryRepository.save(EmployeeRegistry.builder()
                                .employeeId(payroll.getEmployeeNo())
                                .fullName(payroll.getFullName())
                                .exitStatus(ExitStatus.ACTIVE)
                                .build()));
                built.add(masterRecordRepository.save(MasterRecord.builder()
                        .periodVersion(version)
                        .employeeRegistry(placeholder)
                        .payrollSnapshot(payroll)
                        .grossPay(payroll.getGrossPay())
                        .reconciliationStatus(ReconciliationStatus.UNMATCHED)
                        .build()));
            } else {
                // Active in registry but absent from people snapshot — treat as exited lag without DAY_LEVEL exit
                EmployeeRegistry registry = registryOpt.get();
                built.add(masterRecordRepository.save(MasterRecord.builder()
                        .periodVersion(version)
                        .employeeRegistry(registry)
                        .payrollSnapshot(payroll)
                        .grossPay(payroll.getGrossPay())
                        .reconciliationStatus(ReconciliationStatus.AUTO_MATCHED_EXITED)
                        .build()));
            }
        }

        version.setStatus(PeriodStatus.MASTER_BUILT);
        periodVersionRepository.save(version);
        built.forEach(r -> Hibernate.initialize(r.getEmployeeRegistry()));
        return built;
    }

    /**
     * Finalises a period version and publishes {@link PeriodFinalisedEvent} (ADR-022).
     */
    @Transactional
    public void finalisePeriod(UUID periodVersionId) {
        finalisePeriod(periodVersionId, "system");
    }

    @Transactional
    public void finalisePeriod(UUID periodVersionId, String finalisedBy) {
        PeriodVersion version = periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        if (version.getStatus() != PeriodStatus.MASTER_BUILT) {
            throw new BadRequestException(
                    "finalise only allowed when status is MASTER_BUILT, was " + version.getStatus());
        }

        for (PeriodVersion prev : periodVersionRepository
                .findByPeriodIdAndLatestFinalisedTrue(version.getPeriod().getId())) {
            prev.setLatestFinalised(false);
            periodVersionRepository.save(prev);
        }

        version.setStatus(PeriodStatus.FINALISED);
        version.setLatestFinalised(true);
        version.setFinalisedAt(Instant.now());
        version.setFinalisedBy(finalisedBy);
        periodVersionRepository.save(version);

        eventPublisher.publishEvent(buildPeriodFinalisedEvent(version));
    }

    // ── Import mappings ──────────────────────────────────────────────────────

    public List<ImportColumnMapping> findActiveMappings() {
        List<ImportColumnMapping> mappings = importColumnMappingRepository.findByActiveTrue();
        mappings.forEach(m -> Hibernate.initialize(m.getLines()));
        return mappings;
    }

    public Optional<ImportColumnMapping> findActiveMapping(ImportType importType) {
        return importColumnMappingRepository.findByImportTypeAndActiveTrue(importType)
                .map(mapping -> {
                    Hibernate.initialize(mapping.getLines());
                    return mapping;
                });
    }

    public ImportColumnMapping getActiveMapping(ImportType importType) {
        return findActiveMapping(importType)
                .orElseThrow(() -> new NotFoundException(
                        "No active mapping template for import type: " + importType));
    }

    @Transactional
    public ImportColumnMapping saveMappingTemplate(
            ImportType importType, String templateName, List<MappingLineInput> lines) {
        importColumnMappingRepository.findByImportTypeAndActiveTrue(importType)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    importColumnMappingRepository.save(existing);
                });

        ImportColumnMapping mapping = ImportColumnMapping.builder()
                .importType(importType)
                .templateName(templateName)
                .active(true)
                .build();
        for (MappingLineInput line : lines) {
            ImportColumnMappingLine entity = ImportColumnMappingLine.builder()
                    .mapping(mapping)
                    .excelColumnName(line.excelColumnName())
                    .systemAttribute(line.systemAttribute())
                    .build();
            mapping.getLines().add(entity);
        }
        ImportColumnMapping saved = importColumnMappingRepository.save(mapping);
        Hibernate.initialize(saved.getLines());
        return saved;
    }

    /**
     * Uploads and ingests a snapshot Excel file for a period version (ADR-018, ADR-019, ADR-020).
     *
     * <p>Standard upload contract — applies to all current and future import types
     * ({@code ZOHO_PEOPLE}, {@code ZOHO_PAYROLL}, {@code ZOHO_PEOPLE_EXITED},
     * {@code ZOHO_PAYROLL_FNF}, and any subsequently added types):
     * <ul>
     *   <li>First upload of a given import type on the current version → normal insert.</li>
     *   <li>Re-upload of the same import type on the current version → auto-bump: mark the
     *       current version {@code SUPERSEDED}, create a new version ({@code OPEN}) with an
     *       incremented {@code version_number}, and insert rows under the new version.</li>
     *   <li>Uploading a different import type for the first time on the same version never
     *       triggers a version bump.</li>
     *   <li>{@code FINALISED} versions are never superseded — reject with HTTP 400.</li>
     * </ul>
     */
    @Transactional
    public SnapshotUploadResult uploadSnapshotFile(
            UUID periodVersionId,
            ImportType importType,
            UUID mappingId,
            MultipartFile file,
            String uploadedBy) {

        PeriodVersion version = periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        if (version.getStatus() == PeriodStatus.FINALISED) {
            throw new BadRequestException(
                    "This period is finalised. Use 'New Version' to make corrections.");
        }
        if (version.getStatus() == PeriodStatus.SUPERSEDED) {
            throw new BadRequestException("Cannot upload to a superseded period version");
        }

        boolean versionBumped = requiresVersionBump(periodVersionId, importType);
        PeriodVersion targetVersion = version;
        if (versionBumped) {
            version.setStatus(PeriodStatus.SUPERSEDED);
            periodVersionRepository.save(version);
            int nextVersionNumber = periodVersionRepository
                    .findByPeriodIdOrderByVersionNumberDesc(version.getPeriod().getId())
                    .stream()
                    .mapToInt(PeriodVersion::getVersionNumber)
                    .max()
                    .orElse(version.getVersionNumber()) + 1;
            targetVersion = periodVersionRepository.save(PeriodVersion.builder()
                    .period(version.getPeriod())
                    .versionNumber(nextVersionNumber)
                    .status(PeriodStatus.OPEN)
                    .latestFinalised(false)
                    .createdBy(uploadedBy)
                    .build());
        }
        UUID targetVersionId = targetVersion.getId();

        ImportColumnMapping mapping = importColumnMappingRepository.findById(mappingId)
                .orElseThrow(() -> new NotFoundException("Mapping template not found: " + mappingId));
        if (!mapping.isActive()) {
            throw new BadRequestException("Mapping template is not active: " + mappingId);
        }
        if (mapping.getImportType() != importType && !isPayrollMappingCompatible(importType, mapping.getImportType())) {
            throw new BadRequestException("Mapping import_type does not match upload import_type");
        }

        Map<String, String> excelToAttr = mapping.getLines().stream()
                .collect(Collectors.toMap(
                        ImportColumnMappingLine::getExcelColumnName,
                        ImportColumnMappingLine::getSystemAttribute,
                        (a, b) -> a,
                        LinkedHashMap::new));

        ExcelSnapshotParser.ParsedWorkbook parsed = excelSnapshotParser.parse(file, excelToAttr);
        List<Map<String, String>> rows = parsed.rows();

        validateNoDuplicateEmployeeKeys(importType, rows);

        SnapshotUpload upload = SnapshotUpload.builder()
                .periodVersion(targetVersion)
                .importType(importType)
                .uploadedBy(uploadedBy)
                .originalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.xlsx")
                .rowCount(rows.size())
                .unmappedColumns(joinCsv(parsed.unmappedColumns()))
                .missingColumns(joinCsv(parsed.missingColumns()))
                .build();

        List<String> unrecognizedBus = new ArrayList<>();

        switch (importType) {
            case ZOHO_PEOPLE -> {
                // Clear master first — master_record FKs people/payroll snapshots.
                clearMasterIfPresent(targetVersionId, targetVersion);
                peopleSnapshotRepository.deleteByPeriodVersionId(targetVersionId);
                snapshotUploadRepository.save(upload);
                for (Map<String, String> row : rows) {
                    PeopleSnapshot snap = persistPeopleRow(targetVersion, upload, row);
                    String buKey = firstNonBlank(snap.getBuCode(), snap.getBusinessUnit());
                    if (buKey != null && !isKnownCustomer(buKey) && !unrecognizedBus.contains(buKey)) {
                        unrecognizedBus.add(buKey);
                    }
                }
            }
            case ZOHO_PAYROLL, ZOHO_PAYROLL_FNF -> {
                clearMasterIfPresent(targetVersionId, targetVersion);
                payrollSnapshotRepository.deleteByPeriodVersionIdAndImportType(targetVersionId, importType);
                snapshotUploadRepository.save(upload);
                for (Map<String, String> row : rows) {
                    persistPayrollRow(targetVersion, upload, row, importType);
                }
            }
            case ZOHO_PEOPLE_EXITED -> {
                snapshotUploadRepository.save(upload);
                applyExitedEmployees(rows, upload);
            }
        }

        upload.setUnrecognizedBuCodes(joinCsv(unrecognizedBus));
        snapshotUploadRepository.save(upload);

        maybeAdvanceToSnapshotsUploaded(targetVersion);

        return new SnapshotUploadResult(
                upload.getId(),
                targetVersion.getId(),
                rows.size(),
                parsed.unmappedColumns(),
                parsed.missingColumns(),
                unrecognizedBus,
                targetVersion.getStatus());
    }

    public ImportPreview previewImport(UUID periodVersionId) {
        periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));

        List<PeopleSnapshot> people = peopleSnapshotRepository.findByPeriodVersionId(periodVersionId);
        List<PayrollSnapshot> payroll = payrollSnapshotRepository.findByPeriodVersionId(periodVersionId);
        List<SnapshotUpload> uploads =
                snapshotUploadRepository.findByPeriodVersionIdOrderByUploadedAtDesc(periodVersionId);

        List<String> unmapped = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> unrecognizedBu = new ArrayList<>();
        for (SnapshotUpload u : uploads) {
            mergeCsvInto(unmapped, u.getUnmappedColumns());
            mergeCsvInto(missing, u.getMissingColumns());
            mergeCsvInto(unrecognizedBu, u.getUnrecognizedBuCodes());
        }

        return new ImportPreview(
                people.stream().limit(10).toList(),
                payroll.stream().limit(10).toList(),
                unmapped,
                missing,
                unrecognizedBu);
    }

    public SnapshotDetailResponse getSnapshotDetail(UUID periodVersionId, ImportType importType) {
        PeriodVersion version = periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        Hibernate.initialize(version.getPeriod());

        return switch (importType) {
            case ZOHO_PEOPLE -> {
                SnapshotUpload upload = snapshotUploadRepository
                        .findFirstByPeriodVersionIdAndImportTypeOrderByUploadedAtDesc(
                                periodVersionId, importType)
                        .orElseThrow(() -> new NotFoundException(
                                "No snapshot upload for period version " + periodVersionId
                                        + " and import type " + importType));
                yield SnapshotDetailResponse.of(
                        version,
                        upload,
                        peopleSnapshotRepository.findBySnapshotUploadIdOrderByEmployeeIdAsc(upload.getId()).stream()
                                .map(PeopleSnapshotDetailResponse::from)
                                .toList(),
                        List.of(),
                        List.of());
            }
            case ZOHO_PAYROLL, ZOHO_PAYROLL_FNF -> buildPayrollSnapshotDetail(version, importType);
            case ZOHO_PEOPLE_EXITED -> {
                SnapshotUpload upload = snapshotUploadRepository
                        .findFirstByPeriodVersionIdAndImportTypeOrderByUploadedAtDesc(
                                periodVersionId, importType)
                        .orElseThrow(() -> new NotFoundException(
                                "No snapshot upload for period version " + periodVersionId
                                        + " and import type " + importType));
                yield SnapshotDetailResponse.of(
                        version,
                        upload,
                        List.of(),
                        List.of(),
                        employeeRegistryRepository.findByLastUpdatedByUpload_IdOrderByEmployeeIdAsc(upload.getId()).stream()
                                .map(ExitedRegistryDetailResponse::from)
                                .toList());
            }
        };
    }

    private SnapshotDetailResponse buildPayrollSnapshotDetail(
            PeriodVersion version, ImportType requestedType) {
        UUID versionId = version.getId();
        List<SnapshotUpload> payrollUploads = snapshotUploadRepository.findByPeriodVersionId(versionId).stream()
                .filter(u -> u.getImportType() == ImportType.ZOHO_PAYROLL
                        || u.getImportType() == ImportType.ZOHO_PAYROLL_FNF)
                .sorted(Comparator.comparing(SnapshotUpload::getUploadedAt).reversed())
                .toList();
        if (payrollUploads.isEmpty()) {
            throw new NotFoundException(
                    "No payroll snapshot upload for period version " + versionId);
        }
        SnapshotUpload primary = payrollUploads.stream()
                .filter(u -> u.getImportType() == requestedType)
                .findFirst()
                .orElse(payrollUploads.getFirst());
        List<PayrollSnapshotDetailResponse> rows = payrollSnapshotRepository
                .findByPeriodVersionIdOrderByEmployeeNoAsc(versionId).stream()
                .map(PayrollSnapshotDetailResponse::from)
                .toList();
        return SnapshotDetailResponse.ofPayroll(
                version, requestedType, primary, payrollUploads, rows);
    }

    // ── Employee Registry ──────────────────────────────────────────────────────

    public List<EmployeeRegistry> findAllEmployees() {
        return employeeRegistryRepository.findAll();
    }

    public Optional<EmployeeRegistry> findEmployeeByEmployeeId(String employeeId) {
        return employeeRegistryRepository.findByEmployeeId(employeeId);
    }

    public List<AlternateIdLink> findAllAlternateIdLinks() {
        List<AlternateIdLink> links = alternateIdLinkRepository.findAll();
        links.forEach(link -> Hibernate.initialize(link.getEmployeeRegistry()));
        return links;
    }

    @Transactional
    public EmployeeRegistry registerEmployee(String employeeId, String fullName) {
        if (employeeRegistryRepository.existsByEmployeeId(employeeId)) {
            throw new ConflictException("Employee already registered: " + employeeId);
        }
        return employeeRegistryRepository.save(EmployeeRegistry.builder()
                .employeeId(employeeId)
                .fullName(fullName)
                .exitStatus(ExitStatus.ACTIVE)
                .build());
    }

    // ── Master data ───────────────────────────────────────────────────────────

    public List<MasterRecord> findMasterRecords(UUID periodVersionId) {
        periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        List<MasterRecord> records = masterRecordRepository.findByPeriodVersionId(periodVersionId);
        records.forEach(r -> Hibernate.initialize(r.getEmployeeRegistry()));
        return records;
    }

    public MasterSummary summarizeMaster(UUID periodVersionId) {
        List<MasterRecord> records = findMasterRecords(periodVersionId);

        int billableHc = 0, benchHc = 0, supportHc = 0, leadershipHc = 0, managementHc = 0;
        BigDecimal billablePay = BigDecimal.ZERO;
        BigDecimal benchPay = BigDecimal.ZERO;
        BigDecimal supportPay = BigDecimal.ZERO;
        BigDecimal leadershipPay = BigDecimal.ZERO;
        BigDecimal managementPay = BigDecimal.ZERO;

        Map<String, BuAggregate> byBu = new LinkedHashMap<>();

        for (MasterRecord r : records) {
            // Exited / unmatched payroll-only rows excluded from current-period headcount (spec §7.2)
            if (r.getReconciliationStatus() == ReconciliationStatus.AUTO_MATCHED_EXITED
                    || r.getReconciliationStatus() == ReconciliationStatus.UNMATCHED) {
                continue;
            }
            BigDecimal pay = r.getGrossPay() != null ? r.getGrossPay() : BigDecimal.ZERO;

            // Salary bucketing priority: Leadership salary always in Leadership bucket (spec §8)
            if (r.isLeadership()) {
                leadershipHc++;
                leadershipPay = leadershipPay.add(pay);
            } else if (r.isManagement()) {
                managementHc++;
                managementPay = managementPay.add(pay);
            } else if (r.isBillable()) {
                billableHc++;
                billablePay = billablePay.add(pay);
            } else if (r.isBench()) {
                benchHc++;
                benchPay = benchPay.add(pay);
            } else if (r.isSupport()) {
                supportHc++;
                supportPay = supportPay.add(pay);
            }

            String bu = r.getBusinessUnit() != null ? r.getBusinessUnit() : "(unknown)";
            BuAggregate agg = byBu.computeIfAbsent(bu, k -> new BuAggregate(0, BigDecimal.ZERO));
            int billableInc = r.isBillable() ? 1 : 0;
            byBu.put(bu, new BuAggregate(agg.billableHc() + billableInc, agg.totalGrossPay().add(pay)));
        }

        List<BuBreakdown> buBreakdown = byBu.entrySet().stream()
                .map(e -> new BuBreakdown(e.getKey(), e.getValue().billableHc(), e.getValue().totalGrossPay()))
                .toList();

        return new MasterSummary(
                billableHc, billablePay,
                benchHc, benchPay,
                supportHc, supportPay,
                leadershipHc, leadershipPay,
                managementHc, managementPay,
                buBreakdown);
    }

    @Transactional
    public MasterRecord reconcileManually(
            UUID periodVersionId, UUID payrollSnapshotId, UUID employeeRegistryId, String mappedBy) {

        PeriodVersion version = periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        if (version.getStatus() == PeriodStatus.FINALISED) {
            throw new BadRequestException("Cannot reconcile on a FINALISED period version");
        }

        PayrollSnapshot payroll = payrollSnapshotRepository.findById(payrollSnapshotId)
                .orElseThrow(() -> new NotFoundException("Payroll snapshot not found: " + payrollSnapshotId));
        if (!payroll.getPeriodVersion().getId().equals(periodVersionId)) {
            throw new BadRequestException("Payroll snapshot does not belong to this period version");
        }

        EmployeeRegistry registry = employeeRegistryRepository.findById(employeeRegistryId)
                .orElseThrow(() -> new NotFoundException("Employee registry not found: " + employeeRegistryId));

        if (alternateIdLinkRepository.findByAlternateEmployeeNo(payroll.getEmployeeNo()).isEmpty()) {
            alternateIdLinkRepository.save(AlternateIdLink.builder()
                    .employeeRegistry(registry)
                    .alternateEmployeeNo(payroll.getEmployeeNo())
                    .mappedBy(mappedBy)
                    .build());
        }

        MasterRecord master = masterRecordRepository
                .findByPeriodVersionIdAndPayrollSnapshotId(periodVersionId, payrollSnapshotId)
                .orElseGet(() -> MasterRecord.builder()
                        .periodVersion(version)
                        .employeeRegistry(registry)
                        .payrollSnapshot(payroll)
                        .grossPay(payroll.getGrossPay())
                        .reconciliationStatus(ReconciliationStatus.MANUALLY_MAPPED)
                        .build());

        master.setEmployeeRegistry(registry);
        master.setPayrollSnapshot(payroll);
        master.setGrossPay(payroll.getGrossPay());
        master.setReconciliationStatus(ReconciliationStatus.MANUALLY_MAPPED);
        MasterRecord saved = masterRecordRepository.save(master);
        Hibernate.initialize(saved.getEmployeeRegistry());
        return saved;
    }

    // ── Classification config ──────────────────────────────────────────────────

    public List<ClassificationConfig> findClassificationByType(ClassificationConfigType configType) {
        return classificationConfigRepository.findByConfigType(configType);
    }

    public List<ClassificationConfig> findAllClassificationConfig() {
        return classificationConfigRepository.findAll();
    }

    @Transactional
    public ClassificationConfig addClassificationConfig(ClassificationConfigType configType, String value) {
        if (classificationConfigRepository.existsByConfigTypeAndValue(configType, value)) {
            throw new ConflictException(
                    "Value already exists for " + configType + ": " + value);
        }
        return classificationConfigRepository.save(ClassificationConfig.builder()
                .configType(configType)
                .value(value)
                .build());
    }

    @Transactional
    public void deleteClassificationConfig(UUID id) {
        ClassificationConfig entry = classificationConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Classification config not found: " + id));
        long remaining = classificationConfigRepository.countByConfigType(entry.getConfigType());
        if (remaining <= 1) {
            throw new BadRequestException(
                    "Cannot remove the last entry for config_type " + entry.getConfigType());
        }
        classificationConfigRepository.delete(entry);
    }

    // ── Cross-module validation ───────────────────────────────────────────────

    public boolean isKnownCustomer(String buCode) {
        return customerService.isKnownCustomer(buCode);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Set<String> configValues(ClassificationConfigType type) {
        return classificationConfigRepository.findByConfigType(type).stream()
                .map(ClassificationConfig::getValue)
                .collect(Collectors.toSet());
    }

    private EmployeeRegistry upsertRegistryFromPeople(PeopleSnapshot people) {
        return employeeRegistryRepository.findByEmployeeId(people.getEmployeeId())
                .map(existing -> {
                    existing.setFullName(people.getFullName());
                    if (people.getDateOfJoining() != null) {
                        existing.setDateOfJoining(people.getDateOfJoining());
                    }
                    return employeeRegistryRepository.save(existing);
                })
                .orElseGet(() -> employeeRegistryRepository.save(EmployeeRegistry.builder()
                        .employeeId(people.getEmployeeId())
                        .fullName(people.getFullName())
                        .dateOfJoining(people.getDateOfJoining())
                        .exitStatus(ExitStatus.ACTIVE)
                        .build()));
    }

    private PayrollSnapshot findPayrollForEmployee(
            String employeeId,
            Map<String, PayrollSnapshot> payrollByEmployeeNo,
            Map<String, String> alternateToCanonical) {
        PayrollSnapshot direct = payrollByEmployeeNo.get(employeeId);
        if (direct != null) {
            return direct;
        }
        for (var entry : alternateToCanonical.entrySet()) {
            if (employeeId.equals(entry.getValue())) {
                PayrollSnapshot viaAlt = payrollByEmployeeNo.get(entry.getKey());
                if (viaAlt != null) {
                    return viaAlt;
                }
            }
        }
        return null;
    }

    private String resolveBillingCustomerCode(boolean billable, String projectCode) {
        if (!billable || projectCode == null || projectCode.isBlank()) {
            return null;
        }
        return customerService.findCustomerCodeByProjectCode(projectCode);
    }

    private String deriveDataQualityFlags(
            boolean billable,
            String businessUnit,
            String buCode,
            String projectCode,
            String billingCustomerCode) {
        List<String> flags = new ArrayList<>();
        String buKey = buCode != null && !buCode.isBlank() ? buCode : businessUnit;
        boolean isInternalBu = customerService.isInternalBu(buKey);

        if (!isInternalBu) {
            if (projectCode == null || projectCode.isBlank()) {
                flags.add("MISSING_PROJECT_CODE");
            } else if (customerService.findCustomerCodeByProjectCode(projectCode) == null) {
                flags.add("PROJECT_CODE_NOT_FOUND");
            }
        }

        if (billable && billingCustomerCode == null) {
            flags.add("BILLING_CLIENT_UNRESOLVED");
        }

        return flags.isEmpty() ? null : String.join(",", flags);
    }

    private ClassificationFlags classify(
            String practiceUnit,
            String businessUnit,
            String billableStatus,
            Set<String> deliveryPus,
            Set<String> managementBus,
            Set<String> leadershipBus) {

        boolean isDeliveryPu = practiceUnit != null && deliveryPus.contains(practiceUnit);
        boolean isManagement = businessUnit != null && managementBus.contains(businessUnit);
        boolean isLeadership = businessUnit != null && leadershipBus.contains(businessUnit);
        boolean billableY = "Y".equalsIgnoreCase(billableStatus);

        boolean isBillable = billableY;
        boolean isBench = isDeliveryPu && !billableY && !isManagement && !isLeadership;
        boolean isSupport = !isDeliveryPu && !isManagement && !isLeadership;

        return new ClassificationFlags(
                isDeliveryPu, isBillable, isBench, isSupport, isLeadership, isManagement);
    }

    private PeriodFinalisedEvent buildPeriodFinalisedEvent(PeriodVersion version) {
        MasterSummary summary = summarizeMaster(version.getId());
        Map<String, Integer> hcByBu = summary.buBreakdown().stream()
                .collect(Collectors.toMap(BuBreakdown::businessUnit, BuBreakdown::billableHc, Integer::sum));
        BigDecimal totalPay = summary.billableGrossPay()
                .add(summary.benchGrossPay())
                .add(summary.supportGrossPay())
                .add(summary.leadershipGrossPay())
                .add(summary.managementGrossPay());
        return new PeriodFinalisedEvent(
                version.getId(),
                summary.billableHc(),
                summary.benchHc(),
                summary.supportHc(),
                summary.leadershipHc(),
                summary.managementHc(),
                totalPay,
                hcByBu);
    }

    private void validateNoDuplicateEmployeeKeys(ImportType importType, List<Map<String, String>> rows) {
        Set<String> seen = new HashSet<>();
        String keyAttr = isPayrollImportType(importType)
                ? SystemAttribute.EMPLOYEE_NO
                : SystemAttribute.EMPLOYEE_ID;
        for (Map<String, String> row : rows) {
            String key = ExcelSnapshotParser.optional(row, keyAttr);
            if (key == null && isPayrollImportType(importType)) {
                key = ExcelSnapshotParser.optional(row, SystemAttribute.EMPLOYEE_ID);
            }
            if (key == null) {
                continue;
            }
            if (!seen.add(key)) {
                throw new BadRequestException("Duplicate employee key in file: " + key);
            }
        }
    }

    private PeopleSnapshot persistPeopleRow(
            PeriodVersion version, SnapshotUpload upload, Map<String, String> row) {
        String billable = ExcelSnapshotParser.required(row, SystemAttribute.BILLABLE_STATUS);
        if (!billable.equalsIgnoreCase("Y") && !billable.equalsIgnoreCase("N")) {
            throw new BadRequestException("BillableStatus must be Y or N, got: " + billable);
        }
        PeopleSnapshot snap = PeopleSnapshot.builder()
                .snapshotUpload(upload)
                .periodVersion(version)
                .employeeId(ExcelSnapshotParser.required(row, SystemAttribute.EMPLOYEE_ID))
                .fullName(ExcelSnapshotParser.required(row, SystemAttribute.FULL_NAME))
                .practiceUnit(ExcelSnapshotParser.required(row, SystemAttribute.PRACTICE_UNIT))
                .businessUnit(ExcelSnapshotParser.required(row, SystemAttribute.BUSINESS_UNIT))
                .buCode(ExcelSnapshotParser.optional(row, SystemAttribute.BU_CODE))
                .projectCode(ExcelSnapshotParser.optional(row, SystemAttribute.PROJECT_CODE))
                .billableStatus(billable.toUpperCase(Locale.ROOT))
                .jobLevel(ExcelSnapshotParser.optional(row, SystemAttribute.JOB_LEVEL))
                .jobSubLevel(ExcelSnapshotParser.optional(row, SystemAttribute.JOB_SUB_LEVEL))
                .title(ExcelSnapshotParser.optional(row, SystemAttribute.TITLE))
                .dateOfJoining(ExcelSnapshotParser.optionalDate(row, SystemAttribute.DATE_OF_JOINING))
                .build();
        PeopleSnapshot saved = peopleSnapshotRepository.save(snap);
        upsertRegistryFromPeople(saved);
        return saved;
    }

    private void persistPayrollRow(
            PeriodVersion version, SnapshotUpload upload, Map<String, String> row, ImportType importType) {
        String employeeNo = ExcelSnapshotParser.optional(row, SystemAttribute.EMPLOYEE_NO);
        if (employeeNo == null) {
            employeeNo = ExcelSnapshotParser.required(row, SystemAttribute.EMPLOYEE_ID);
        }
        PayrollSnapshot snap = PayrollSnapshot.builder()
                .snapshotUpload(upload)
                .periodVersion(version)
                .importType(importType)
                .employeeNo(employeeNo)
                .fullName(ExcelSnapshotParser.required(row, SystemAttribute.FULL_NAME))
                .grossPay(ExcelSnapshotParser.requiredDecimal(row, SystemAttribute.GROSS_PAY))
                .netPay(ExcelSnapshotParser.requiredDecimal(row, SystemAttribute.NET_PAY))
                .ctcPerAnnum(ExcelSnapshotParser.optionalDecimal(row, SystemAttribute.CTC_PER_ANNUM))
                .build();
        payrollSnapshotRepository.save(snap);
    }

    private void applyExitedEmployees(List<Map<String, String>> rows, SnapshotUpload upload) {
        for (Map<String, String> row : rows) {
            String employeeId = ExcelSnapshotParser.required(row, SystemAttribute.EMPLOYEE_ID);
            LocalDate lastWorkingDay =
                    ExcelSnapshotParser.requiredDate(row, SystemAttribute.LAST_WORKING_DAY);
            employeeRegistryRepository.findByEmployeeId(employeeId).ifPresent(registry -> {
                registry.setExitDate(lastWorkingDay);
                registry.setExitDatePrecision(ExitDatePrecision.DAY_LEVEL);
                if (registry.getExitStatus() != ExitStatus.EXITED) {
                    registry.setExitStatus(ExitStatus.EXITED);
                }
                registry.setLastUpdatedByUpload(upload);
                employeeRegistryRepository.save(registry);
            });
        }
    }

    private static PeriodVersion latestNonSupersededVersion(List<PeriodVersion> versions) {
        return versions.stream()
                .filter(v -> v.getStatus() != PeriodStatus.SUPERSEDED)
                .findFirst()
                .orElse(versions.getFirst());
    }

    /**
     * True when this version already has data for the import type — triggers auto-bump on re-upload.
     * Checks both snapshot_upload records and persisted snapshot rows so re-upload is detected even
     * if upload metadata is missing.
     */
    private boolean requiresVersionBump(UUID periodVersionId, ImportType importType) {
        if (snapshotUploadRepository.existsByPeriodVersionIdAndImportType(periodVersionId, importType)) {
            return true;
        }
        return switch (importType) {
            case ZOHO_PEOPLE -> peopleSnapshotRepository.countByPeriodVersionId(periodVersionId) > 0;
            case ZOHO_PAYROLL, ZOHO_PAYROLL_FNF ->
                    payrollSnapshotRepository.existsByPeriodVersionIdAndImportType(periodVersionId, importType);
            case ZOHO_PEOPLE_EXITED -> false;
        };
    }

    private void clearMasterIfPresent(UUID periodVersionId, PeriodVersion version) {
        if (version.getStatus() == PeriodStatus.MASTER_BUILT
                || !masterRecordRepository.findByPeriodVersionId(periodVersionId).isEmpty()) {
            masterRecordRepository.deleteByPeriodVersionId(periodVersionId);
            if (version.getStatus() == PeriodStatus.MASTER_BUILT) {
                version.setStatus(PeriodStatus.SNAPSHOTS_UPLOADED);
                periodVersionRepository.save(version);
            }
        }
    }

    private void maybeAdvanceToSnapshotsUploaded(PeriodVersion version) {
        UUID versionId = version.getId();
        boolean hasPeople = peopleSnapshotRepository.countByPeriodVersionId(versionId) > 0;
        boolean hasPayrollUpload = snapshotUploadRepository.existsByPeriodVersionIdAndImportType(
                        versionId, ImportType.ZOHO_PAYROLL)
                || snapshotUploadRepository.existsByPeriodVersionIdAndImportType(
                        versionId, ImportType.ZOHO_PAYROLL_FNF);

        if (hasPeople && hasPayrollUpload && version.getStatus() == PeriodStatus.OPEN) {
            version.setStatus(PeriodStatus.SNAPSHOTS_UPLOADED);
            periodVersionRepository.save(version);
        }
    }

    private static boolean isPayrollImportType(ImportType importType) {
        return importType == ImportType.ZOHO_PAYROLL || importType == ImportType.ZOHO_PAYROLL_FNF;
    }

    private static boolean isPayrollMappingCompatible(ImportType uploadType, ImportType mappingType) {
        return isPayrollImportType(uploadType) && isPayrollImportType(mappingType);
    }

    /** When an employee has both regular and F&F rows, people matching uses regular payroll. */
    private static PayrollSnapshot preferRegularPayroll(PayrollSnapshot a, PayrollSnapshot b) {
        if (a.getImportType() == ImportType.ZOHO_PAYROLL) {
            return a;
        }
        if (b.getImportType() == ImportType.ZOHO_PAYROLL) {
            return b;
        }
        return a;
    }

    private static String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private static void mergeCsvInto(List<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && !target.contains(t)) {
                target.add(t);
            }
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    // ── public result types (module API) ─────────────────────────────────────

    public record MappingLineInput(String excelColumnName, String systemAttribute) {}

    public record SnapshotUploadResult(
            UUID uploadId,
            UUID periodVersionId,
            int rowsImported,
            List<String> unmappedColumns,
            List<String> missingColumns,
            List<String> unrecognizedBuCodes,
            PeriodStatus periodVersionStatus
    ) {}

    public record ImportPreview(
            List<PeopleSnapshot> peoplePreview,
            List<PayrollSnapshot> payrollPreview,
            List<String> unmappedColumns,
            List<String> missingColumns,
            List<String> unrecognizedBuCodes
    ) {}

    public record MasterSummary(
            int billableHc, BigDecimal billableGrossPay,
            int benchHc, BigDecimal benchGrossPay,
            int supportHc, BigDecimal supportGrossPay,
            int leadershipHc, BigDecimal leadershipGrossPay,
            int managementHc, BigDecimal managementGrossPay,
            List<BuBreakdown> buBreakdown
    ) {}

    public record BuBreakdown(String businessUnit, int billableHc, BigDecimal totalGrossPay) {}

    private record BuAggregate(int billableHc, BigDecimal totalGrossPay) {}

    private record ClassificationFlags(
            boolean deliveryPu,
            boolean billable,
            boolean bench,
            boolean support,
            boolean leadership,
            boolean management
    ) {}
}
