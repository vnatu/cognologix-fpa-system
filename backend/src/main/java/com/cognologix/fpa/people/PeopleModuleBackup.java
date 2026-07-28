package com.cognologix.fpa.people;

import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.people.domain.*;
import com.cognologix.fpa.people.repository.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.cognologix.fpa.general.BackupGridHelper.*;

/**
 * Backup/restore grid operations for People &amp; Payroll (ADR-044 Tier 2).
 */
@Component
@RequiredArgsConstructor
class PeopleModuleBackup {

    static final String FILE_CLASSIFICATION = "classification_config.xlsx";
    static final String FILE_MAPPINGS = "column_mapping_templates.xlsx";
    static final String FILE_PERIODS = "periods.xlsx";
    static final String FILE_PERIOD_VERSIONS = "period_versions.xlsx";
    static final String FILE_EMPLOYEE_REGISTRY = "employee_registry.xlsx";
    static final String FILE_ALTERNATE_IDS = "alternate_id_links.xlsx";
    static final String FILE_PEOPLE_SNAPSHOTS = "zoho_people_snapshots.xlsx";
    static final String FILE_PAYROLL_SNAPSHOTS = "zoho_payroll_snapshots.xlsx";
    static final String FILE_MASTER_RECORDS = "master_records.xlsx";

    static final String RESTORE_UPLOAD_FILENAME = "backup-restore.xlsx";
    static final String RESTORE_UPLOAD_BY = "restore";

    private final ClassificationConfigRepository classificationConfigRepository;
    private final ImportColumnMappingRepository importColumnMappingRepository;
    private final ImportColumnMappingLineRepository importColumnMappingLineRepository;
    private final PeriodRepository periodRepository;
    private final PeriodVersionRepository periodVersionRepository;
    private final EmployeeRegistryRepository employeeRegistryRepository;
    private final AlternateIdLinkRepository alternateIdLinkRepository;
    private final SnapshotUploadRepository snapshotUploadRepository;
    private final PeopleSnapshotRepository peopleSnapshotRepository;
    private final PayrollSnapshotRepository payrollSnapshotRepository;
    private final MasterRecordRepository masterRecordRepository;

    List<BackupSheet> exportBackupSheets() {
        return List.of(
                exportClassificationSheet(),
                exportMappingSheet(),
                exportPeriodsSheet(),
                exportPeriodVersionsSheet(),
                exportEmployeeRegistrySheet(),
                exportAlternateIdLinksSheet(),
                exportPeopleSnapshotsSheet(),
                exportPayrollSnapshotsSheet(),
                exportMasterRecordsSheet());
    }

    BackupSheet exportClassificationSheet() {
        List<ClassificationConfig> configs = classificationConfigRepository.findAll();
        configs.sort(Comparator
                .comparing((ClassificationConfig c) -> c.getConfigType().name())
                .thenComparing(ClassificationConfig::getValue, String.CASE_INSENSITIVE_ORDER));
        List<String[]> rows = new ArrayList<>();
        for (ClassificationConfig c : configs) {
            rows.add(new String[]{c.getConfigType().name(), c.getValue()});
        }
        return new BackupSheet(FILE_CLASSIFICATION, new String[]{"config_type", "value"}, rows);
    }

    BackupSheet exportMappingSheet() {
        List<ImportColumnMapping> mappings = importColumnMappingRepository.findAll();
        mappings.forEach(m -> Hibernate.initialize(m.getLines()));
        List<String[]> rows = new ArrayList<>();
        mappings.stream()
                .sorted(Comparator.comparing((ImportColumnMapping m) -> m.getImportType().name())
                        .thenComparing(ImportColumnMapping::getTemplateName))
                .forEach(m -> m.getLines().stream()
                        .sorted(Comparator.comparing(ImportColumnMappingLine::getExcelColumnName))
                        .forEach(line -> rows.add(row(
                                m.getImportType().name(),
                                m.getTemplateName(),
                                line.getExcelColumnName(),
                                line.getSystemAttribute()))));
        return new BackupSheet(FILE_MAPPINGS,
                new String[]{"import_type", "template_name", "excel_column_name", "system_attribute"}, rows);
    }

