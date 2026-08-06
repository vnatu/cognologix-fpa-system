package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.dto.BudgetingDtos.*;
import com.cognologix.fpa.budgeting.dto.PlanInputImportResponse;
import com.cognologix.fpa.budgeting.repository.*;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.customer.CustomerService.BuCustomerRef;
import com.cognologix.fpa.customer.CustomerService.CustomerRef;
import com.cognologix.fpa.expenses.ExpenseService;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.people.PeoplePayrollService.MasterRecordFact;
import com.cognologix.fpa.people.PeriodFinalisedEvent;
import com.cognologix.fpa.revenue.RevenueService;
import com.cognologix.fpa.revenue.dto.RevenueDtos.MonthlyRevenueSummary;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Public API surface for the Budgeting &amp; Forecasting module (ADR-037).
 * Controllers and other modules call this class only — never sub-package types directly (ADR-008).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetingService {

    private static final Pattern FISCAL_YEAR_PATTERN = Pattern.compile("^FY(\\d{2})(\\d{2})$");
    private static final List<String> SEEDED_FORECAST_TYPES =
            List.of(ForecastType.NORMAL, ForecastType.AGGRESSIVE, ForecastType.CONSERVATIVE);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal STATUTORY_RATE = new BigDecimal("0.13");
    private static final BigDecimal VARIABLE_PAY_RATE = new BigDecimal("0.30");
    private static final Set<Integer> VARIABLE_PAY_MONTHS = Set.of(6, 9, 12, 3);
    private static final Set<String> DELIVERY_OVERHEAD_LINES = Set.of("training_upskilling", "subcontractors");
    private static final Set<String> DIRECT_OVERHEAD_LINES = Set.of("staff_medical", "staff_welfare",
            "computer_consumables", "subscription_software", "training_upskilling");

    private final FinancialYearPlanRepository financialYearPlanRepository;
    private final ForecastTypeRepository forecastTypeRepository;
    private final ForecastVersionRepository forecastVersionRepository;
    private final PeriodActualsRepository periodActualsRepository;
    private final PeriodBuActualsRepository periodBuActualsRepository;
    private final HcPlanRepository hcPlanRepository;
    private final SalaryBudgetRepository salaryBudgetRepository;
    private final ClientRevenuePlanRepository clientRevenuePlanRepository;
    private final OverheadBudgetRepository overheadBudgetRepository;
    private final ClientRevenueActualRepository clientRevenueActualRepository;
    private final OverheadActualsRepository overheadActualsRepository;
    private final OverheadLineItemRepository overheadLineItemRepository;
    private final CustomerService customerService;
    private final RevenueService revenueService;
    private final ExpenseService expenseService;
    private final PeoplePayrollService peoplePayrollService;
    private final BudgetingExcelIO budgetingExcelIO;
    private final BudgetingModuleBackup budgetingModuleBackup;

    public static final String REVENUE_SOURCE_MODULE = "REVENUE_MODULE";
    public static final String REVENUE_SOURCE_MANUAL = "MANUAL_OVERRIDE";

    @Transactional
    public FinancialYearPlan createFinancialYearPlan(String fiscalYear, int openingHc) {
        return createFinancialYearPlan(fiscalYear, openingHc, "system", null, null);
    }

    @Transactional
    public FinancialYearPlan createFinancialYearPlan(String fiscalYear, int openingHc, String createdBy) {
        return createFinancialYearPlan(fiscalYear, openingHc, createdBy, null, null);
    }

    @Transactional
    public FinancialYearPlan createFinancialYearPlan(
            String fiscalYear,
            int openingHc,
            String createdBy,
            LocalDate fiscalYearStartOverride,
            LocalDate fiscalYearEndOverride) {
        if (fiscalYear == null || fiscalYear.isBlank()) {
            throw new IllegalArgumentException("fiscalYear is required");
        }
        Matcher matcher = FISCAL_YEAR_PATTERN.matcher(fiscalYear.trim().toUpperCase());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "fiscalYear must match FYxxxx pattern (e.g. FY2627), got: " + fiscalYear);
        }
        if (openingHc < 0) {
            throw new IllegalArgumentException("openingHc must be >= 0");
        }
        String normalized = fiscalYear.trim().toUpperCase();
        if (financialYearPlanRepository.existsByFiscalYear(normalized)) {
            throw new IllegalArgumentException("Financial year plan already exists: " + normalized);
        }

        int startYear = 2000 + Integer.parseInt(matcher.group(1));
        int endYear = 2000 + Integer.parseInt(matcher.group(2));
        if (endYear != startYear + 1) {
            throw new IllegalArgumentException(
                    "fiscalYear end must be start+1 (e.g. FY2627), got: " + normalized);
        }

        LocalDate derivedStart = LocalDate.of(startYear, 4, 1);
        LocalDate derivedEnd = LocalDate.of(endYear, 3, 31);
        LocalDate fiscalYearStart = fiscalYearStartOverride != null ? fiscalYearStartOverride : derivedStart;
        LocalDate fiscalYearEnd = fiscalYearEndOverride != null ? fiscalYearEndOverride : derivedEnd;
        if (!fiscalYearEnd.isAfter(fiscalYearStart)) {
            throw new IllegalArgumentException(
                    "fiscalYearEnd must be after fiscalYearStart");
        }

        FinancialYearPlan plan = FinancialYearPlan.builder()
                .fiscalYear(normalized)
                .fiscalYearStart(fiscalYearStart)
                .fiscalYearEnd(fiscalYearEnd)
                .openingHc(openingHc)
                .createdBy(createdBy)
                .build();

        for (String typeName : SEEDED_FORECAST_TYPES) {
            ForecastType type = ForecastType.builder()
                    .financialYearPlan(plan)
                    .typeName(typeName)
                    .primary(ForecastType.NORMAL.equals(typeName))
                    .build();
            ForecastVersion draftV1 = ForecastVersion.builder()
                    .forecastType(type)
                    .versionNumber(1)
                    .status(ForecastVersionStatus.DRAFT)
                    .createdBy(createdBy)
                    .build();
            type.getVersions().add(draftV1);
            plan.getForecastTypes().add(type);
        }

        FinancialYearPlan saved = financialYearPlanRepository.save(plan);
        Hibernate.initialize(saved.getForecastTypes());
        saved.getForecastTypes().forEach(t -> Hibernate.initialize(t.getVersions()));
        return saved;
    }

    @Transactional
    public ForecastVersion publishForecastVersion(UUID forecastVersionId) {
        return publishForecastVersion(forecastVersionId, "system");
    }

    @Transactional
    public ForecastVersion publishForecastVersion(UUID forecastVersionId, String publishedBy) {
        ForecastVersion version = forecastVersionRepository.findById(forecastVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Forecast version not found: " + forecastVersionId));
        if (version.getStatus() != ForecastVersionStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT versions can be published, was " + version.getStatus());
        }

        Instant now = Instant.now();
        UUID forecastTypeId = version.getForecastType().getId();
        forecastVersionRepository.findByForecastTypeIdAndStatus(forecastTypeId, ForecastVersionStatus.ACTIVE)
                .ifPresent(prior -> {
                    prior.setStatus(ForecastVersionStatus.SUPERSEDED);
                    prior.setSupersededAt(now);
                    prior.setSupersededBy(publishedBy);
                    forecastVersionRepository.save(prior);
                });

        version.setStatus(ForecastVersionStatus.ACTIVE);
        version.setPublishedAt(now);
        version.setPublishedBy(publishedBy);
        return forecastVersionRepository.save(version);
    }

    @Transactional
    public ForecastVersion publishForecastVersion(UUID planId, UUID typeId, UUID versionId, String publishedBy) {
        ForecastVersion version = forecastVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Forecast version not found: " + versionId));
        if (!version.getForecastType().getId().equals(typeId)) {
            throw new IllegalArgumentException("Version does not belong to the specified forecast type");
        }
        if (!version.getForecastType().getFinancialYearPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Forecast type does not belong to the specified plan");
        }
        return publishForecastVersion(versionId, publishedBy);
    }

    @Transactional
    public ForecastVersion createDraftVersion(UUID forecastTypeId) {
        return createDraftVersion(forecastTypeId, "system");
    }

    @Transactional
    public ForecastVersion createDraftVersion(UUID forecastTypeId, String createdBy) {
        ForecastType type = forecastTypeRepository.findById(forecastTypeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Forecast type not found: " + forecastTypeId));
        if (forecastVersionRepository.findByForecastTypeIdAndStatus(forecastTypeId, ForecastVersionStatus.DRAFT)
                .isPresent()) {
            throw new IllegalStateException(
                    "A DRAFT version already exists for forecast type: " + type.getTypeName());
        }
        int nextNumber = forecastVersionRepository.findByForecastTypeIdOrderByVersionNumberAsc(forecastTypeId)
                .stream()
                .mapToInt(ForecastVersion::getVersionNumber)
                .max()
                .orElse(0) + 1;

        ForecastVersion draft = ForecastVersion.builder()
                .forecastType(type)
                .versionNumber(nextNumber)
                .status(ForecastVersionStatus.DRAFT)
                .createdBy(createdBy)
                .build();
        ForecastVersion saved = forecastVersionRepository.save(draft);

        Optional<ForecastVersion> activeVersionOpt = forecastVersionRepository
                .findByForecastTypeIdAndStatus(forecastTypeId, ForecastVersionStatus.ACTIVE);
        if (activeVersionOpt.isPresent()) {
            copyPlanInputs(activeVersionOpt.get().getId(), saved.getId());
        }

        return saved;
    }

    @Transactional
    public ForecastVersion createDraftVersion(UUID planId, UUID typeId, String createdBy) {
        ForecastType type = forecastTypeRepository.findById(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Forecast type not found: " + typeId));
        if (!type.getFinancialYearPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Forecast type does not belong to the specified plan");
        }
        return createDraftVersion(typeId, createdBy);
    }

    private void copyPlanInputs(UUID sourceVersionId, UUID targetVersionId) {
        ForecastVersion target = forecastVersionRepository.findById(targetVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Target version not found"));

        List<HcPlan> sourceHc = hcPlanRepository.findByForecastVersionId(sourceVersionId);
        for (HcPlan src : sourceHc) {
            HcPlan copy = HcPlan.builder()
                    .forecastVersion(target)
                    .planMonth(src.getPlanMonth())
                    .planYear(src.getPlanYear())
                    .plannedHires(src.getPlannedHires())
                    .plannedExits(src.getPlannedExits())
                    .plannedBillableHc(src.getPlannedBillableHc())
                    .plannedBenchHc(src.getPlannedBenchHc())
                    .plannedSupportHc(src.getPlannedSupportHc())
                    .plannedLeadershipHc(src.getPlannedLeadershipHc())
                    .plannedManagementHc(src.getPlannedManagementHc())
                    .build();
            hcPlanRepository.save(copy);
        }

        List<SalaryBudget> sourceSalary = salaryBudgetRepository.findByForecastVersionId(sourceVersionId);
        for (SalaryBudget src : sourceSalary) {
            SalaryBudget copy = SalaryBudget.builder()
                    .forecastVersion(target)
                    .planMonth(src.getPlanMonth())
                    .planYear(src.getPlanYear())
                    .billableSalaries(src.getBillableSalaries())
                    .benchSalaries(src.getBenchSalaries())
                    .supportSalaries(src.getSupportSalaries())
                    .cofoundersSalaries(src.getCofoundersSalaries())
                    .seniorMgmtSalaries(src.getSeniorMgmtSalaries())
                    .build();
            salaryBudgetRepository.save(copy);
        }

        List<ClientRevenuePlan> sourceRevenue = clientRevenuePlanRepository.findByForecastVersionId(sourceVersionId);
        for (ClientRevenuePlan src : sourceRevenue) {
            ClientRevenuePlan copy = ClientRevenuePlan.builder()
                    .forecastVersion(target)
                    .customerId(src.getCustomerId())
                    .planMonth(src.getPlanMonth())
                    .planYear(src.getPlanYear())
                    .plannedTmRevenue(src.getPlannedTmRevenue())
                    .plannedFixedBidRevenue(src.getPlannedFixedBidRevenue())
                    .build();
            clientRevenuePlanRepository.save(copy);
        }

        List<OverheadBudget> sourceOverhead = overheadBudgetRepository.findByForecastVersionId(sourceVersionId);
        for (OverheadBudget src : sourceOverhead) {
            OverheadBudget copy = OverheadBudget.builder()
                    .forecastVersion(target)
                    .planMonth(src.getPlanMonth())
                    .planYear(src.getPlanYear())
                    .overheadLine(src.getOverheadLine())
                    .amount(src.getAmount())
                    .build();
            overheadBudgetRepository.save(copy);
        }
    }

    public Optional<ForecastVersion> getActiveBaseline(UUID financialYearPlanId) {
        return forecastTypeRepository.findByFinancialYearPlanIdAndPrimaryTrue(financialYearPlanId)
                .flatMap(type -> forecastVersionRepository.findByForecastTypeIdAndStatus(
                        type.getId(), ForecastVersionStatus.ACTIVE));
    }

    public List<FinancialYearPlan> listFinancialYearPlans() {
        return financialYearPlanRepository.findAll();
    }

    public Optional<FinancialYearPlan> getFinancialYearPlan(UUID planId) {
        Optional<FinancialYearPlan> planOpt = financialYearPlanRepository.findById(planId);
        planOpt.ifPresent(plan -> {
            Hibernate.initialize(plan.getForecastTypes());
            plan.getForecastTypes().forEach(type -> Hibernate.initialize(type.getVersions()));
        });
        return planOpt;
    }

    public List<ForecastType> listForecastTypes(UUID planId) {
        return forecastTypeRepository.findByFinancialYearPlanId(planId);
    }

    @Transactional
    public void upsertHcPlan(UUID planId, UUID typeId, UUID versionId, List<HcPlan> hcPlans) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        Map<MonthYearKey, HcPlan> existingByKey = hcPlanRepository.findByForecastVersionId(versionId)
                .stream()
                .collect(Collectors.toMap(
                        p -> new MonthYearKey(p.getPlanMonth(), p.getPlanYear()),
                        p -> p,
                        (a, b) -> a,
                        LinkedHashMap::new));

        Set<MonthYearKey> kept = new HashSet<>();
        for (HcPlan incoming : hcPlans) {
            MonthYearKey key = new MonthYearKey(incoming.getPlanMonth(), incoming.getPlanYear());
            kept.add(key);
            HcPlan target = existingByKey.get(key);
            if (target == null) {
                incoming.setId(null);
                incoming.setForecastVersion(version);
                hcPlanRepository.save(incoming);
            } else {
                target.setPlannedHires(incoming.getPlannedHires());
                target.setPlannedExits(incoming.getPlannedExits());
                target.setPlannedBillableHc(incoming.getPlannedBillableHc());
                target.setPlannedBenchHc(incoming.getPlannedBenchHc());
                target.setPlannedSupportHc(incoming.getPlannedSupportHc());
                target.setPlannedLeadershipHc(incoming.getPlannedLeadershipHc());
                target.setPlannedManagementHc(incoming.getPlannedManagementHc());
                hcPlanRepository.save(target);
            }
        }
        existingByKey.forEach((key, row) -> {
            if (!kept.contains(key)) {
                hcPlanRepository.delete(row);
            }
        });
    }

    @Transactional
    public void upsertSalaryBudget(UUID planId, UUID typeId, UUID versionId, List<SalaryBudget> salaryBudgets) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        Map<MonthYearKey, SalaryBudget> existingByKey = salaryBudgetRepository
                .findByForecastVersionId(versionId)
                .stream()
                .collect(Collectors.toMap(
                        b -> new MonthYearKey(b.getPlanMonth(), b.getPlanYear()),
                        b -> b,
                        (a, b) -> a,
                        LinkedHashMap::new));

        Set<MonthYearKey> kept = new HashSet<>();
        for (SalaryBudget incoming : salaryBudgets) {
            MonthYearKey key = new MonthYearKey(incoming.getPlanMonth(), incoming.getPlanYear());
            kept.add(key);
            SalaryBudget target = existingByKey.get(key);
            if (target == null) {
                incoming.setId(null);
                incoming.setForecastVersion(version);
                salaryBudgetRepository.save(incoming);
            } else {
                target.setBillableSalaries(incoming.getBillableSalaries());
                target.setBenchSalaries(incoming.getBenchSalaries());
                target.setSupportSalaries(incoming.getSupportSalaries());
                target.setCofoundersSalaries(incoming.getCofoundersSalaries());
                target.setSeniorMgmtSalaries(incoming.getSeniorMgmtSalaries());
                salaryBudgetRepository.save(target);
            }
        }
        existingByKey.forEach((key, row) -> {
            if (!kept.contains(key)) {
                salaryBudgetRepository.delete(row);
            }
        });
    }

    @Transactional
    public void upsertRevenuePlan(UUID planId, UUID typeId, UUID versionId, List<ClientRevenuePlan> revenuePlans) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        Map<CustomerMonthYearKey, ClientRevenuePlan> existingByKey = clientRevenuePlanRepository
                .findByForecastVersionId(versionId)
                .stream()
                .collect(Collectors.toMap(
                        p -> new CustomerMonthYearKey(p.getCustomerId(), p.getPlanMonth(), p.getPlanYear()),
                        p -> p,
                        (a, b) -> a,
                        LinkedHashMap::new));

        Set<CustomerMonthYearKey> kept = new HashSet<>();
        for (ClientRevenuePlan incoming : revenuePlans) {
            CustomerMonthYearKey key = new CustomerMonthYearKey(
                    incoming.getCustomerId(), incoming.getPlanMonth(), incoming.getPlanYear());
            kept.add(key);
            ClientRevenuePlan target = existingByKey.get(key);
            if (target == null) {
                incoming.setId(null);
                incoming.setForecastVersion(version);
                clientRevenuePlanRepository.save(incoming);
            } else {
                target.setPlannedTmRevenue(incoming.getPlannedTmRevenue());
                target.setPlannedFixedBidRevenue(incoming.getPlannedFixedBidRevenue());
                clientRevenuePlanRepository.save(target);
            }
        }
        existingByKey.forEach((key, row) -> {
            if (!kept.contains(key)) {
                clientRevenuePlanRepository.delete(row);
            }
        });
    }

    @Transactional
    public void upsertOverheadBudget(UUID planId, UUID typeId, UUID versionId, List<OverheadBudget> overheadBudgets) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        Map<OverheadMonthYearKey, OverheadBudget> existingByKey = overheadBudgetRepository
                .findByForecastVersionId(versionId)
                .stream()
                .collect(Collectors.toMap(
                        b -> new OverheadMonthYearKey(b.getPlanMonth(), b.getPlanYear(), b.getOverheadLine()),
                        b -> b,
                        (a, b) -> a,
                        LinkedHashMap::new));

        Set<OverheadMonthYearKey> kept = new HashSet<>();
        for (OverheadBudget incoming : overheadBudgets) {
            OverheadMonthYearKey key = new OverheadMonthYearKey(
                    incoming.getPlanMonth(), incoming.getPlanYear(), incoming.getOverheadLine());
            kept.add(key);
            OverheadBudget target = existingByKey.get(key);
            if (target == null) {
                incoming.setId(null);
                incoming.setForecastVersion(version);
                overheadBudgetRepository.save(incoming);
            } else {
                target.setAmount(incoming.getAmount());
                overheadBudgetRepository.save(target);
            }
        }
        existingByKey.forEach((key, row) -> {
            if (!kept.contains(key)) {
                overheadBudgetRepository.delete(row);
            }
        });
    }

    private ForecastVersion validateDraftVersion(UUID planId, UUID typeId, UUID versionId) {
        ForecastVersion version = forecastVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));
        if (version.getStatus() != ForecastVersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT versions can be modified");
        }
        if (!version.getForecastType().getId().equals(typeId)) {
            throw new IllegalArgumentException("Version does not belong to the specified type");
        }
        if (!version.getForecastType().getFinancialYearPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Type does not belong to the specified plan");
        }
        return version;
    }

    private ForecastVersion validateVersionBelongs(UUID planId, UUID typeId, UUID versionId) {
        ForecastVersion version = forecastVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));
        if (!version.getForecastType().getId().equals(typeId)) {
            throw new IllegalArgumentException("Version does not belong to the specified type");
        }
        if (!version.getForecastType().getFinancialYearPlan().getId().equals(planId)) {
            throw new IllegalArgumentException("Type does not belong to the specified plan");
        }
        return version;
    }

    public List<HcPlan> getHcPlan(UUID planId, UUID typeId, UUID versionId) {
        validateVersionBelongs(planId, typeId, versionId);
        return hcPlanRepository.findByForecastVersionId(versionId);
    }

    public List<SalaryBudget> getSalaryBudget(UUID planId, UUID typeId, UUID versionId) {
        validateVersionBelongs(planId, typeId, versionId);
        return salaryBudgetRepository.findByForecastVersionId(versionId);
    }

    public List<ClientRevenuePlan> getRevenuePlan(UUID planId, UUID typeId, UUID versionId) {
        validateVersionBelongs(planId, typeId, versionId);
        return clientRevenuePlanRepository.findByForecastVersionId(versionId);
    }

    public List<OverheadBudget> getOverheadBudget(UUID planId, UUID typeId, UUID versionId) {
        validateVersionBelongs(planId, typeId, versionId);
        return overheadBudgetRepository.findByForecastVersionId(versionId);
    }

    public byte[] exportHcPlan(UUID planId, UUID typeId, UUID versionId) {
        return budgetingExcelIO.exportHcPlan(getHcPlan(planId, typeId, versionId));
    }

    public byte[] exportSalaryBudget(UUID planId, UUID typeId, UUID versionId) {
        return budgetingExcelIO.exportSalaryBudget(getSalaryBudget(planId, typeId, versionId));
    }

    public byte[] exportRevenuePlan(UUID planId, UUID typeId, UUID versionId) {
        return budgetingExcelIO.exportRevenuePlan(getRevenuePlan(planId, typeId, versionId));
    }

    public byte[] exportOverheadBudget(UUID planId, UUID typeId, UUID versionId) {
        return budgetingExcelIO.exportOverheadBudget(getOverheadBudget(planId, typeId, versionId));
    }

    public byte[] exportAllInputs(UUID planId, UUID typeId, UUID versionId) {
        return budgetingExcelIO.zipPlanInputs(
                exportHcPlan(planId, typeId, versionId),
                exportSalaryBudget(planId, typeId, versionId),
                exportRevenuePlan(planId, typeId, versionId),
                exportOverheadBudget(planId, typeId, versionId));
    }

    public byte[] buildHcPlanImportSample() {
        return budgetingExcelIO.buildHcPlanSample();
    }

    public byte[] buildSalaryBudgetImportSample() {
        return budgetingExcelIO.buildSalaryBudgetSample();
    }

    public byte[] buildRevenuePlanImportSample() {
        return budgetingExcelIO.buildRevenuePlanSample();
    }

    public byte[] buildOverheadBudgetImportSample() {
        return budgetingExcelIO.buildOverheadBudgetSample();
    }

    @Transactional
    public PlanInputImportResponse importHcPlan(UUID planId, UUID typeId, UUID versionId, MultipartFile file) {
        BudgetingExcelIO.HcPlanParseResult parsed = budgetingExcelIO.parseHcPlan(file);
        Map<MonthYearKey, HcPlan> merged = new LinkedHashMap<>();
        for (HcPlan existing : getHcPlan(planId, typeId, versionId)) {
            merged.put(new MonthYearKey(existing.getPlanMonth(), existing.getPlanYear()), existing);
        }
        int created = 0;
        int skipped = 0;
        for (HcPlan imported : parsed.rows()) {
            MonthYearKey key = new MonthYearKey(imported.getPlanMonth(), imported.getPlanYear());
            if (merged.containsKey(key)) {
                skipped++;
            } else {
                created++;
            }
            merged.put(key, imported);
        }
        if (!parsed.rows().isEmpty()) {
            upsertHcPlan(planId, typeId, versionId, new ArrayList<>(merged.values()));
        }
        return new PlanInputImportResponse(parsed.totalRows(), created, skipped, parsed.errors());
    }

    @Transactional
    public PlanInputImportResponse importSalaryBudget(UUID planId, UUID typeId, UUID versionId, MultipartFile file) {
        BudgetingExcelIO.SalaryBudgetParseResult parsed = budgetingExcelIO.parseSalaryBudget(file);
        Map<MonthYearKey, SalaryBudget> merged = new LinkedHashMap<>();
        for (SalaryBudget existing : getSalaryBudget(planId, typeId, versionId)) {
            merged.put(new MonthYearKey(existing.getPlanMonth(), existing.getPlanYear()), existing);
        }
        int created = 0;
        int skipped = 0;
        for (SalaryBudget imported : parsed.rows()) {
            MonthYearKey key = new MonthYearKey(imported.getPlanMonth(), imported.getPlanYear());
            if (merged.containsKey(key)) {
                skipped++;
            } else {
                created++;
            }
            merged.put(key, imported);
        }
        if (!parsed.rows().isEmpty()) {
            upsertSalaryBudget(planId, typeId, versionId, new ArrayList<>(merged.values()));
        }
        return new PlanInputImportResponse(parsed.totalRows(), created, skipped, parsed.errors());
    }

    @Transactional
    public PlanInputImportResponse importRevenuePlan(UUID planId, UUID typeId, UUID versionId, MultipartFile file) {
        BudgetingExcelIO.RevenuePlanParseResult parsed = budgetingExcelIO.parseRevenuePlan(file);
        Map<CustomerMonthYearKey, ClientRevenuePlan> merged = new LinkedHashMap<>();
        for (ClientRevenuePlan existing : getRevenuePlan(planId, typeId, versionId)) {
            merged.put(new CustomerMonthYearKey(
                    existing.getCustomerId(), existing.getPlanMonth(), existing.getPlanYear()), existing);
        }
        int created = 0;
        int skipped = 0;
        for (ClientRevenuePlan imported : parsed.rows()) {
            CustomerMonthYearKey key = new CustomerMonthYearKey(
                    imported.getCustomerId(), imported.getPlanMonth(), imported.getPlanYear());
            if (merged.containsKey(key)) {
                skipped++;
            } else {
                created++;
            }
            merged.put(key, imported);
        }
        if (!parsed.rows().isEmpty()) {
            upsertRevenuePlan(planId, typeId, versionId, new ArrayList<>(merged.values()));
        }
        return new PlanInputImportResponse(parsed.totalRows(), created, skipped, parsed.errors());
    }

    @Transactional
    public PlanInputImportResponse importOverheadBudget(UUID planId, UUID typeId, UUID versionId, MultipartFile file) {
        BudgetingExcelIO.OverheadBudgetParseResult parsed = budgetingExcelIO.parseOverheadBudget(file);
        Map<OverheadMonthYearKey, OverheadBudget> merged = new LinkedHashMap<>();
        for (OverheadBudget existing : getOverheadBudget(planId, typeId, versionId)) {
            merged.put(new OverheadMonthYearKey(
                    existing.getPlanMonth(), existing.getPlanYear(), existing.getOverheadLine()), existing);
        }
        int created = 0;
        int skipped = 0;
        for (OverheadBudget imported : parsed.rows()) {
            OverheadMonthYearKey key = new OverheadMonthYearKey(
                    imported.getPlanMonth(), imported.getPlanYear(), imported.getOverheadLine());
            if (merged.containsKey(key)) {
                skipped++;
            } else {
                created++;
            }
            merged.put(key, imported);
        }
        if (!parsed.rows().isEmpty()) {
            upsertOverheadBudget(planId, typeId, versionId, new ArrayList<>(merged.values()));
        }
        return new PlanInputImportResponse(parsed.totalRows(), created, skipped, parsed.errors());
    }

    private record MonthYearKey(int month, int year) {}
    private record CustomerMonthYearKey(UUID customerId, int month, int year) {}
    private record OverheadMonthYearKey(int month, int year, String overheadLine) {}

    public List<OverheadLineItem> listOverheadLineItems() {
        return overheadLineItemRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public PeriodActuals upsertRevenueActuals(UUID planId, int month, int year, BigDecimal manualTotal,
                                              List<ClientRevenueActual> byClient, String enteredBy) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        PeriodActuals actuals = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year)
                .orElseGet(() -> {
                    PeriodActuals newActuals = PeriodActuals.builder()
                            .financialYearPlan(plan)
                            .actualsMonth(month)
                            .actualsYear(year)
                            .build();
                    return periodActualsRepository.save(newActuals);
                });

        actuals.setActualRevenueManual(manualTotal);
        PeriodActuals saved = periodActualsRepository.save(actuals);

        Map<UUID, ClientRevenueActual> existingByCustomer = clientRevenueActualRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year)
                .stream()
                .collect(Collectors.toMap(ClientRevenueActual::getCustomerId, a -> a, (a, b) -> a));

        Set<UUID> keptCustomers = new HashSet<>();
        if (byClient != null) {
            for (ClientRevenueActual entry : byClient) {
                keptCustomers.add(entry.getCustomerId());
                ClientRevenueActual target = existingByCustomer.get(entry.getCustomerId());
                if (target == null) {
                    ClientRevenueActual actual = ClientRevenueActual.builder()
                            .financialYearPlan(plan)
                            .customerId(entry.getCustomerId())
                            .actualsMonth(month)
                            .actualsYear(year)
                            .actualRevenue(entry.getActualRevenue())
                            .enteredBy(enteredBy)
                            .build();
                    clientRevenueActualRepository.save(actual);
                } else {
                    target.setActualRevenue(entry.getActualRevenue());
                    target.setEnteredBy(enteredBy);
                    clientRevenueActualRepository.save(target);
                }
            }
        }
        existingByCustomer.forEach((customerId, row) -> {
            if (!keptCustomers.contains(customerId)) {
                clientRevenueActualRepository.delete(row);
            }
        });
        return saved;
    }

    @Transactional
    public void upsertOverheadActuals(UUID planId, int month, int year,
                                      List<OverheadActuals> lineItems, String enteredBy) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year)
                .orElseGet(() -> {
                    PeriodActuals newActuals = PeriodActuals.builder()
                            .financialYearPlan(plan)
                            .actualsMonth(month)
                            .actualsYear(year)
                            .build();
                    return periodActualsRepository.save(newActuals);
                });

        Map<String, OverheadActuals> existingByLine = overheadActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year)
                .stream()
                .collect(Collectors.toMap(OverheadActuals::getOverheadLine, o -> o, (a, b) -> a));

        Set<String> keptLines = new HashSet<>();
        if (lineItems != null) {
            for (OverheadActuals item : lineItems) {
                keptLines.add(item.getOverheadLine());
                OverheadActuals target = existingByLine.get(item.getOverheadLine());
                if (target == null) {
                    OverheadActuals overhead = OverheadActuals.builder()
                            .financialYearPlan(plan)
                            .actualsMonth(month)
                            .actualsYear(year)
                            .overheadLine(item.getOverheadLine())
                            .actualAmount(item.getActualAmount())
                            .enteredBy(enteredBy)
                            .build();
                    overheadActualsRepository.save(overhead);
                } else {
                    target.setActualAmount(item.getActualAmount());
                    target.setEnteredBy(enteredBy);
                    overheadActualsRepository.save(target);
                }
            }
        }
        existingByLine.forEach((line, row) -> {
            if (!keptLines.contains(line)) {
                overheadActualsRepository.delete(row);
            }
        });
    }

    public Optional<PeriodActuals> getPeriodActualsDetail(UUID planId, int month, int year) {
        return periodActualsRepository.findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);
    }

    public RollingForecastResult getRollingForecast(UUID financialYearPlanId) {
        return getRollingForecast(financialYearPlanId, PeriodGranularity.ANNUAL, null, null, null);
    }

    public RollingForecastResult getRollingForecast(
            UUID financialYearPlanId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(financialYearPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + financialYearPlanId));

        ForecastVersion baseline = getActiveBaseline(financialYearPlanId)
                .orElseThrow(() -> new IllegalStateException(
                        "No ACTIVE baseline (Normal) version for plan: " + financialYearPlanId));

        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        List<MonthlyFinancials> months = new ArrayList<>();
        for (LocalDate m = plan.getFiscalYearStart();
             !m.isAfter(plan.getFiscalYearEnd());
             m = m.plusMonths(1)) {

            Optional<PeriodActuals> actualsOpt = periodActualsRepository
                    .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                            financialYearPlanId, m.getMonthValue(), m.getYear());

            if (actualsOpt.isPresent()) {
                months.add(buildMonthlyFinancialsFromActuals(plan, actualsOpt.get(), m));
            } else {
                months.add(buildMonthlyFinancialsFromPlan(plan, baseline, m));
            }
        }

        // Chart always returns 12 months; granularity only drives highlight metadata (ADR-049).
        return new RollingForecastResult(
                financialYearPlanId,
                plan.getFiscalYear(),
                baseline.getId(),
                months,
                selection.granularity().name(),
                selection.periodLabel(),
                selection.highlightMonth(),
                selection.highlightYear(),
                selection.highlightQuarter());
    }

    /**
     * Delta = Rolling Forecast − Baseline (ACTIVE Normal).
     *
     * <p>Sign convention (for frontend traffic-lights):
     * <ul>
     *   <li>Revenue / Gross Profit / EBITDA: positive = above plan (good), negative = below plan (bad)</li>
     *   <li>Costs (Salary, Overhead, COGS, OpEx): positive = over-budget (bad), negative = under-budget (good)</li>
     * </ul>
     */
    public DeltaResult getDelta(UUID financialYearPlanId) {
        return getDelta(financialYearPlanId, PeriodGranularity.ANNUAL, null, null, null);
    }

    public DeltaResult getDelta(
            UUID financialYearPlanId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter) {
        RollingForecastResult rolling = getRollingForecast(
                financialYearPlanId, granularity, month, year, quarter);
        FinancialYearPlan plan = financialYearPlanRepository.findById(financialYearPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        ForecastVersion baseline = getActiveBaseline(financialYearPlanId)
                .orElseThrow(() -> new IllegalStateException("No ACTIVE baseline"));
        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        List<MonthlyFinancials> deltaMonths = new ArrayList<>();
        List<MonthlyFinancials> baselineMonths = new ArrayList<>();
        for (MonthlyFinancials rollingMonth : rolling.months()) {
            LocalDate monthDate = LocalDate.of(rollingMonth.year(), rollingMonth.month(), 1);
            MonthlyFinancials baselineMonth = buildMonthlyFinancialsFromPlan(plan, baseline, monthDate);
            baselineMonths.add(baselineMonth);

            HcFigures deltaHc = new HcFigures(
                    rollingMonth.hc().billableHc() - baselineMonth.hc().billableHc(),
                    rollingMonth.hc().benchHc() - baselineMonth.hc().benchHc(),
                    rollingMonth.hc().supportHc() - baselineMonth.hc().supportHc(),
                    rollingMonth.hc().leadershipHc() - baselineMonth.hc().leadershipHc(),
                    rollingMonth.hc().managementHc() - baselineMonth.hc().managementHc(),
                    rollingMonth.hc().totalHc() - baselineMonth.hc().totalHc()
            );

            SalaryFigures deltaSalary = new SalaryFigures(
                    subtract(rollingMonth.salary().billable(), baselineMonth.salary().billable()),
                    subtract(rollingMonth.salary().bench(), baselineMonth.salary().bench()),
                    subtract(rollingMonth.salary().support(), baselineMonth.salary().support()),
                    subtract(rollingMonth.salary().cofounders(), baselineMonth.salary().cofounders()),
                    subtract(rollingMonth.salary().seniorMgmt(), baselineMonth.salary().seniorMgmt()),
                    subtract(rollingMonth.salary().total(), baselineMonth.salary().total())
            );

            List<ClientRevenueFigures> deltaRevenue = computeDeltaRevenueByClient(
                    rollingMonth.revenueByClient(), baselineMonth.revenueByClient());

            List<OverheadLineFigures> deltaOverhead = computeDeltaOverheadLines(
                    rollingMonth.overhead(), baselineMonth.overhead());

            // Ratio delta = Actual% − Plan% (never ratio of HC deltas)
            BigDecimal billableRatioDelta = subtract(
                    rollingMonth.billableRatioPct(), baselineMonth.billableRatioPct());

            MonthlyFinancials delta = new MonthlyFinancials(
                    rollingMonth.month(),
                    rollingMonth.year(),
                    rollingMonth.fromActuals(),
                    deltaHc,
                    deltaSalary,
                    deltaRevenue,
                    subtract(rollingMonth.totalRevenue(), baselineMonth.totalRevenue()),
                    deltaOverhead,
                    subtract(rollingMonth.totalOverhead(), baselineMonth.totalOverhead()),
                    subtract(rollingMonth.totalSalaryCost(), baselineMonth.totalSalaryCost()),
                    subtract(rollingMonth.statutoryBenefits(), baselineMonth.statutoryBenefits()),
                    subtract(rollingMonth.variablePay(), baselineMonth.variablePay()),
                    subtract(rollingMonth.totalCogs(), baselineMonth.totalCogs()),
                    subtract(rollingMonth.grossProfit(), baselineMonth.grossProfit()),
                    subtract(rollingMonth.totalOpex(), baselineMonth.totalOpex()),
                    subtract(rollingMonth.ebitda(), baselineMonth.ebitda()),
                    billableRatioDelta
            );
            deltaMonths.add(delta);
        }

        List<MonthlyFinancials> inScope = filterMonths(deltaMonths, selection, false);
        List<MonthlyFinancials> rollingInScope = filterMonths(rolling.months(), selection, false);
        List<MonthlyFinancials> baselineInScope = filterMonths(baselineMonths, selection, false);
        MonthlyFinancials summed = sumMonthlyFinancials(inScope, selection);
        // Period ratio delta from period HC aggregates — not from summed HC deltas
        BigDecimal periodRatioDelta = subtract(
                billableRatioPct(sumHcFigures(rollingInScope)),
                billableRatioPct(sumHcFigures(baselineInScope)));
        MonthlyFinancials periodTotal = withBillableRatioPct(summed, periodRatioDelta);

        return new DeltaResult(
                financialYearPlanId,
                plan.getFiscalYear(),
                baseline.getId(),
                deltaMonths,
                selection.granularity().name(),
                selection.periodLabel(),
                periodTotal);
    }

    public PlanVsActualResult getPlanVsActual(UUID financialYearPlanId) {
        return getPlanVsActual(financialYearPlanId, null, PeriodGranularity.ANNUAL, null, null, null);
    }

    public PlanVsActualResult getPlanVsActual(UUID financialYearPlanId, UUID forecastTypeId) {
        return getPlanVsActual(financialYearPlanId, forecastTypeId, PeriodGranularity.ANNUAL, null, null, null);
    }

    public PlanVsActualResult getPlanVsActual(
            UUID financialYearPlanId,
            UUID forecastTypeId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(financialYearPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        ForecastVersion baseline;
        if (forecastTypeId != null) {
            baseline = forecastVersionRepository.findByForecastTypeIdAndStatus(forecastTypeId, ForecastVersionStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalStateException("No ACTIVE version for forecast type: " + forecastTypeId));
            if (!baseline.getForecastType().getFinancialYearPlan().getId().equals(financialYearPlanId)) {
                throw new IllegalArgumentException("Forecast type does not belong to the specified plan");
            }
        } else {
            baseline = getActiveBaseline(financialYearPlanId)
                    .orElseThrow(() -> new IllegalStateException("No ACTIVE baseline"));
        }

        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        List<MonthlyPlanVsActual> months = new ArrayList<>();
        MoneyTriad q1Rev = zero(), q2Rev = zero(), q3Rev = zero(), q4Rev = zero();
        MoneyTriad q1Sal = zero(), q2Sal = zero(), q3Sal = zero(), q4Sal = zero();
        MoneyTriad q1Ovh = zero(), q2Ovh = zero(), q3Ovh = zero(), q4Ovh = zero();
        MoneyTriad q1Cogs = zero(), q2Cogs = zero(), q3Cogs = zero(), q4Cogs = zero();
        MoneyTriad q1Gp = zero(), q2Gp = zero(), q3Gp = zero(), q4Gp = zero();
        MoneyTriad q1Ebitda = zero(), q2Ebitda = zero(), q3Ebitda = zero(), q4Ebitda = zero();

        for (LocalDate m = plan.getFiscalYearStart();
             !m.isAfter(plan.getFiscalYearEnd());
             m = m.plusMonths(1)) {

            MonthlyFinancials planMonth = buildMonthlyFinancialsFromPlan(plan, baseline, m);
            Optional<PeriodActuals> actualsOpt = periodActualsRepository
                    .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                            financialYearPlanId, m.getMonthValue(), m.getYear());

            MonthlyFinancials actualMonth = actualsOpt.isPresent()
                    ? buildMonthlyFinancialsFromActuals(plan, actualsOpt.get(), m)
                    : nullMonth(m);

            String revenueSource = actualsOpt.isPresent()
                    ? resolveActualRevenue(plan.getId(), m.getMonthValue(), m.getYear(), actualsOpt.get())
                            .source()
                    : null;

            MonthlyPlanVsActual pva = buildMonthlyPlanVsActual(
                    planMonth, actualMonth, actualsOpt.isPresent(), revenueSource);
            months.add(pva);

            int fiscalMonth = getFiscalMonth(m);
            if (fiscalMonth <= 3) {
                q1Rev = add(q1Rev, pva.totalRevenue());
                q1Sal = add(q1Sal, pva.totalSalaryCost());
                q1Ovh = add(q1Ovh, pva.totalOverhead());
                q1Cogs = add(q1Cogs, pva.totalCogs());
                q1Gp = add(q1Gp, pva.grossProfit());
                q1Ebitda = add(q1Ebitda, pva.ebitda());
            } else if (fiscalMonth <= 6) {
                q2Rev = add(q2Rev, pva.totalRevenue());
                q2Sal = add(q2Sal, pva.totalSalaryCost());
                q2Ovh = add(q2Ovh, pva.totalOverhead());
                q2Cogs = add(q2Cogs, pva.totalCogs());
                q2Gp = add(q2Gp, pva.grossProfit());
                q2Ebitda = add(q2Ebitda, pva.ebitda());
            } else if (fiscalMonth <= 9) {
                q3Rev = add(q3Rev, pva.totalRevenue());
                q3Sal = add(q3Sal, pva.totalSalaryCost());
                q3Ovh = add(q3Ovh, pva.totalOverhead());
                q3Cogs = add(q3Cogs, pva.totalCogs());
                q3Gp = add(q3Gp, pva.grossProfit());
                q3Ebitda = add(q3Ebitda, pva.ebitda());
            } else {
                q4Rev = add(q4Rev, pva.totalRevenue());
                q4Sal = add(q4Sal, pva.totalSalaryCost());
                q4Ovh = add(q4Ovh, pva.totalOverhead());
                q4Cogs = add(q4Cogs, pva.totalCogs());
                q4Gp = add(q4Gp, pva.grossProfit());
                q4Ebitda = add(q4Ebitda, pva.ebitda());
            }
        }

        PeriodTotals q1 = new PeriodTotals("Q1", q1Rev, q1Sal, q1Ovh, q1Cogs, q1Gp, q1Ebitda);
        PeriodTotals q2 = new PeriodTotals("Q2", q2Rev, q2Sal, q2Ovh, q2Cogs, q2Gp, q2Ebitda);
        PeriodTotals q3 = new PeriodTotals("Q3", q3Rev, q3Sal, q3Ovh, q3Cogs, q3Gp, q3Ebitda);
        PeriodTotals q4 = new PeriodTotals("Q4", q4Rev, q4Sal, q4Ovh, q4Cogs, q4Gp, q4Ebitda);
        PeriodTotals fy = new PeriodTotals("FY",
                add(add(add(q1Rev, q2Rev), q3Rev), q4Rev),
                add(add(add(q1Sal, q2Sal), q3Sal), q4Sal),
                add(add(add(q1Ovh, q2Ovh), q3Ovh), q4Ovh),
                add(add(add(q1Cogs, q2Cogs), q3Cogs), q4Cogs),
                add(add(add(q1Gp, q2Gp), q3Gp), q4Gp),
                add(add(add(q1Ebitda, q2Ebitda), q3Ebitda), q4Ebitda)
        );

        boolean ytdOnly = selection.granularity() == PeriodGranularity.ANNUAL;
        List<MonthlyPlanVsActual> inScope = filterPvaMonths(months, selection, ytdOnly);
        PeriodTotals selectedPeriod = aggregatePvaPeriod(selection.periodLabel(), inScope);

        long monthsWithActuals = months.stream().filter(MonthlyPlanVsActual::hasActuals).count();
        String coverageNote = null;
        if (selection.granularity() == PeriodGranularity.ANNUAL) {
            coverageNote = buildActualsCoverageNote(months);
        }

        return new PlanVsActualResult(
                financialYearPlanId,
                plan.getFiscalYear(),
                baseline.getId(),
                months,
                q1, q2, q3, q4, fy,
                selection.granularity().name(),
                selection.periodLabel(),
                selectedPeriod,
                coverageNote,
                (int) monthsWithActuals,
                months.size());
    }

    public CostPerEmployeeResult getCostPerEmployee(UUID planId, int month, int year) {
        return getCostPerEmployee(planId, PeriodGranularity.MONTHLY, month, year, null, null);
    }

    public CostPerEmployeeResult getCostPerEmployee(UUID planId, int month, int year, UUID forecastTypeId) {
        return getCostPerEmployee(planId, PeriodGranularity.MONTHLY, month, year, null, forecastTypeId);
    }

    public CostPerEmployeeResult getCostPerEmployee(
            UUID planId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter,
            UUID forecastTypeId) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        List<CostPerEmployeeResult> monthly = new ArrayList<>();
        for (YearMonth ym : selection.months()) {
            monthly.add(computeCostPerEmployeeForMonth(
                    planId, ym.getMonthValue(), ym.getYear(), forecastTypeId, false));
        }
        if (monthly.isEmpty()) {
            throw new IllegalArgumentException("No months in selected period");
        }
        if (monthly.size() == 1) {
            CostPerEmployeeResult single = monthly.getFirst();
            return new CostPerEmployeeResult(
                    single.financialYearPlanId(),
                    selection.highlightMonth(),
                    selection.highlightYear(),
                    selection.highlightQuarter(),
                    selection.granularity().name(),
                    selection.periodLabel(),
                    single.fromActuals(),
                    single.billable(),
                    single.bench(),
                    single.support(),
                    single.leadership(),
                    single.totalCostPerBillableHead());
        }

        boolean fromActuals = monthly.stream().allMatch(CostPerEmployeeResult::fromActuals);
        CategoryCost billable = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::billable).toList());
        CategoryCost bench = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::bench).toList());
        CategoryCost support = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::support).toList());
        CategoryCost leadership = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::leadership).toList());
        return new CostPerEmployeeResult(
                planId,
                selection.highlightMonth(),
                selection.highlightYear(),
                selection.highlightQuarter(),
                selection.granularity().name(),
                selection.periodLabel(),
                fromActuals,
                billable,
                bench,
                support,
                leadership,
                billable.total());
    }

    /**
     * Cost-per-employee layers always from the ACTIVE baseline plan (ignores period_actuals).
     * Used by Standard Reports Plan vs Actual columns (ADR-053).
     */
    public CostPerEmployeeResult getCostPerEmployeePlan(
            UUID planId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter,
            UUID forecastTypeId) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        List<CostPerEmployeeResult> monthly = new ArrayList<>();
        for (YearMonth ym : selection.months()) {
            monthly.add(computeCostPerEmployeeForMonth(
                    planId, ym.getMonthValue(), ym.getYear(), forecastTypeId, true));
        }
        if (monthly.isEmpty()) {
            throw new IllegalArgumentException("No months in selected period");
        }
        if (monthly.size() == 1) {
            CostPerEmployeeResult single = monthly.getFirst();
            return new CostPerEmployeeResult(
                    single.financialYearPlanId(),
                    selection.highlightMonth(),
                    selection.highlightYear(),
                    selection.highlightQuarter(),
                    selection.granularity().name(),
                    selection.periodLabel(),
                    false,
                    single.billable(),
                    single.bench(),
                    single.support(),
                    single.leadership(),
                    single.totalCostPerBillableHead());
        }
        CategoryCost billable = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::billable).toList());
        CategoryCost bench = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::bench).toList());
        CategoryCost support = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::support).toList());
        CategoryCost leadership = averageCategoryCosts(monthly.stream().map(CostPerEmployeeResult::leadership).toList());
        return new CostPerEmployeeResult(
                planId,
                selection.highlightMonth(),
                selection.highlightYear(),
                selection.highlightQuarter(),
                selection.granularity().name(),
                selection.periodLabel(),
                false,
                billable,
                bench,
                support,
                leadership,
                billable.total());
    }

    private CostPerEmployeeResult computeCostPerEmployeeForMonth(
            UUID planId, int month, int year, UUID forecastTypeId) {
        return computeCostPerEmployeeForMonth(planId, month, year, forecastTypeId, false);
    }

    private CostPerEmployeeResult computeCostPerEmployeeForMonth(
            UUID planId, int month, int year, UUID forecastTypeId, boolean forcePlan) {
        Optional<PeriodActuals> actualsOpt = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);

        boolean fromActuals = !forcePlan && actualsOpt.isPresent();
        LocalDate monthDate = LocalDate.of(year, month, 1);

        HcFigures hc;
        SalaryFigures salary;
        List<OverheadLineFigures> overheadLines;

        if (fromActuals) {
            PeriodActuals actuals = actualsOpt.get();
            hc = new HcFigures(
                    nullSafeInt(actuals.getActualBillableHc()),
                    nullSafeInt(actuals.getActualBenchHc()),
                    nullSafeInt(actuals.getActualSupportHc()),
                    nullSafeInt(actuals.getActualLeadershipHc()),
                    nullSafeInt(actuals.getActualManagementHc()),
                    nullSafeInt(actuals.getActualTotalHc())
            );
            salary = new SalaryFigures(
                    nullSafe(actuals.getActualBillableSalaries()),
                    nullSafe(actuals.getActualBenchSalaries()),
                    nullSafe(actuals.getActualSupportSalaries()),
                    nullSafe(actuals.getActualManagementSalaries()),
                    nullSafe(actuals.getActualLeadershipSalaries()),
                    ZERO
            );
            overheadLines = resolveOverheadActualsFromExpenses(month, year);
        } else {
            ForecastVersion baseline;
            if (forecastTypeId != null) {
                baseline = forecastVersionRepository.findByForecastTypeIdAndStatus(forecastTypeId, ForecastVersionStatus.ACTIVE)
                        .orElseThrow(() -> new IllegalStateException("No ACTIVE version for forecast type: " + forecastTypeId));
                if (!baseline.getForecastType().getFinancialYearPlan().getId().equals(planId)) {
                    throw new IllegalArgumentException("Forecast type does not belong to the specified plan");
                }
            } else {
                baseline = getActiveBaseline(planId)
                        .orElseThrow(() -> new IllegalStateException("No ACTIVE baseline"));
            }
            hc = getPlannedHcForMonth(baseline.getId(), monthDate);
            salary = getPlannedSalariesForMonth(baseline.getId(), monthDate);
            overheadLines = overheadBudgetRepository
                    .findByForecastVersionIdAndPlanMonthAndPlanYear(baseline.getId(), month, year)
                    .stream()
                    .map(o -> new OverheadLineFigures(o.getOverheadLine(), o.getAmount()))
                    .toList();
        }

        Map<String, BigDecimal> overheadMap = overheadLines.stream()
                .collect(Collectors.toMap(OverheadLineFigures::lineCode, OverheadLineFigures::amount));

        BigDecimal billableContrib = fromActuals
                ? nullSafe(actualsOpt.get().getActualBillableEmployerContributions()) : null;
        BigDecimal benchContrib = fromActuals
                ? nullSafe(actualsOpt.get().getActualBenchEmployerContributions()) : null;
        BigDecimal supportContrib = fromActuals
                ? nullSafe(actualsOpt.get().getActualSupportEmployerContributions()) : null;
        BigDecimal leadershipContrib = fromActuals
                ? nullSafe(actualsOpt.get().getActualLeadershipEmployerContributions()) : null;

        CategoryCost billableCost = computeCategoryLayers("Billable", hc.billableHc(),
                salary.billable(), billableContrib, fromActuals, overheadMap, hc.totalHc(), hc.billableHc());
        CategoryCost benchCost = computeCategoryLayers("Bench", hc.benchHc(),
                salary.bench(), benchContrib, fromActuals, overheadMap, hc.totalHc(), hc.billableHc());
        CategoryCost supportCost = computeCategoryLayers("Support", hc.supportHc(),
                salary.support(), supportContrib, fromActuals, overheadMap, hc.totalHc(), 0);
        CategoryCost leadershipCost = computeCategoryLayers("Leadership", hc.leadershipHc(),
                salary.seniorMgmt(), leadershipContrib, fromActuals, overheadMap, hc.totalHc(), 0);

        return new CostPerEmployeeResult(planId, month, year, null, PeriodGranularity.MONTHLY.name(),
                formatMonthLabel(month, year), fromActuals,
                billableCost, benchCost, supportCost, leadershipCost, billableCost.total());
    }

    public BuMetricsResult getBuMetrics(UUID planId, int month, int year) {
        return getBuMetrics(planId, PeriodGranularity.MONTHLY, month, year, null, null);
    }

    public BuMetricsResult getBuMetrics(UUID planId, int month, int year, UUID forecastTypeId) {
        return getBuMetrics(planId, PeriodGranularity.MONTHLY, month, year, null, forecastTypeId);
    }

    public BuMetricsResult getBuMetrics(
            UUID planId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter,
            UUID forecastTypeId) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        boolean ytdOnly = selection.granularity() == PeriodGranularity.ANNUAL;
        List<YearMonth> monthsInScope = selection.months();
        if (ytdOnly) {
            monthsInScope = monthsInScope.stream()
                    .filter(ym -> periodActualsRepository
                            .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                                    planId, ym.getMonthValue(), ym.getYear())
                            .isPresent())
                    .toList();
            if (monthsInScope.isEmpty()) {
                // No actuals yet — fall back to empty rows with zero plan (still return customers).
                monthsInScope = List.of();
            }
        }

        Map<UUID, BuMetricRow> merged = new LinkedHashMap<>();
        int monthCount = 0;
        for (YearMonth ym : monthsInScope) {
            BuMetricsResult monthResult = computeBuMetricsForMonth(
                    planId, ym.getMonthValue(), ym.getYear(), forecastTypeId);
            monthCount++;
            for (BuMetricRow row : monthResult.rows()) {
                merged.merge(row.customerId(), row, this::sumBuMetricRows);
            }
        }

        if (monthCount == 0) {
            // Annual with no actuals: return customers with zeros from a plan-only April compute if available
            YearMonth first = YearMonth.from(plan.getFiscalYearStart());
            BuMetricsResult emptyMonth = computeBuMetricsForMonth(
                    planId, first.getMonthValue(), first.getYear(), forecastTypeId);
            List<BuMetricRow> zeroRows = emptyMonth.rows().stream()
                    .map(r -> new BuMetricRow(
                            r.customerId(), r.customerCode(), r.customerName(), r.internal(),
                            ZERO, ZERO, ZERO, null, null, null, ZERO, null, ZERO, null, null))
                    .toList();
            return new BuMetricsResult(
                    planId,
                    selection.highlightMonth(),
                    selection.highlightYear(),
                    selection.highlightQuarter(),
                    selection.granularity().name(),
                    selection.periodLabel(),
                    zeroRows);
        }

        List<BuMetricRow> rows = merged.values().stream()
                .map(r -> recalculateBuMargins(r))
                .toList();

        return new BuMetricsResult(
                planId,
                selection.highlightMonth(),
                selection.highlightYear(),
                selection.highlightQuarter(),
                selection.granularity().name(),
                selection.periodLabel(),
                rows);
    }

    /**
     * BU Analysis from master_record per period (ADR-051). Aggregates headcount and payroll by
     * business unit; revenue from RevenueService for external BUs.
     */
    public BuAnalysisResult getBuAnalysis(
            UUID planId,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        PeriodSelection selection = resolvePeriodSelection(plan, granularity, month, year, quarter);

        List<YearMonth> monthsInScope = selection.months();
        if (selection.granularity() == PeriodGranularity.ANNUAL) {
            monthsInScope = monthsInScope.stream()
                    .filter(ym -> !peoplePayrollService
                            .findActiveMasterRecordFacts(ym.getMonthValue(), ym.getYear())
                            .isEmpty())
                    .toList();
        }

        Map<String, BuAggAccum> byBuKey = new LinkedHashMap<>();
        Map<String, BigDecimal> revenueByCustomerCode = new HashMap<>();
        int monthsWithData = 0;

        for (YearMonth ym : monthsInScope) {
            List<MasterRecordFact> facts = peoplePayrollService
                    .findActiveMasterRecordFacts(ym.getMonthValue(), ym.getYear());
            if (!facts.isEmpty()) {
                monthsWithData++;
            }
            for (MasterRecordFact fact : facts) {
                String buName = fact.businessUnit() != null && !fact.businessUnit().isBlank()
                        ? fact.businessUnit().trim() : "(Unassigned)";
                Optional<BuCustomerRef> ref = customerService.resolveBuCustomer(buName);
                String key = ref.map(r -> r.id().toString()).orElse("name:" + buName);
                BuAggAccum agg = byBuKey.computeIfAbsent(key, k -> new BuAggAccum(buName, ref.orElse(null)));
                agg.add(fact);
            }

            ResolvedActualRevenue resolved = resolveActualRevenue(
                    planId, ym.getMonthValue(), ym.getYear(),
                    periodActualsRepository
                            .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                                    planId, ym.getMonthValue(), ym.getYear())
                            .orElse(null));
            for (ClientRevenueFigures fig : resolved.byClient()) {
                if (fig.customerCode() != null && !"MANUAL".equals(fig.customerCode())) {
                    revenueByCustomerCode.merge(
                            fig.customerCode(), nullSafe(fig.totalRevenue()), BigDecimal::add);
                }
            }
        }

        int divisor = Math.max(1, monthsWithData > 0 ? monthsWithData : 1);

        BigDecimal totalCompanyPayrollCost = byBuKey.values().stream()
                .map(a -> a.totalPayrollCost)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalCompanyRevenue = revenueByCustomerCode.values().stream()
                .reduce(ZERO, BigDecimal::add);
        int totalCompanyHc = byBuKey.values().stream()
                .mapToInt(a -> avgHc(a.totalHc, divisor))
                .sum();

        List<ExternalBuAnalysisRow> external = new ArrayList<>();
        List<InternalBuAnalysisRow> internal = new ArrayList<>();

        for (BuAggAccum agg : byBuKey.values()) {
            int totalHc = avgHc(agg.totalHc, divisor);
            int billableHc = avgHc(agg.billableHc, divisor);
            int nonBillableHc = Math.max(0, totalHc - billableHc);
            BigDecimal avgCost = agg.totalHc > 0
                    ? divide(agg.totalPayrollCost, new BigDecimal(agg.totalHc))
                    : ZERO;
            BigDecimal costPct = pctAmount(agg.totalPayrollCost, totalCompanyPayrollCost);
            List<PositionBreakdownRow> positions = buildPositionBreakdown(agg, divisor);

            boolean isInternal = agg.ref != null && agg.ref.internal();
            String code = agg.ref != null ? agg.ref.customerCode() : agg.buName;
            String name = agg.ref != null ? agg.ref.customerName() : agg.buName;

            if (isInternal) {
                internal.add(new InternalBuAnalysisRow(
                        code, name, totalHc, billableHc, nonBillableHc,
                        agg.totalGrossPay, agg.totalPayrollCost, avgCost, costPct, positions));
            } else {
                BigDecimal actualRevenue = ZERO;
                if (agg.ref != null) {
                    actualRevenue = revenueByCustomerCode.getOrDefault(agg.ref.customerCode(), ZERO);
                } else {
                    actualRevenue = revenueByCustomerCode.getOrDefault(agg.buName, ZERO);
                }
                BigDecimal revenuePct = pctAmount(actualRevenue, totalCompanyRevenue);
                BigDecimal grossMargin = actualRevenue.subtract(agg.totalPayrollCost);
                BigDecimal grossMarginPct = actualRevenue.compareTo(ZERO) > 0
                        ? grossMargin.multiply(new BigDecimal("100"))
                        .divide(actualRevenue, 2, RoundingMode.HALF_UP)
                        : ZERO;
                BigDecimal billableGross = agg.billableGrossPay;
                BigDecimal nonBillableGross = agg.totalGrossPay.subtract(agg.billableGrossPay);
                BigDecimal billablePayroll = agg.billablePayrollCost;
                BigDecimal nonBillablePayroll = agg.totalPayrollCost.subtract(agg.billablePayrollCost);

                external.add(new ExternalBuAnalysisRow(
                        code, name, totalHc, billableHc, nonBillableHc,
                        agg.totalGrossPay, billableGross, nonBillableGross,
                        agg.totalPayrollCost, billablePayroll, nonBillablePayroll,
                        avgCost, costPct, revenuePct, actualRevenue, grossMargin, grossMarginPct,
                        positions));
            }
        }

        external.sort(Comparator.comparingInt(ExternalBuAnalysisRow::totalHc).reversed());
        internal.sort(Comparator.comparingInt(InternalBuAnalysisRow::totalHc).reversed());

        return new BuAnalysisResult(
                planId,
                selection.highlightMonth(),
                selection.highlightYear(),
                selection.highlightQuarter(),
                selection.granularity().name(),
                selection.periodLabel(),
                totalCompanyPayrollCost,
                totalCompanyRevenue,
                totalCompanyHc,
                external,
                internal);
    }

    private static int avgHc(int sumHc, int monthCount) {
        if (monthCount <= 1) {
            return sumHc;
        }
        return BigDecimal.valueOf(sumHc)
                .divide(BigDecimal.valueOf(monthCount), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static BigDecimal pctAmount(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(new BigDecimal("100")).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private List<PositionBreakdownRow> buildPositionBreakdown(BuAggAccum agg, int divisor) {
        List<PositionBreakdownRow> rows = new ArrayList<>();
        int buHcForPct = Math.max(1, agg.totalHc);
        for (Map.Entry<String, PositionAccum> e : agg.byTitle.entrySet()) {
            PositionAccum p = e.getValue();
            int hc = avgHc(p.headcount, divisor);
            BigDecimal avgCost = p.headcount > 0
                    ? divide(p.totalPayrollCost, new BigDecimal(p.headcount))
                    : ZERO;
            BigDecimal pct = pctAmount(new BigDecimal(p.headcount), new BigDecimal(buHcForPct));
            rows.add(new PositionBreakdownRow(e.getKey(), hc, avgCost, pct));
        }
        rows.sort(Comparator.comparingInt(PositionBreakdownRow::headcount).reversed());
        return rows;
    }

    private static final class BuAggAccum {
        final String buName;
        final BuCustomerRef ref;
        int totalHc;
        int billableHc;
        BigDecimal totalGrossPay = BigDecimal.ZERO;
        BigDecimal billableGrossPay = BigDecimal.ZERO;
        BigDecimal totalPayrollCost = BigDecimal.ZERO;
        BigDecimal billablePayrollCost = BigDecimal.ZERO;
        final Map<String, PositionAccum> byTitle = new LinkedHashMap<>();

        BuAggAccum(String buName, BuCustomerRef ref) {
            this.buName = buName;
            this.ref = ref;
        }

        void add(MasterRecordFact fact) {
            totalHc++;
            BigDecimal gross = fact.grossPay() != null ? fact.grossPay() : BigDecimal.ZERO;
            BigDecimal cost = fact.totalPayrollCost() != null ? fact.totalPayrollCost() : BigDecimal.ZERO;
            totalGrossPay = totalGrossPay.add(gross);
            totalPayrollCost = totalPayrollCost.add(cost);
            if (fact.billable()) {
                billableHc++;
                billableGrossPay = billableGrossPay.add(gross);
                billablePayrollCost = billablePayrollCost.add(cost);
            }
            String title = fact.title() != null && !fact.title().isBlank() ? fact.title() : "(Unassigned)";
            PositionAccum pos = byTitle.computeIfAbsent(title, k -> new PositionAccum());
            pos.headcount++;
            pos.totalPayrollCost = pos.totalPayrollCost.add(cost);
        }
    }

    private static final class PositionAccum {
        int headcount;
        BigDecimal totalPayrollCost = BigDecimal.ZERO;
    }

    private BuMetricsResult computeBuMetricsForMonth(
            UUID planId, int month, int year, UUID forecastTypeId) {
        ForecastVersion baseline;
        if (forecastTypeId != null) {
            baseline = forecastVersionRepository.findByForecastTypeIdAndStatus(forecastTypeId, ForecastVersionStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalStateException("No ACTIVE version for forecast type: " + forecastTypeId));
            if (!baseline.getForecastType().getFinancialYearPlan().getId().equals(planId)) {
                throw new IllegalArgumentException("Forecast type does not belong to the specified plan");
            }
        } else {
            baseline = getActiveBaseline(planId)
                    .orElseThrow(() -> new IllegalStateException("No ACTIVE baseline"));
        }

        List<CustomerRef> customers = customerService.listCustomerRefs(true);
        LocalDate monthDate = LocalDate.of(year, month, 1);

        List<ClientRevenuePlan> plannedRevenues = clientRevenuePlanRepository
                .findByForecastVersionIdAndPlanMonthAndPlanYear(baseline.getId(), month, year);
        Map<UUID, BigDecimal> plannedRevenueMap = plannedRevenues.stream()
                .collect(Collectors.toMap(
                        ClientRevenuePlan::getCustomerId,
                        p -> p.getPlannedTmRevenue().add(p.getPlannedFixedBidRevenue()),
                        BigDecimal::add
                ));

        Optional<PeriodActuals> actualsOpt = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);
        ResolvedActualRevenue resolved = resolveActualRevenue(planId, month, year, actualsOpt.orElse(null));
        Map<UUID, BigDecimal> actualRevenueMap = new HashMap<>();
        for (ClientRevenueFigures fig : resolved.byClient()) {
            if (fig.customerId() != null) {
                actualRevenueMap.merge(fig.customerId(), fig.totalRevenue(), BigDecimal::add);
            }
        }

        HcFigures plannedHc = getPlannedHcForMonth(baseline.getId(), monthDate);
        int totalPlannedBillableHc = plannedHc.billableHc();
        BigDecimal totalPlannedExternalRevenue = customers.stream()
                .filter(c -> !c.internal())
                .map(c -> plannedRevenueMap.getOrDefault(c.id(), ZERO))
                .reduce(ZERO, BigDecimal::add);
        final Map<String, PeriodBuActuals> buActualsMap;
        if (actualsOpt.isPresent()) {
            List<PeriodBuActuals> buActuals = periodBuActualsRepository
                    .findByPeriodActualsId(actualsOpt.get().getId());
            buActualsMap = buActuals.stream()
                    .collect(Collectors.toMap(PeriodBuActuals::getBusinessUnit, ba -> ba));
        } else {
            buActualsMap = new HashMap<>();
        }

        List<BuMetricRow> rows = new ArrayList<>();
        for (CustomerRef customer : customers) {
            BigDecimal plannedRevenue = plannedRevenueMap.getOrDefault(customer.id(), ZERO);
            BigDecimal actualRevenue = actualRevenueMap.getOrDefault(customer.id(), ZERO);

            BigDecimal clientPlannedRevenue = customer.internal() ? ZERO : plannedRevenue;
            BigDecimal clientActualRevenue = customer.internal() ? ZERO : actualRevenue;

            BigDecimal revenueShare = totalPlannedExternalRevenue.compareTo(ZERO) > 0
                    ? divide(clientPlannedRevenue, totalPlannedExternalRevenue)
                    : ZERO;
            int plannedClientBillableHc = revenueShare.multiply(new BigDecimal(totalPlannedBillableHc))
                    .setScale(0, RoundingMode.HALF_UP).intValue();

            SalaryFigures plannedSalaries = getPlannedSalariesForMonth(baseline.getId(), monthDate);
            BigDecimal totalPlannedBillableSalary = plannedSalaries.billable().add(plannedSalaries.bench());
            BigDecimal avgPlannedSalary = totalPlannedBillableHc > 0
                    ? divide(totalPlannedBillableSalary, new BigDecimal(Math.max(1, totalPlannedBillableHc)))
                    : ZERO;
            BigDecimal plannedSalaryCost = avgPlannedSalary.multiply(new BigDecimal(plannedClientBillableHc));

            PeriodBuActuals buActual = null;
            for (Map.Entry<String, PeriodBuActuals> entry : buActualsMap.entrySet()) {
                Optional<BuCustomerRef> matched = customerService.resolveBuCustomer(entry.getKey());
                if (matched.isPresent() && matched.get().id().equals(customer.id())) {
                    buActual = entry.getValue();
                    break;
                }
            }

            Integer actualBillableHc = buActual != null ? buActual.getBillableHc() : null;
            BigDecimal actualSalaryCost = null;
            if (buActual != null) {
                actualSalaryCost = buActual.getTotalPayrollCost() != null
                        ? buActual.getTotalPayrollCost()
                        : buActual.getTotalGrossPay();
            }

            BigDecimal plannedGrossMargin = clientPlannedRevenue.subtract(plannedSalaryCost);
            BigDecimal actualGrossMargin = actualSalaryCost != null
                    ? clientActualRevenue.subtract(actualSalaryCost)
                    : null;

            BigDecimal plannedGrossMarginPct = clientPlannedRevenue.compareTo(ZERO) > 0
                    ? plannedGrossMargin.multiply(new BigDecimal("100"))
                    .divide(clientPlannedRevenue, 2, RoundingMode.HALF_UP)
                    : ZERO;
            BigDecimal actualGrossMarginPct = actualGrossMargin != null && clientActualRevenue.compareTo(ZERO) > 0
                    ? actualGrossMargin.multiply(new BigDecimal("100"))
                    .divide(clientActualRevenue, 2, RoundingMode.HALF_UP)
                    : null;

            int actualHcForAvg = actualBillableHc != null && actualBillableHc > 0 ? actualBillableHc
                    : (buActual != null ? 1 : 0);
            BigDecimal avgSalaryPerHead = actualSalaryCost != null && actualHcForAvg > 0
                    ? divide(actualSalaryCost, new BigDecimal(actualHcForAvg))
                    : null;

            rows.add(new BuMetricRow(
                    customer.id(),
                    customer.customerCode(),
                    customer.customerName(),
                    customer.internal(),
                    clientPlannedRevenue,
                    clientActualRevenue,
                    plannedSalaryCost,
                    actualSalaryCost,
                    plannedClientBillableHc > 0 ? plannedClientBillableHc : null,
                    actualBillableHc,
                    plannedGrossMargin,
                    actualGrossMargin,
                    plannedGrossMarginPct,
                    actualGrossMarginPct,
                    avgSalaryPerHead
            ));
        }

        return new BuMetricsResult(planId, month, year, null, PeriodGranularity.MONTHLY.name(),
                formatMonthLabel(month, year), rows);
    }

    @Transactional
    public void onPeriodFinalised(PeriodFinalisedEvent event) {
        LocalDate periodDate = LocalDate.of(event.periodYear(), event.periodMonth(), 1);
        Optional<FinancialYearPlan> planOpt = financialYearPlanRepository
                .findByFiscalYearStartLessThanEqualAndFiscalYearEndGreaterThanEqual(periodDate, periodDate);
        if (planOpt.isEmpty()) {
            return;
        }
        FinancialYearPlan plan = planOpt.get();

        PeriodActuals actuals = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                        plan.getId(), event.periodMonth(), event.periodYear())
                .orElseGet(() -> PeriodActuals.builder()
                        .financialYearPlan(plan)
                        .actualsMonth(event.periodMonth())
                        .actualsYear(event.periodYear())
                        .build());

        actuals.setActualBillableHc(event.billableHeadcount());
        actuals.setActualBenchHc(event.benchHeadcount());
        actuals.setActualSupportHc(event.supportHeadcount());
        actuals.setActualLeadershipHc(event.leadershipHeadcount());
        actuals.setActualManagementHc(event.managementHeadcount());
        actuals.setActualTotalHc(event.totalHeadcount());
        actuals.setActualBillableSalaries(event.billableGrossPay());
        actuals.setActualBenchSalaries(event.benchGrossPay());
        actuals.setActualSupportSalaries(event.supportGrossPay());
        actuals.setActualLeadershipSalaries(event.leadershipGrossPay());
        actuals.setActualManagementSalaries(event.managementGrossPay());
        actuals.setActualBillableEmployerContributions(event.billableEmployerContributions());
        actuals.setActualBenchEmployerContributions(event.benchEmployerContributions());
        actuals.setActualSupportEmployerContributions(event.supportEmployerContributions());
        actuals.setActualLeadershipEmployerContributions(event.leadershipEmployerContributions());
        actuals.setActualManagementEmployerContributions(event.managementEmployerContributions());
        actuals.setActualTotalEmployerContributions(event.totalEmployerContributions());
        actuals.setActualTotalPayrollCost(event.totalPayrollCost());
        actuals.setPeoplePeriodVersionId(event.periodVersionId());

        PeriodActuals saved = periodActualsRepository.saveAndFlush(actuals);

        if (saved.getId() != null) {
            periodBuActualsRepository.deleteByPeriodActualsId(saved.getId());
        }
        if (event.buActuals() != null && !event.buActuals().isEmpty()) {
            for (PeriodFinalisedEvent.BuPeriodActual buActual : event.buActuals()) {
                PeriodBuActuals buRecord = PeriodBuActuals.builder()
                        .periodActuals(saved)
                        .businessUnit(buActual.businessUnit())
                        .billableHc(buActual.billableHc())
                        .totalGrossPay(buActual.totalGrossPay() != null ? buActual.totalGrossPay() : ZERO)
                        .totalEmployerContributions(buActual.totalEmployerContributions())
                        .totalPayrollCost(buActual.totalPayrollCost())
                        .build();
                periodBuActualsRepository.save(buRecord);
            }
        }
    }

    private MonthlyFinancials buildMonthlyFinancialsFromActuals(FinancialYearPlan plan,
                                                                PeriodActuals actuals, LocalDate monthDate) {
        int month = monthDate.getMonthValue();
        int year = monthDate.getYear();

        HcFigures hc = new HcFigures(
                nullSafeInt(actuals.getActualBillableHc()),
                nullSafeInt(actuals.getActualBenchHc()),
                nullSafeInt(actuals.getActualSupportHc()),
                nullSafeInt(actuals.getActualLeadershipHc()),
                nullSafeInt(actuals.getActualManagementHc()),
                nullSafeInt(actuals.getActualTotalHc())
        );

        // SalaryFigures keep gross pay for category display; payroll cost (gross + contrib)
        // is applied inside computeFinancials (ADR-045 / ADR-052).
        BigDecimal billableSalary = nullSafe(actuals.getActualBillableSalaries());
        BigDecimal benchSalary = nullSafe(actuals.getActualBenchSalaries());
        BigDecimal supportSalary = nullSafe(actuals.getActualSupportSalaries());
        BigDecimal cofoundersSalary = nullSafe(actuals.getActualManagementSalaries());
        BigDecimal seniorMgmtSalary = nullSafe(actuals.getActualLeadershipSalaries());

        SalaryFigures salary = new SalaryFigures(
                billableSalary, benchSalary, supportSalary, cofoundersSalary, seniorMgmtSalary,
                billableSalary.add(benchSalary).add(supportSalary).add(cofoundersSalary).add(seniorMgmtSalary)
        );

        ResolvedActualRevenue resolved = resolveActualRevenue(plan.getId(), month, year, actuals);
        List<ClientRevenueFigures> revenueByClient = resolved.byClient();
        BigDecimal totalRevenue = revenueByClient.stream()
                .map(ClientRevenueFigures::totalRevenue)
                .reduce(ZERO, BigDecimal::add);

        List<OverheadLineFigures> overhead = resolveOverheadActualsFromExpenses(month, year);

        BigDecimal totalOverhead = overhead.stream()
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        return computeFinancials(
                month, year, true, hc, salary, revenueByClient, totalRevenue, overhead, totalOverhead,
                nullSafe(actuals.getActualBillableEmployerContributions()),
                nullSafe(actuals.getActualBenchEmployerContributions()),
                nullSafe(actuals.getActualSupportEmployerContributions()),
                nullSafe(actuals.getActualLeadershipEmployerContributions()),
                nullSafe(actuals.getActualManagementEmployerContributions()));
    }

    /**
     * Overhead actuals come from the Expenses module (ADR-050). Empty/null → zero (no manual override).
     */
    private List<OverheadLineFigures> resolveOverheadActualsFromExpenses(int month, int year) {
        Map<String, BigDecimal> fromExpenses = expenseService.getMonthlyExpenseActuals(month, year);
        if (fromExpenses == null || fromExpenses.isEmpty()) {
            return List.of();
        }
        return fromExpenses.entrySet().stream()
                .map(e -> new OverheadLineFigures(e.getKey(), nullSafe(e.getValue())))
                .toList();
    }

    private MonthlyFinancials buildMonthlyFinancialsFromPlan(FinancialYearPlan plan,
                                                             ForecastVersion version, LocalDate monthDate) {
        int month = monthDate.getMonthValue();
        int year = monthDate.getYear();

        HcFigures hc = getPlannedHcForMonth(version.getId(), monthDate);
        SalaryFigures salary = getPlannedSalariesForMonth(version.getId(), monthDate);

        List<ClientRevenuePlan> revenuePlans = clientRevenuePlanRepository
                .findByForecastVersionIdAndPlanMonthAndPlanYear(version.getId(), month, year);

        List<ClientRevenueFigures> revenueByClient = revenuePlans.stream()
                .map(rp -> {
                    Optional<CustomerRef> custOpt = customerService.findCustomerRef(rp.getCustomerId());
                    return new ClientRevenueFigures(
                            rp.getCustomerId(),
                            custOpt.map(CustomerRef::customerCode).orElse("UNKNOWN"),
                            custOpt.map(CustomerRef::customerName).orElse("Unknown Customer"),
                            rp.getPlannedTmRevenue(),
                            rp.getPlannedFixedBidRevenue(),
                            rp.getPlannedTmRevenue().add(rp.getPlannedFixedBidRevenue())
                    );
                })
                .toList();

        BigDecimal totalRevenue = revenueByClient.stream()
                .map(ClientRevenueFigures::totalRevenue)
                .reduce(ZERO, BigDecimal::add);

        List<OverheadBudget> overheadBudgets = overheadBudgetRepository
                .findByForecastVersionIdAndPlanMonthAndPlanYear(version.getId(), month, year);
        List<OverheadLineFigures> overhead = overheadBudgets.stream()
                .map(ob -> new OverheadLineFigures(ob.getOverheadLine(), ob.getAmount()))
                .toList();

        BigDecimal totalOverhead = overhead.stream()
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        return computeFinancials(
                month, year, false, hc, salary, revenueByClient, totalRevenue, overhead, totalOverhead,
                null, null, null, null, null);
    }

    /**
     * Confirmed P&amp;L formulas (ADR-052):
     * <ul>
     *   <li>COGS = Billable Payroll Cost + Bench Payroll Cost + Delivery Overheads
     *       (training_upskilling, subcontractors)</li>
     *   <li>Gross Profit = Total Revenue − COGS</li>
     *   <li>OpEx = Support + Leadership + Management Payroll Cost + Non-Delivery Overheads
     *       + Variable Pay</li>
     *   <li>EBITDA = Gross Profit − OpEx</li>
     * </ul>
     * Payroll Cost = Gross Pay + Employer Contributions (actuals) or Gross × 1.13 (plan estimate).
     */
    private MonthlyFinancials computeFinancials(int month, int year, boolean fromActuals,
                                                HcFigures hc, SalaryFigures salary,
                                                List<ClientRevenueFigures> revenueByClient, BigDecimal totalRevenue,
                                                List<OverheadLineFigures> overhead, BigDecimal totalOverhead,
                                                BigDecimal billableContrib,
                                                BigDecimal benchContrib,
                                                BigDecimal supportContrib,
                                                BigDecimal leadershipContrib,
                                                BigDecimal managementContrib) {
        BigDecimal billablePayroll = payrollCost(salary.billable(), billableContrib, fromActuals);
        BigDecimal benchPayroll = payrollCost(salary.bench(), benchContrib, fromActuals);
        BigDecimal supportPayroll = payrollCost(salary.support(), supportContrib, fromActuals);
        BigDecimal leadershipPayroll = payrollCost(salary.seniorMgmt(), leadershipContrib, fromActuals);
        BigDecimal managementPayroll = payrollCost(salary.cofounders(), managementContrib, fromActuals);

        BigDecimal totalSalaryCost = billablePayroll.add(benchPayroll).add(supportPayroll)
                .add(leadershipPayroll).add(managementPayroll);

        BigDecimal statutoryBenefits;
        if (fromActuals) {
            statutoryBenefits = nullSafe(billableContrib).add(nullSafe(benchContrib))
                    .add(nullSafe(supportContrib)).add(nullSafe(leadershipContrib))
                    .add(nullSafe(managementContrib));
        } else {
            statutoryBenefits = salary.total().multiply(STATUTORY_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal variablePay = ZERO;
        if (VARIABLE_PAY_MONTHS.contains(month)) {
            BigDecimal variableBase = salary.cofounders().add(salary.seniorMgmt());
            variablePay = variableBase.multiply(VARIABLE_PAY_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal deliveryOverhead = overhead.stream()
                .filter(o -> DELIVERY_OVERHEAD_LINES.contains(o.lineCode()))
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalCogs = billablePayroll.add(benchPayroll).add(deliveryOverhead);
        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);

        BigDecimal nonDeliveryOverhead = overhead.stream()
                .filter(o -> !DELIVERY_OVERHEAD_LINES.contains(o.lineCode()))
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalOpex = supportPayroll.add(leadershipPayroll).add(managementPayroll)
                .add(variablePay).add(nonDeliveryOverhead);

        BigDecimal ebitda = grossProfit.subtract(totalOpex);

        return new MonthlyFinancials(
                month, year, fromActuals, hc, salary, revenueByClient, totalRevenue,
                overhead, totalOverhead, totalSalaryCost, statutoryBenefits, variablePay,
                totalCogs, grossProfit, totalOpex, ebitda, billableRatioPct(hc)
        );
    }

    /** Billable Ratio % = (billableHc ÷ totalHc) × 100 — always a percentage, never 0–1. */
    private static BigDecimal billableRatioPct(HcFigures hc) {
        if (hc == null || hc.totalHc() <= 0) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(hc.billableHc())
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(hc.totalHc()), 2, RoundingMode.HALF_UP);
    }

    private static HcFigures sumHcFigures(List<MonthlyFinancials> months) {
        HcFigures hc = new HcFigures(0, 0, 0, 0, 0, 0);
        for (MonthlyFinancials m : months) {
            hc = new HcFigures(
                    hc.billableHc() + m.hc().billableHc(),
                    hc.benchHc() + m.hc().benchHc(),
                    hc.supportHc() + m.hc().supportHc(),
                    hc.leadershipHc() + m.hc().leadershipHc(),
                    hc.managementHc() + m.hc().managementHc(),
                    hc.totalHc() + m.hc().totalHc());
        }
        return hc;
    }

    private static MonthlyFinancials withBillableRatioPct(MonthlyFinancials m, BigDecimal billableRatioPct) {
        return new MonthlyFinancials(
                m.month(), m.year(), m.fromActuals(), m.hc(), m.salary(), m.revenueByClient(),
                m.totalRevenue(), m.overhead(), m.totalOverhead(), m.totalSalaryCost(),
                m.statutoryBenefits(), m.variablePay(), m.totalCogs(), m.grossProfit(),
                m.totalOpex(), m.ebitda(), billableRatioPct);
    }

    /** Actuals: gross + employer contributions. Plan: gross × 1.13 estimate (ADR-045). */
    private BigDecimal payrollCost(BigDecimal grossOrBudget, BigDecimal actualContrib, boolean fromActuals) {
        if (fromActuals) {
            return nullSafe(grossOrBudget).add(nullSafe(actualContrib));
        }
        return nullSafe(grossOrBudget).multiply(BigDecimal.ONE.add(STATUTORY_RATE))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private MonthlyFinancials nullMonth(LocalDate monthDate) {
        int month = monthDate.getMonthValue();
        int year = monthDate.getYear();
        HcFigures zeroHc = new HcFigures(0, 0, 0, 0, 0, 0);
        SalaryFigures zeroSalary = new SalaryFigures(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
        return new MonthlyFinancials(month, year, false, zeroHc, zeroSalary, List.of(), ZERO,
                List.of(), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private HcFigures getPlannedHcForMonth(UUID versionId, LocalDate monthDate) {
        int month = monthDate.getMonthValue();
        int year = monthDate.getYear();
        Optional<HcPlan> hcOpt = hcPlanRepository
                .findByForecastVersionIdAndPlanMonthAndPlanYear(versionId, month, year);
        if (hcOpt.isPresent()) {
            HcPlan hc = hcOpt.get();
            int total = hc.getPlannedBillableHc() + hc.getPlannedBenchHc() + hc.getPlannedSupportHc()
                    + hc.getPlannedLeadershipHc() + hc.getPlannedManagementHc();
            return new HcFigures(hc.getPlannedBillableHc(), hc.getPlannedBenchHc(),
                    hc.getPlannedSupportHc(), hc.getPlannedLeadershipHc(), hc.getPlannedManagementHc(), total);
        }
        return new HcFigures(0, 0, 0, 0, 0, 0);
    }

    private SalaryFigures getPlannedSalariesForMonth(UUID versionId, LocalDate monthDate) {
        int month = monthDate.getMonthValue();
        int year = monthDate.getYear();
        Optional<SalaryBudget> salaryOpt = salaryBudgetRepository
                .findByForecastVersionIdAndPlanMonthAndPlanYear(versionId, month, year);
        if (salaryOpt.isPresent()) {
            SalaryBudget s = salaryOpt.get();
            BigDecimal total = s.getBillableSalaries().add(s.getBenchSalaries()).add(s.getSupportSalaries())
                    .add(s.getCofoundersSalaries()).add(s.getSeniorMgmtSalaries());
            return new SalaryFigures(s.getBillableSalaries(), s.getBenchSalaries(), s.getSupportSalaries(),
                    s.getCofoundersSalaries(), s.getSeniorMgmtSalaries(), total);
        }
        return new SalaryFigures(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    private MonthlyPlanVsActual buildMonthlyPlanVsActual(MonthlyFinancials plan, MonthlyFinancials actual,
                                                         boolean hasActuals, String revenueSource) {
        TriadHc hc = new TriadHc(plan.hc(), actual.hc(),
                new HcFigures(
                        actual.hc().billableHc() - plan.hc().billableHc(),
                        actual.hc().benchHc() - plan.hc().benchHc(),
                        actual.hc().supportHc() - plan.hc().supportHc(),
                        actual.hc().leadershipHc() - plan.hc().leadershipHc(),
                        actual.hc().managementHc() - plan.hc().managementHc(),
                        actual.hc().totalHc() - plan.hc().totalHc()
                )
        );

        TriadSalary salary = new TriadSalary(plan.salary(), actual.salary(),
                new SalaryFigures(
                        subtract(actual.salary().billable(), plan.salary().billable()),
                        subtract(actual.salary().bench(), plan.salary().bench()),
                        subtract(actual.salary().support(), plan.salary().support()),
                        subtract(actual.salary().cofounders(), plan.salary().cofounders()),
                        subtract(actual.salary().seniorMgmt(), plan.salary().seniorMgmt()),
                        subtract(actual.salary().total(), plan.salary().total())
                )
        );

        List<TriadClientRevenue> revenueByClient = computeTriadRevenueByClient(
                plan.revenueByClient(), actual.revenueByClient());

        MoneyTriad totalRevenue = triad(plan.totalRevenue(), actual.totalRevenue());

        List<TriadOverhead> overheadTriad = computeTriadOverheadLines(plan.overhead(), actual.overhead());

        MoneyTriad totalOverhead = triad(plan.totalOverhead(), actual.totalOverhead());
        MoneyTriad totalSalaryCost = triad(plan.totalSalaryCost(), actual.totalSalaryCost());
        MoneyTriad statutoryBenefits = triad(plan.statutoryBenefits(), actual.statutoryBenefits());
        MoneyTriad totalCogs = triad(plan.totalCogs(), actual.totalCogs());
        MoneyTriad grossProfit = triad(plan.grossProfit(), actual.grossProfit());
        MoneyTriad ebitda = triad(plan.ebitda(), actual.ebitda());

        return new MonthlyPlanVsActual(plan.month(), plan.year(), hasActuals, revenueSource,
                hc, salary, revenueByClient,
                totalRevenue, overheadTriad, totalOverhead, totalSalaryCost, statutoryBenefits,
                totalCogs, grossProfit, ebitda);
    }

    /**
     * Prefer Revenue module net figures when an upload exists for the period; otherwise fall back
     * to {@code period_actuals.actual_revenue_manual} / per-client manual rows (ADR-043).
     */
    private ResolvedActualRevenue resolveActualRevenue(
            UUID planId, int month, int year, PeriodActuals actualsOrNull) {
        List<MonthlyRevenueSummary> fromRevenue =
                revenueService.getAllClientsMonthlyRevenue(month, year);
        if (fromRevenue != null) {
            List<ClientRevenueFigures> byClient = fromRevenue.stream()
                    .map(summary -> {
                        var customer = customerService.resolveBuCustomer(summary.customerId());
                        BigDecimal amount = preferredNetAmount(summary);
                        return new ClientRevenueFigures(
                                customer.map(CustomerService.BuCustomerRef::id).orElse(null),
                                summary.customerId(),
                                customer.map(CustomerService.BuCustomerRef::customerName)
                                        .orElse(summary.customerId()),
                                ZERO,
                                amount,
                                amount);
                    })
                    .toList();
            return new ResolvedActualRevenue(byClient, REVENUE_SOURCE_MODULE);
        }

        List<ClientRevenueActual> revenueActuals = clientRevenueActualRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);
        if (!revenueActuals.isEmpty()) {
            List<ClientRevenueFigures> byClient = revenueActuals.stream()
                    .map(ra -> {
                        Optional<CustomerRef> custOpt = customerService.findCustomerRef(ra.getCustomerId());
                        return new ClientRevenueFigures(
                                ra.getCustomerId(),
                                custOpt.map(CustomerRef::customerCode).orElse("UNKNOWN"),
                                custOpt.map(CustomerRef::customerName).orElse("Unknown Customer"),
                                ZERO,
                                ra.getActualRevenue(),
                                ra.getActualRevenue());
                    })
                    .toList();
            return new ResolvedActualRevenue(byClient, REVENUE_SOURCE_MANUAL);
        }

        if (actualsOrNull != null && actualsOrNull.getActualRevenueManual() != null) {
            return new ResolvedActualRevenue(
                    List.of(new ClientRevenueFigures(
                            null,
                            "MANUAL",
                            "Manual Override",
                            ZERO,
                            actualsOrNull.getActualRevenueManual(),
                            actualsOrNull.getActualRevenueManual())),
                    REVENUE_SOURCE_MANUAL);
        }

        return new ResolvedActualRevenue(List.of(), null);
    }

    private static BigDecimal preferredNetAmount(MonthlyRevenueSummary summary) {
        // Budgeting plan figures are INR — prefer converted net when present.
        if (summary.netRevenueInr() != null) {
            return summary.netRevenueInr();
        }
        return nullSafeStatic(summary.netRevenue());
    }

    private static BigDecimal nullSafeStatic(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private record ResolvedActualRevenue(List<ClientRevenueFigures> byClient, String source) {}


    private CategoryCost computeCategoryLayers(String category, int headcount, BigDecimal salary,
                                               BigDecimal actualEmployerContributions, boolean fromActuals,
                                               Map<String, BigDecimal> overheadMap, int totalHc, int billableHc) {
        if (headcount == 0) {
            return new CategoryCost(category, 0, ZERO, ZERO, fromActuals ? "ACTUAL" : "ESTIMATE_13PCT",
                    ZERO, ZERO, ZERO, ZERO);
        }

        BigDecimal avgSalary = divide(salary, new BigDecimal(Math.max(1, headcount)));
        BigDecimal employerPerHead;
        String contribSource;
        if (fromActuals) {
            employerPerHead = divide(nullSafe(actualEmployerContributions), new BigDecimal(Math.max(1, headcount)));
            contribSource = "ACTUAL";
        } else {
            employerPerHead = avgSalary.multiply(STATUTORY_RATE).setScale(2, RoundingMode.HALF_UP);
            contribSource = "ESTIMATE_13PCT";
        }
        BigDecimal layer1 = avgSalary.add(employerPerHead);

        BigDecimal directOverheadTotal = DIRECT_OVERHEAD_LINES.stream()
                .map(line -> overheadMap.getOrDefault(line, ZERO))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal layer2 = totalHc > 0
                ? divide(directOverheadTotal, new BigDecimal(totalHc))
                : ZERO;

        BigDecimal layer3 = ZERO;
        // Layer 3 (shared/fixed overhead) is allocated to Billable HC only — Full Absorption Model 1 (ADR-038)
        if ("Billable".equals(category)) {
            BigDecimal allOtherOverhead = overheadMap.entrySet().stream()
                    .filter(e -> !DIRECT_OVERHEAD_LINES.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .reduce(ZERO, BigDecimal::add);
            layer3 = billableHc > 0
                    ? divide(allOtherOverhead, new BigDecimal(billableHc))
                    : ZERO;
        }

        BigDecimal total = layer1.add(layer2).add(layer3);
        return new CategoryCost(category, headcount, avgSalary, employerPerHead, contribSource,
                layer1, layer2, layer3, total);
    }

    private List<ClientRevenueFigures> computeDeltaRevenueByClient(
            List<ClientRevenueFigures> rolling, List<ClientRevenueFigures> baseline) {
        Map<String, ClientRevenueFigures> baselineMap = baseline.stream()
                .collect(Collectors.toMap(this::clientRevenueKey, c -> c, (a, b) -> a));
        Map<String, ClientRevenueFigures> result = new LinkedHashMap<>();

        for (ClientRevenueFigures r : rolling) {
            String key = clientRevenueKey(r);
            ClientRevenueFigures b = baselineMap.getOrDefault(key,
                    new ClientRevenueFigures(r.customerId(), r.customerCode(), r.customerName(), ZERO, ZERO, ZERO));
            result.put(key, new ClientRevenueFigures(
                    r.customerId(), r.customerCode(), r.customerName(),
                    subtract(r.tmRevenue(), b.tmRevenue()),
                    subtract(r.fixedBidRevenue(), b.fixedBidRevenue()),
                    subtract(r.totalRevenue(), b.totalRevenue())
            ));
        }

        for (ClientRevenueFigures b : baseline) {
            String key = clientRevenueKey(b);
            if (!result.containsKey(key)) {
                result.put(key, new ClientRevenueFigures(
                        b.customerId(), b.customerCode(), b.customerName(),
                        b.tmRevenue().negate(), b.fixedBidRevenue().negate(), b.totalRevenue().negate()
                ));
            }
        }

        return new ArrayList<>(result.values());
    }

    private String clientRevenueKey(ClientRevenueFigures figures) {
        return figures.customerId() != null ? figures.customerId().toString() : "MANUAL";
    }

    private List<OverheadLineFigures> computeDeltaOverheadLines(
            List<OverheadLineFigures> rolling, List<OverheadLineFigures> baseline) {
        Map<String, BigDecimal> baselineMap = baseline.stream()
                .collect(Collectors.toMap(OverheadLineFigures::lineCode, OverheadLineFigures::amount));
        Map<String, BigDecimal> result = new HashMap<>();

        for (OverheadLineFigures r : rolling) {
            BigDecimal b = baselineMap.getOrDefault(r.lineCode(), ZERO);
            result.put(r.lineCode(), subtract(r.amount(), b));
        }

        for (OverheadLineFigures b : baseline) {
            if (!result.containsKey(b.lineCode())) {
                result.put(b.lineCode(), b.amount().negate());
            }
        }

        return result.entrySet().stream()
                .map(e -> new OverheadLineFigures(e.getKey(), e.getValue()))
                .toList();
    }

    private List<TriadClientRevenue> computeTriadRevenueByClient(
            List<ClientRevenueFigures> plan, List<ClientRevenueFigures> actual) {
        Map<UUID, ClientRevenueFigures> actualMap = actual.stream()
                .collect(Collectors.toMap(ClientRevenueFigures::customerId, c -> c, (a, b) -> a));
        Map<UUID, TriadClientRevenue> result = new HashMap<>();

        for (ClientRevenueFigures p : plan) {
            ClientRevenueFigures a = actualMap.getOrDefault(p.customerId(),
                    new ClientRevenueFigures(p.customerId(), p.customerCode(), p.customerName(), ZERO, ZERO, ZERO));
            result.put(p.customerId(), new TriadClientRevenue(
                    p.customerId(), p.customerCode(),
                    triad(p.tmRevenue(), a.tmRevenue()),
                    triad(p.fixedBidRevenue(), a.fixedBidRevenue()),
                    triad(p.totalRevenue(), a.totalRevenue())
            ));
        }

        for (ClientRevenueFigures a : actual) {
            if (!result.containsKey(a.customerId())) {
                result.put(a.customerId(), new TriadClientRevenue(
                        a.customerId(), a.customerCode(),
                        triad(ZERO, a.tmRevenue()),
                        triad(ZERO, a.fixedBidRevenue()),
                        triad(ZERO, a.totalRevenue())
                ));
            }
        }

        return new ArrayList<>(result.values());
    }

    private List<TriadOverhead> computeTriadOverheadLines(
            List<OverheadLineFigures> plan, List<OverheadLineFigures> actual) {
        Map<String, BigDecimal> actualMap = actual.stream()
                .collect(Collectors.toMap(OverheadLineFigures::lineCode, OverheadLineFigures::amount));
        Map<String, MoneyTriad> result = new HashMap<>();

        for (OverheadLineFigures p : plan) {
            BigDecimal a = actualMap.getOrDefault(p.lineCode(), ZERO);
            result.put(p.lineCode(), triad(p.amount(), a));
        }

        for (OverheadLineFigures a : actual) {
            if (!result.containsKey(a.lineCode())) {
                result.put(a.lineCode(), triad(ZERO, a.amount()));
            }
        }

        return result.entrySet().stream()
                .map(e -> new TriadOverhead(e.getKey(), e.getValue()))
                .toList();
    }

    public Optional<YearMonth> findLatestActualsMonth(UUID planId) {
        List<PeriodActuals> actuals = periodActualsRepository.findByFinancialYearPlanId(planId);
        return actuals.stream()
                .map(a -> YearMonth.of(a.getActualsYear(), a.getActualsMonth()))
                .max(Comparator
                        .comparingInt((YearMonth ym) -> fiscalSortKey(ym.getMonthValue(), ym.getYear()))
                        .thenComparing(ym -> ym));
    }

    private record PeriodSelection(
            PeriodGranularity granularity,
            List<YearMonth> months,
            String periodLabel,
            Integer highlightMonth,
            Integer highlightYear,
            Integer highlightQuarter
    ) {}

    private PeriodSelection resolvePeriodSelection(
            FinancialYearPlan plan,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            Integer quarter) {
        PeriodGranularity g = granularity != null ? granularity : PeriodGranularity.ANNUAL;
        return switch (g) {
            case MONTHLY -> {
                if (month == null || year == null) {
                    throw new IllegalArgumentException("month and year are required for MONTHLY granularity");
                }
                if (month < 1 || month > 12) {
                    throw new IllegalArgumentException("month must be between 1 and 12");
                }
                YearMonth ym = YearMonth.of(year, month);
                ensureInFiscalYear(plan, ym);
                yield new PeriodSelection(
                        g,
                        List.of(ym),
                        formatMonthLabel(month, year),
                        month,
                        year,
                        null);
            }
            case QUARTERLY -> {
                if (quarter == null || year == null) {
                    throw new IllegalArgumentException("quarter and year are required for QUARTERLY granularity");
                }
                if (quarter < 1 || quarter > 4) {
                    throw new IllegalArgumentException("quarter must be between 1 and 4");
                }
                List<YearMonth> qMonths = quarterMonths(quarter, year);
                for (YearMonth ym : qMonths) {
                    ensureInFiscalYear(plan, ym);
                }
                yield new PeriodSelection(
                        g,
                        qMonths,
                        "Q" + quarter + " " + plan.getFiscalYear(),
                        qMonths.getFirst().getMonthValue(),
                        qMonths.getFirst().getYear(),
                        quarter);
            }
            case ANNUAL -> {
                List<YearMonth> all = new ArrayList<>();
                for (LocalDate d = plan.getFiscalYearStart();
                     !d.isAfter(plan.getFiscalYearEnd());
                     d = d.plusMonths(1)) {
                    all.add(YearMonth.from(d));
                }
                yield new PeriodSelection(
                        g,
                        List.copyOf(all),
                        plan.getFiscalYear(),
                        null,
                        null,
                        null);
            }
        };
    }

    private static List<YearMonth> quarterMonths(int quarter, int firstMonthYear) {
        // year = calendar year of the quarter's first month (ADR-049)
        return switch (quarter) {
            case 1 -> List.of(
                    YearMonth.of(firstMonthYear, 4),
                    YearMonth.of(firstMonthYear, 5),
                    YearMonth.of(firstMonthYear, 6));
            case 2 -> List.of(
                    YearMonth.of(firstMonthYear, 7),
                    YearMonth.of(firstMonthYear, 8),
                    YearMonth.of(firstMonthYear, 9));
            case 3 -> List.of(
                    YearMonth.of(firstMonthYear, 10),
                    YearMonth.of(firstMonthYear, 11),
                    YearMonth.of(firstMonthYear, 12));
            case 4 -> List.of(
                    YearMonth.of(firstMonthYear, 1),
                    YearMonth.of(firstMonthYear, 2),
                    YearMonth.of(firstMonthYear, 3));
            default -> throw new IllegalArgumentException("quarter must be between 1 and 4");
        };
    }

    private void ensureInFiscalYear(FinancialYearPlan plan, YearMonth ym) {
        LocalDate d = ym.atDay(1);
        if (d.isBefore(plan.getFiscalYearStart().withDayOfMonth(1))
                || d.isAfter(plan.getFiscalYearEnd().withDayOfMonth(1))) {
            throw new IllegalArgumentException(
                    "Period " + ym + " is outside fiscal year " + plan.getFiscalYear());
        }
    }

    private static String formatMonthLabel(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    private static int fiscalSortKey(int month, int year) {
        // Apr…Mar ordering within an Indian FY
        int fiscalMonth = month >= 4 ? month - 3 : month + 9;
        int fiscalYearStart = month >= 4 ? year : year - 1;
        return fiscalYearStart * 12 + fiscalMonth;
    }

    private List<MonthlyFinancials> filterMonths(
            List<MonthlyFinancials> months, PeriodSelection selection, boolean actualsOnly) {
        Set<YearMonth> scope = new HashSet<>(selection.months());
        return months.stream()
                .filter(m -> scope.contains(YearMonth.of(m.year(), m.month())))
                .filter(m -> !actualsOnly || m.fromActuals())
                .toList();
    }

    private List<MonthlyPlanVsActual> filterPvaMonths(
            List<MonthlyPlanVsActual> months, PeriodSelection selection, boolean actualsOnly) {
        Set<YearMonth> scope = new HashSet<>(selection.months());
        return months.stream()
                .filter(m -> scope.contains(YearMonth.of(m.year(), m.month())))
                .filter(m -> !actualsOnly || m.hasActuals())
                .toList();
    }

    private PeriodTotals aggregatePvaPeriod(String label, List<MonthlyPlanVsActual> months) {
        MoneyTriad rev = zero(), sal = zero(), ovh = zero(), cogs = zero(), gp = zero(), ebitda = zero();
        for (MonthlyPlanVsActual m : months) {
            rev = add(rev, m.totalRevenue());
            sal = add(sal, m.totalSalaryCost());
            ovh = add(ovh, m.totalOverhead());
            cogs = add(cogs, m.totalCogs());
            gp = add(gp, m.grossProfit());
            ebitda = add(ebitda, m.ebitda());
        }
        return new PeriodTotals(label, rev, sal, ovh, cogs, gp, ebitda);
    }

    private String buildActualsCoverageNote(List<MonthlyPlanVsActual> months) {
        List<MonthlyPlanVsActual> withActuals = months.stream()
                .filter(MonthlyPlanVsActual::hasActuals)
                .toList();
        int n = withActuals.size();
        if (n == 0) {
            return "Actuals: none (0 of 12 months)";
        }
        MonthlyPlanVsActual first = withActuals.getFirst();
        MonthlyPlanVsActual last = withActuals.getLast();
        String start = Month.of(first.month()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                + " " + first.year();
        String end = Month.of(last.month()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                + " " + last.year();
        String range = start.equals(end) ? start : start + "–" + end;
        return "Actuals: " + range + " (" + n + " of 12 months)";
    }

    private MonthlyFinancials sumMonthlyFinancials(List<MonthlyFinancials> months, PeriodSelection selection) {
        if (months.isEmpty()) {
            YearMonth anchor = selection.months().isEmpty()
                    ? YearMonth.now()
                    : selection.months().getFirst();
            return nullMonth(anchor.atDay(1));
        }
        MonthlyFinancials first = months.getFirst();
        HcFigures hc = new HcFigures(0, 0, 0, 0, 0, 0);
        SalaryFigures salary = new SalaryFigures(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
        Map<String, ClientRevenueFigures> revenueByClient = new LinkedHashMap<>();
        Map<String, BigDecimal> overhead = new LinkedHashMap<>();
        BigDecimal totalRevenue = ZERO;
        BigDecimal totalOverhead = ZERO;
        BigDecimal totalSalaryCost = ZERO;
        BigDecimal statutoryBenefits = ZERO;
        BigDecimal variablePay = ZERO;
        BigDecimal totalCogs = ZERO;
        BigDecimal grossProfit = ZERO;
        BigDecimal totalOpex = ZERO;
        BigDecimal ebitda = ZERO;
        boolean anyActuals = false;

        for (MonthlyFinancials m : months) {
            anyActuals = anyActuals || m.fromActuals();
            hc = addHc(hc, m.hc());
            salary = addSalary(salary, m.salary());
            for (ClientRevenueFigures c : m.revenueByClient()) {
                String key = clientRevenueKey(c);
                revenueByClient.merge(key, c, (a, b) -> new ClientRevenueFigures(
                        a.customerId(), a.customerCode(), a.customerName(),
                        a.tmRevenue().add(b.tmRevenue()),
                        a.fixedBidRevenue().add(b.fixedBidRevenue()),
                        a.totalRevenue().add(b.totalRevenue())));
            }
            for (OverheadLineFigures o : m.overhead()) {
                overhead.merge(o.lineCode(), o.amount(), BigDecimal::add);
            }
            totalRevenue = totalRevenue.add(m.totalRevenue());
            totalOverhead = totalOverhead.add(m.totalOverhead());
            totalSalaryCost = totalSalaryCost.add(m.totalSalaryCost());
            statutoryBenefits = statutoryBenefits.add(m.statutoryBenefits());
            variablePay = variablePay.add(m.variablePay());
            totalCogs = totalCogs.add(m.totalCogs());
            grossProfit = grossProfit.add(m.grossProfit());
            totalOpex = totalOpex.add(m.totalOpex());
            ebitda = ebitda.add(m.ebitda());
        }

        return new MonthlyFinancials(
                first.month(),
                first.year(),
                anyActuals,
                hc,
                salary,
                List.copyOf(revenueByClient.values()),
                totalRevenue,
                overhead.entrySet().stream()
                        .map(e -> new OverheadLineFigures(e.getKey(), e.getValue()))
                        .toList(),
                totalOverhead,
                totalSalaryCost,
                statutoryBenefits,
                variablePay,
                totalCogs,
                grossProfit,
                totalOpex,
                ebitda,
                billableRatioPct(hc));
    }

    private HcFigures addHc(HcFigures a, HcFigures b) {
        return new HcFigures(
                a.billableHc() + b.billableHc(),
                a.benchHc() + b.benchHc(),
                a.supportHc() + b.supportHc(),
                a.leadershipHc() + b.leadershipHc(),
                a.managementHc() + b.managementHc(),
                a.totalHc() + b.totalHc());
    }

    private SalaryFigures addSalary(SalaryFigures a, SalaryFigures b) {
        return new SalaryFigures(
                a.billable().add(b.billable()),
                a.bench().add(b.bench()),
                a.support().add(b.support()),
                a.cofounders().add(b.cofounders()),
                a.seniorMgmt().add(b.seniorMgmt()),
                a.total().add(b.total()));
    }

    private CategoryCost averageCategoryCosts(List<CategoryCost> costs) {
        if (costs.isEmpty()) {
            return new CategoryCost("?", 0, ZERO, ZERO, "ESTIMATE_13PCT", ZERO, ZERO, ZERO, ZERO);
        }
        int n = costs.size();
        BigDecimal nBd = new BigDecimal(n);
        CategoryCost first = costs.getFirst();
        int avgHc = (int) Math.round(costs.stream().mapToInt(CategoryCost::headcount).average().orElse(0));
        return new CategoryCost(
                first.category(),
                avgHc,
                avgBd(costs.stream().map(CategoryCost::grossPayPerHead).toList(), nBd),
                avgBd(costs.stream().map(CategoryCost::employerContributionsPerHead).toList(), nBd),
                costs.stream().allMatch(c -> "ACTUAL".equals(c.employerContributionsSource()))
                        ? "ACTUAL" : first.employerContributionsSource(),
                avgBd(costs.stream().map(CategoryCost::layer1).toList(), nBd),
                avgBd(costs.stream().map(CategoryCost::layer2).toList(), nBd),
                avgBd(costs.stream().map(CategoryCost::layer3).toList(), nBd),
                avgBd(costs.stream().map(CategoryCost::total).toList(), nBd));
    }

    private BigDecimal avgBd(List<BigDecimal> values, BigDecimal n) {
        return values.stream().reduce(ZERO, BigDecimal::add).divide(n, 2, RoundingMode.HALF_UP);
    }

    private BuMetricRow sumBuMetricRows(BuMetricRow a, BuMetricRow b) {
        BigDecimal actualRevenue = sumNullable(a.actualRevenue(), b.actualRevenue());
        BigDecimal actualSalary = sumNullable(a.actualSalaryCost(), b.actualSalaryCost());
        Integer plannedHc = sumNullableInt(a.plannedBillableHc(), b.plannedBillableHc());
        Integer actualHc = sumNullableInt(a.actualBillableHc(), b.actualBillableHc());
        return new BuMetricRow(
                a.customerId(),
                a.customerCode(),
                a.customerName(),
                a.internal(),
                a.plannedRevenue().add(b.plannedRevenue()),
                actualRevenue,
                a.plannedSalaryCost().add(b.plannedSalaryCost()),
                actualSalary,
                plannedHc,
                actualHc,
                ZERO, // recalculated
                null,
                ZERO,
                null,
                null);
    }

    private BuMetricRow recalculateBuMargins(BuMetricRow r) {
        BigDecimal plannedGm = r.plannedRevenue().subtract(r.plannedSalaryCost());
        BigDecimal actualGm = r.actualSalaryCost() != null
                ? nullSafe(r.actualRevenue()).subtract(r.actualSalaryCost())
                : null;
        BigDecimal plannedGmPct = r.plannedRevenue().compareTo(ZERO) > 0
                ? plannedGm.multiply(new BigDecimal("100"))
                .divide(r.plannedRevenue(), 2, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal actualGmPct = actualGm != null && nullSafe(r.actualRevenue()).compareTo(ZERO) > 0
                ? actualGm.multiply(new BigDecimal("100"))
                .divide(nullSafe(r.actualRevenue()), 2, RoundingMode.HALF_UP)
                : null;
        int hcForAvg = r.actualBillableHc() != null && r.actualBillableHc() > 0
                ? r.actualBillableHc()
                : 0;
        BigDecimal avgSalary = r.actualSalaryCost() != null && hcForAvg > 0
                ? divide(r.actualSalaryCost(), new BigDecimal(hcForAvg))
                : null;
        return new BuMetricRow(
                r.customerId(),
                r.customerCode(),
                r.customerName(),
                r.internal(),
                r.plannedRevenue(),
                r.actualRevenue(),
                r.plannedSalaryCost(),
                r.actualSalaryCost(),
                r.plannedBillableHc(),
                r.actualBillableHc(),
                plannedGm,
                actualGm,
                plannedGmPct,
                actualGmPct,
                avgSalary);
    }

    private BigDecimal sumNullable(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        return nullSafe(a).add(nullSafe(b));
    }

    private Integer sumNullableInt(Integer a, Integer b) {
        if (a == null && b == null) return null;
        return (a != null ? a : 0) + (b != null ? b : 0);
    }

    private int getFiscalMonth(LocalDate date) {
        int month = date.getMonthValue();
        return month >= 4 ? month - 3 : month + 9;
    }

    private MoneyTriad triad(BigDecimal plan, BigDecimal actual) {
        return new MoneyTriad(plan, actual, subtract(actual, plan));
    }

    private MoneyTriad zero() {
        return new MoneyTriad(ZERO, ZERO, ZERO);
    }

    private MoneyTriad add(MoneyTriad a, MoneyTriad b) {
        return new MoneyTriad(
                a.plan().add(b.plan()),
                a.actual().add(b.actual()),
                a.variance().add(b.variance())
        );
    }

    private BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private int nullSafeInt(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * Public API for Revenue Dashboard — planned revenue for a client/period from the
     * active primary baseline covering that calendar month (ADR-039).
     */
    public Optional<ClientRevenuePlanView> getClientRevenuePlan(UUID customerId, int month, int year) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        LocalDate asOf = LocalDate.of(year, month, 1);
        Optional<FinancialYearPlan> planOpt = financialYearPlanRepository
                .findByFiscalYearStartLessThanEqualAndFiscalYearEndGreaterThanEqual(asOf, asOf);
        if (planOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<ForecastVersion> baseline = getActiveBaseline(planOpt.get().getId());
        if (baseline.isEmpty()) {
            return Optional.empty();
        }
        return clientRevenuePlanRepository
                .findByForecastVersionIdAndPlanMonthAndPlanYear(baseline.get().getId(), month, year)
                .stream()
                .filter(p -> customerId.equals(p.getCustomerId()))
                .findFirst()
                .map(p -> new ClientRevenuePlanView(
                        p.getCustomerId(),
                        p.getPlanMonth(),
                        p.getPlanYear(),
                        nullSafe(p.getPlannedTmRevenue()),
                        nullSafe(p.getPlannedFixedBidRevenue()),
                        nullSafe(p.getPlannedTmRevenue()).add(nullSafe(p.getPlannedFixedBidRevenue()))));
    }

    /** Cross-module view of planned client revenue (Revenue module — ADR-039). */
    public record ClientRevenuePlanView(
            UUID customerId,
            int planMonth,
            int planYear,
            BigDecimal plannedTmRevenue,
            BigDecimal plannedFixedBidRevenue,
            BigDecimal plannedTotal
    ) {}

    // ── Backup / restore (ADR-044 Tier 2) ────────────────────────────────────

    public List<BackupSheet> exportBackupSheets() {
        return budgetingModuleBackup.exportBackupSheets();
    }

    @Transactional
    public void wipeForRestore() {
        budgetingModuleBackup.wipeBudgetingData();
    }

    @Transactional
    public Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        return budgetingModuleBackup.restoreBackupSheets(rowsByFile);
    }
}