    BackupSheet exportPeriodsSheet() {
        List<String[]> rows = periodRepository.findAll().stream()
                .sorted(Comparator.comparing(Period::getPeriodYear).thenComparing(Period::getPeriodMonth))
                .map(p -> row(str(p.getPeriodMonth()), str(p.getPeriodYear())))
                .toList();
        return new BackupSheet(FILE_PERIODS, new String[]{"period_month", "period_year"}, rows);
    }

    BackupSheet exportPeriodVersionsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (PeriodVersion pv : periodVersionRepository.findAll()) {
            Hibernate.initialize(pv.getPeriod());
            rows.add(row(
                    str(pv.getPeriod().getPeriodMonth()),
                    str(pv.getPeriod().getPeriodYear()),
                    str(pv.getVersionNumber()),
                    pv.getStatus().name(),
                    pv.getFinalisedAt() != null ? pv.getFinalisedAt().toString() : "",
                    str(pv.getFinalisedBy())));
        }
        rows = sortedRows(rows, 1, 0, 2);
        return new BackupSheet(FILE_PERIOD_VERSIONS,
                new String[]{"period_month", "period_year", "version_number", "status", "finalised_at", "finalised_by"},
                rows);
    }

    BackupSheet exportEmployeeRegistrySheet() {
        List<String[]> rows = employeeRegistryRepository.findAll().stream()
                .sorted(Comparator.comparing(EmployeeRegistry::getEmployeeId))
                .map(e -> row(
                        e.getEmployeeId(),
                        e.getFullName(),
                        e.getDateOfJoining() != null ? e.getDateOfJoining().toString() : "",
                        e.getExitStatus().name(),
                        e.getExitDate() != null ? e.getExitDate().toString() : "",
                        e.getExitDatePrecision() != null ? e.getExitDatePrecision().name() : ""))
                .toList();
        return new BackupSheet(FILE_EMPLOYEE_REGISTRY,
                new String[]{"employee_id", "full_name", "date_of_joining", "exit_status", "exit_date", "exit_date_precision"},
                rows);
    }

    BackupSheet exportAlternateIdLinksSheet() {
        List<String[]> rows = new ArrayList<>();
        for (AlternateIdLink link : alternateIdLinkRepository.findAll()) {
            Hibernate.initialize(link.getEmployeeRegistry());
            rows.add(row(
                    link.getAlternateEmployeeNo(),
                    link.getEmployeeRegistry().getEmployeeId(),
                    link.getMappedBy(),
                    link.getMappedAt().toString()));
        }
        return new BackupSheet(FILE_ALTERNATE_IDS,
                new String[]{"alternate_employee_no", "employee_id", "mapped_by", "mapped_at"}, rows);
    }

    BackupSheet exportPeopleSnapshotsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (PeopleSnapshot snap : peopleSnapshotRepository.findAll()) {
            Hibernate.initialize(snap.getPeriodVersion());
            Hibernate.initialize(snap.getPeriodVersion().getPeriod());
            Hibernate.initialize(snap.getSnapshotUpload());
            Period p = snap.getPeriodVersion().getPeriod();
            rows.add(row(
                    str(p.getPeriodMonth()),
                    str(p.getPeriodYear()),
                    str(snap.getPeriodVersion().getVersionNumber()),
                    snap.getSnapshotUpload().getImportType().name(),
                    snap.getEmployeeId(),
                    snap.getFullName(),
                    snap.getPracticeUnit(),
                    snap.getBusinessUnit(),
                    str(snap.getBuCode()),
                    str(snap.getProjectCode()),
                    snap.getBillableStatus(),
                    str(snap.getJobLevel()),
                    str(snap.getJobSubLevel()),
                    str(snap.getTitle()),
                    snap.getDateOfJoining() != null ? snap.getDateOfJoining().toString() : ""));
        }
        return new BackupSheet(FILE_PEOPLE_SNAPSHOTS,
                new String[]{"period_month", "period_year", "period_version_number", "import_type",
                        "employee_id", "full_name", "practice_unit", "business_unit", "bu_code", "project_code",
                        "billable_status", "job_level", "job_sub_level", "title", "date_of_joining"},
                rows);
    }

    BackupSheet exportPayrollSnapshotsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (PayrollSnapshot snap : payrollSnapshotRepository.findAll()) {
            Hibernate.initialize(snap.getPeriodVersion());
            Hibernate.initialize(snap.getPeriodVersion().getPeriod());
            Hibernate.initialize(snap.getSnapshotUpload());
            Period p = snap.getPeriodVersion().getPeriod();
            rows.add(row(
                    str(p.getPeriodMonth()),
                    str(p.getPeriodYear()),
                    str(snap.getPeriodVersion().getVersionNumber()),
                    snap.getImportType().name(),
                    snap.getEmployeeNo(),
                    snap.getFullName(),
                    snap.getGrossPay().toPlainString(),
                    snap.getNetPay().toPlainString(),
                    snap.getCtcPerAnnum() != null ? snap.getCtcPerAnnum().toPlainString() : "",
                    snap.getEpfContribution() != null ? snap.getEpfContribution().toPlainString() : "",
                    snap.getEpsContribution() != null ? snap.getEpsContribution().toPlainString() : "",
                    snap.getEdliContribution() != null ? snap.getEdliContribution().toPlainString() : "",
                    snap.getEpfAdminCharges() != null ? snap.getEpfAdminCharges().toPlainString() : "",
                    snap.getVpf() != null ? snap.getVpf().toPlainString() : "",
                    snap.getNpsDeduction() != null ? snap.getNpsDeduction().toPlainString() : "",
                    snap.getGratuity() != null ? snap.getGratuity().toPlainString() : ""));
        }
        return new BackupSheet(FILE_PAYROLL_SNAPSHOTS,
                new String[]{"period_month", "period_year", "period_version_number", "import_type",
                        "employee_no", "full_name", "gross_pay", "net_pay", "ctc_per_annum",
                        "epf_contribution", "eps_contribution", "edli_contribution", "epf_admin_charges",
                        "vpf", "nps_deduction", "gratuity"},
                rows);
    }

    BackupSheet exportMasterRecordsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (MasterRecord mr : masterRecordRepository.findAll()) {
            Hibernate.initialize(mr.getPeriodVersion());
            Hibernate.initialize(mr.getPeriodVersion().getPeriod());
            Hibernate.initialize(mr.getEmployeeRegistry());
            Period p = mr.getPeriodVersion().getPeriod();
            rows.add(row(
                    str(p.getPeriodMonth()),
                    str(p.getPeriodYear()),
                    str(mr.getPeriodVersion().getVersionNumber()),
                    mr.getEmployeeRegistry().getEmployeeId(),
                    str(mr.getPracticeUnit()),
                    str(mr.getBusinessUnit()),
                    str(mr.getBillableStatus()),
                    str(mr.getJobLevel()),
                    mr.getGrossPay() != null ? mr.getGrossPay().toPlainString() : "",
                    mr.getTotalEmployerContributions() != null
                            ? mr.getTotalEmployerContributions().toPlainString() : "",
                    String.valueOf(mr.isDeliveryPu()),
                    String.valueOf(mr.isBillable()),
                    String.valueOf(mr.isBench()),
                    String.valueOf(mr.isSupport()),
                    String.valueOf(mr.isLeadership()),
                    String.valueOf(mr.isManagement()),
                    mr.getReconciliationStatus().name(),
                    str(mr.getBillingCustomerCode()),
                    str(mr.getDataQualityFlags())));
        }
        return new BackupSheet(FILE_MASTER_RECORDS,
                new String[]{"period_month", "period_year", "period_version_number", "employee_id",
                        "practice_unit", "business_unit", "billable_status", "job_level", "gross_pay",
                        "total_employer_contributions",
                        "is_delivery_pu", "is_billable", "is_bench", "is_support", "is_leadership",
                        "is_management", "reconciliation_status", "billing_customer_code", "data_quality_flags"},
                rows);
    }

    @Transactional
    void wipePeopleData() {
        masterRecordRepository.deleteAllInBatch();
        payrollSnapshotRepository.deleteAllInBatch();
        peopleSnapshotRepository.deleteAllInBatch();
        snapshotUploadRepository.deleteAllInBatch();
        alternateIdLinkRepository.deleteAllInBatch();
        employeeRegistryRepository.deleteAllInBatch();
        periodVersionRepository.deleteAllInBatch();
        periodRepository.deleteAllInBatch();
        importColumnMappingLineRepository.deleteAllInBatch();
        importColumnMappingRepository.deleteAllInBatch();
        classificationConfigRepository.deleteAllInBatch();
    }

    @Transactional
    Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(FILE_CLASSIFICATION,
                restoreClassification(rowsByFile.getOrDefault(FILE_CLASSIFICATION, List.of())));
        counts.put(FILE_MAPPINGS, restoreMappings(rowsByFile.getOrDefault(FILE_MAPPINGS, List.of())));
        counts.put(FILE_PERIODS, restorePeriods(rowsByFile.getOrDefault(FILE_PERIODS, List.of())));
        Map<PeriodVersionKey, PeriodVersion> versions = restorePeriodVersions(
                rowsByFile.getOrDefault(FILE_PERIOD_VERSIONS, List.of()));
        counts.put(FILE_PERIOD_VERSIONS, versions.size());
        counts.put(FILE_EMPLOYEE_REGISTRY,
                restoreEmployeeRegistry(rowsByFile.getOrDefault(FILE_EMPLOYEE_REGISTRY, List.of())));
        counts.put(FILE_ALTERNATE_IDS,
                restoreAlternateIds(rowsByFile.getOrDefault(FILE_ALTERNATE_IDS, List.of())));
        Map<UploadKey, SnapshotUpload> uploads = new HashMap<>();
        counts.put(FILE_PEOPLE_SNAPSHOTS,
                restorePeopleSnapshots(rowsByFile.getOrDefault(FILE_PEOPLE_SNAPSHOTS, List.of()), versions, uploads));
        counts.put(FILE_PAYROLL_SNAPSHOTS,
                restorePayrollSnapshots(rowsByFile.getOrDefault(FILE_PAYROLL_SNAPSHOTS, List.of()), versions, uploads));
        counts.put(FILE_MASTER_RECORDS,
                restoreMasterRecords(rowsByFile.getOrDefault(FILE_MASTER_RECORDS, List.of()), versions));
        return counts;
    }

    private int restoreClassification(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                ClassificationConfigType type = ClassificationConfigType.valueOf(requireCell(row, 0, "config_type"));
                String value = requireCell(row, 1, "value");
                classificationConfigRepository.save(ClassificationConfig.builder()
                        .configType(type).value(value).build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreMappings(List<String[]> rows) {
        record GroupKey(ImportType importType, String templateName) {}
        Map<GroupKey, List<String[]>> groups = new LinkedHashMap<>();
        for (String[] row : rows) {
            try {
                ImportType type = ImportType.valueOf(requireCell(row, 0, "import_type"));
                String name = requireCell(row, 1, "template_name");
                groups.computeIfAbsent(new GroupKey(type, name), k -> new ArrayList<>()).add(row);
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        int count = 0;
        for (var entry : groups.entrySet()) {
            try {
                ImportColumnMapping mapping = ImportColumnMapping.builder()
                        .importType(entry.getKey().importType())
                        .templateName(entry.getKey().templateName())
                        .active(true)
                        .build();
                for (String[] lineRow : entry.getValue()) {
                    mapping.getLines().add(ImportColumnMappingLine.builder()
                            .mapping(mapping)
                            .excelColumnName(requireCell(lineRow, 2, "excel_column_name"))
                            .systemAttribute(requireCell(lineRow, 3, "system_attribute"))
                            .build());
                }
                importColumnMappingRepository.save(mapping);
                count += entry.getValue().size();
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restorePeriods(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                if (periodRepository.findByPeriodMonthAndPeriodYear(month, year).isEmpty()) {
                    periodRepository.save(Period.builder().periodMonth(month).periodYear(year).build());
                    count++;
                }
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private Map<PeriodVersionKey, PeriodVersion> restorePeriodVersions(List<String[]> rows) {
        Map<PeriodVersionKey, PeriodVersion> result = new HashMap<>();
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                int versionNumber = parseIntRequired(cell(row, 2), "version_number");
                PeriodStatus status = PeriodStatus.valueOf(requireCell(row, 3, "status"));
                Instant finalisedAt = parseInstant(cell(row, 4), "finalised_at");
                String finalisedBy = cell(row, 5);

                Period period = periodRepository.findByPeriodMonthAndPeriodYear(month, year)
                        .orElseGet(() -> periodRepository.save(
                                Period.builder().periodMonth(month).periodYear(year).build()));
                PeriodVersion pv = PeriodVersion.builder()
                        .period(period)
                        .versionNumber(versionNumber)
                        .status(status)
                        .finalisedAt(finalisedAt)
                        .finalisedBy(finalisedBy)
                        .latestFinalised(status == PeriodStatus.FINALISED)
                        .createdBy(RESTORE_UPLOAD_BY)
                        .build();
                pv = periodVersionRepository.save(pv);
                result.put(new PeriodVersionKey(month, year, versionNumber), pv);
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return result;
    }

    private int restoreEmployeeRegistry(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                employeeRegistryRepository.save(EmployeeRegistry.builder()
                        .employeeId(requireCell(row, 0, "employee_id"))
                        .fullName(requireCell(row, 1, "full_name"))
                        .dateOfJoining(parseDate(cell(row, 2), "date_of_joining"))
                        .exitStatus(ExitStatus.valueOf(requireCell(row, 3, "exit_status")))
                        .exitDate(parseDate(cell(row, 4), "exit_date"))
                        .exitDatePrecision(cell(row, 5) != null
                                ? ExitDatePrecision.valueOf(cell(row, 5)) : null)
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreAlternateIds(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                String altNo = requireCell(row, 0, "alternate_employee_no");
                String employeeId = requireCell(row, 1, "employee_id");
                EmployeeRegistry registry = employeeRegistryRepository.findByEmployeeId(employeeId).orElse(null);
                if (registry == null) {
                    continue;
                }
                Instant mappedAt = parseInstant(cell(row, 3), "mapped_at");
                alternateIdLinkRepository.save(AlternateIdLink.builder()
                        .alternateEmployeeNo(altNo)
                        .employeeRegistry(registry)
                        .mappedBy(cell(row, 2) != null ? cell(row, 2) : RESTORE_UPLOAD_BY)
                        .mappedAt(mappedAt != null ? mappedAt : Instant.now())
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private PeriodVersion resolveVersion(Map<PeriodVersionKey, PeriodVersion> cache,
                                         int month, int year, int versionNumber) {
        PeriodVersionKey key = new PeriodVersionKey(month, year, versionNumber);
        PeriodVersion cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Period period = periodRepository.findByPeriodMonthAndPeriodYear(month, year).orElse(null);
        if (period == null) {
            return null;
        }
        return periodVersionRepository.findByPeriodIdAndVersionNumber(period.getId(), versionNumber).orElse(null);
    }

    private SnapshotUpload resolveUpload(Map<PeriodVersionKey, PeriodVersion> versions,
                                         Map<UploadKey, SnapshotUpload> uploads,
                                         int month, int year, int versionNumber, ImportType importType) {
        PeriodVersion pv = resolveVersion(versions, month, year, versionNumber);
        if (pv == null) {
            return null;
        }
        UploadKey key = new UploadKey(month, year, versionNumber, importType);
        return uploads.computeIfAbsent(key, k -> snapshotUploadRepository.save(SnapshotUpload.builder()
                .periodVersion(pv)
                .importType(importType)
                .uploadedBy(RESTORE_UPLOAD_BY)
                .originalFilename(RESTORE_UPLOAD_FILENAME)
                .rowCount(0)
                .build()));
    }

    private int restorePeopleSnapshots(List<String[]> rows,
                                       Map<PeriodVersionKey, PeriodVersion> versions,
                                       Map<UploadKey, SnapshotUpload> uploads) {
        int count = 0;
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                int versionNumber = parseIntRequired(cell(row, 2), "period_version_number");
                ImportType importType = ImportType.valueOf(requireCell(row, 3, "import_type"));
                PeriodVersion pv = resolveVersion(versions, month, year, versionNumber);
                SnapshotUpload upload = resolveUpload(versions, uploads, month, year, versionNumber, importType);
                if (pv == null || upload == null) {
                    continue;
                }
                peopleSnapshotRepository.save(PeopleSnapshot.builder()
                        .periodVersion(pv)
                        .snapshotUpload(upload)
                        .employeeId(requireCell(row, 4, "employee_id"))
                        .fullName(requireCell(row, 5, "full_name"))
                        .practiceUnit(requireCell(row, 6, "practice_unit"))
                        .businessUnit(requireCell(row, 7, "business_unit"))
                        .buCode(cell(row, 8))
                        .projectCode(cell(row, 9))
                        .billableStatus(requireCell(row, 10, "billable_status"))
                        .jobLevel(cell(row, 11))
                        .jobSubLevel(cell(row, 12))
                        .title(cell(row, 13))
                        .dateOfJoining(parseDate(cell(row, 14), "date_of_joining"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restorePayrollSnapshots(List<String[]> rows,
                                          Map<PeriodVersionKey, PeriodVersion> versions,
                                          Map<UploadKey, SnapshotUpload> uploads) {
        int count = 0;
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                int versionNumber = parseIntRequired(cell(row, 2), "period_version_number");
                ImportType importType = ImportType.valueOf(requireCell(row, 3, "import_type"));
                PeriodVersion pv = resolveVersion(versions, month, year, versionNumber);
                SnapshotUpload upload = resolveUpload(versions, uploads, month, year, versionNumber, importType);
                if (pv == null || upload == null) {
                    continue;
                }
                payrollSnapshotRepository.save(PayrollSnapshot.builder()
                        .periodVersion(pv)
                        .snapshotUpload(upload)
                        .importType(importType)
                        .employeeNo(requireCell(row, 4, "employee_no"))
                        .fullName(requireCell(row, 5, "full_name"))
                        .grossPay(parseDecimalRequired(cell(row, 6), "gross_pay"))
                        .netPay(parseDecimalRequired(cell(row, 7), "net_pay"))
                        .ctcPerAnnum(parseDecimal(cell(row, 8), "ctc_per_annum"))
                        .epfContribution(parseDecimal(cell(row, 9), "epf_contribution"))
                        .epsContribution(parseDecimal(cell(row, 10), "eps_contribution"))
                        .edliContribution(parseDecimal(cell(row, 11), "edli_contribution"))
                        .epfAdminCharges(parseDecimal(cell(row, 12), "epf_admin_charges"))
                        .vpf(parseDecimal(cell(row, 13), "vpf"))
                        .npsDeduction(parseDecimal(cell(row, 14), "nps_deduction"))
                        .gratuity(parseDecimal(cell(row, 15), "gratuity"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreMasterRecords(List<String[]> rows, Map<PeriodVersionKey, PeriodVersion> versions) {
        int count = 0;
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                int versionNumber = parseIntRequired(cell(row, 2), "period_version_number");
                String employeeId = requireCell(row, 3, "employee_id");
                PeriodVersion pv = resolveVersion(versions, month, year, versionNumber);
                EmployeeRegistry registry = employeeRegistryRepository.findByEmployeeId(employeeId).orElse(null);
                if (pv == null || registry == null) {
                    continue;
                }
                masterRecordRepository.save(MasterRecord.builder()
                        .periodVersion(pv)
                        .employeeRegistry(registry)
                        .practiceUnit(cell(row, 4))
                        .businessUnit(cell(row, 5))
                        .billableStatus(cell(row, 6))
                        .jobLevel(cell(row, 7))
                        .grossPay(parseDecimal(cell(row, 8), "gross_pay"))
                        .totalEmployerContributions(parseDecimal(cell(row, 9), "total_employer_contributions"))
                        .deliveryPu(parseBoolean(cell(row, 10)))
                        .billable(parseBoolean(cell(row, 11)))
                        .bench(parseBoolean(cell(row, 12)))
                        .support(parseBoolean(cell(row, 13)))
                        .leadership(parseBoolean(cell(row, 14)))
                        .management(parseBoolean(cell(row, 15)))
                        .reconciliationStatus(ReconciliationStatus.valueOf(requireCell(row, 16, "reconciliation_status")))
                        .billingCustomerCode(cell(row, 17))
                        .dataQualityFlags(cell(row, 18))
                        .builtBy(RESTORE_UPLOAD_BY)
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private record PeriodVersionKey(int month, int year, int versionNumber) {}
    private record UploadKey(int month, int year, int versionNumber, ImportType importType) {}
}
