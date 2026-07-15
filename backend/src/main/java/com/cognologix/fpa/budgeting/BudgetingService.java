package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.dto.BudgetingDtos.*;
import com.cognologix.fpa.budgeting.repository.*;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.CustomerService.BuCustomerRef;
import com.cognologix.fpa.customer.CustomerService.CustomerRef;
import com.cognologix.fpa.people.PeriodFinalisedEvent;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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

    @Transactional
    public FinancialYearPlan createFinancialYearPlan(String fiscalYear, int openingHc) {
        return createFinancialYearPlan(fiscalYear, openingHc, "system");
    }

    @Transactional
    public FinancialYearPlan createFinancialYearPlan(String fiscalYear, int openingHc, String createdBy) {
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

        FinancialYearPlan plan = FinancialYearPlan.builder()
                .fiscalYear(normalized)
                .fiscalYearStart(LocalDate.of(startYear, 4, 1))
                .fiscalYearEnd(LocalDate.of(endYear, 3, 31))
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
        hcPlanRepository.deleteByForecastVersionId(versionId);
        for (HcPlan plan : hcPlans) {
            plan.setId(null);
            plan.setForecastVersion(version);
            hcPlanRepository.save(plan);
        }
    }

    @Transactional
    public void upsertSalaryBudget(UUID planId, UUID typeId, UUID versionId, List<SalaryBudget> salaryBudgets) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        salaryBudgetRepository.deleteByForecastVersionId(versionId);
        for (SalaryBudget budget : salaryBudgets) {
            budget.setId(null);
            budget.setForecastVersion(version);
            salaryBudgetRepository.save(budget);
        }
    }

    @Transactional
    public void upsertRevenuePlan(UUID planId, UUID typeId, UUID versionId, List<ClientRevenuePlan> revenuePlans) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        clientRevenuePlanRepository.deleteByForecastVersionId(versionId);
        for (ClientRevenuePlan plan : revenuePlans) {
            plan.setId(null);
            plan.setForecastVersion(version);
            clientRevenuePlanRepository.save(plan);
        }
    }

    @Transactional
    public void upsertOverheadBudget(UUID planId, UUID typeId, UUID versionId, List<OverheadBudget> overheadBudgets) {
        ForecastVersion version = validateDraftVersion(planId, typeId, versionId);
        overheadBudgetRepository.deleteByForecastVersionId(versionId);
        for (OverheadBudget budget : overheadBudgets) {
            budget.setId(null);
            budget.setForecastVersion(version);
            overheadBudgetRepository.save(budget);
        }
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

    public List<OverheadLineItem> listOverheadLineItems() {
        return overheadLineItemRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public void upsertRevenueActuals(UUID planId, int month, int year, BigDecimal manualTotal,
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
        periodActualsRepository.save(actuals);

        clientRevenueActualRepository.deleteByPlanMonthYear(planId, month, year);
        if (byClient != null && !byClient.isEmpty()) {
            for (ClientRevenueActual entry : byClient) {
                ClientRevenueActual actual = ClientRevenueActual.builder()
                        .financialYearPlan(plan)
                        .customerId(entry.getCustomerId())
                        .actualsMonth(month)
                        .actualsYear(year)
                        .actualRevenue(entry.getActualRevenue())
                        .enteredBy(enteredBy)
                        .build();
                clientRevenueActualRepository.save(actual);
            }
        }
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

        overheadActualsRepository.deleteByPlanMonthYear(planId, month, year);
        if (lineItems != null && !lineItems.isEmpty()) {
            for (OverheadActuals item : lineItems) {
                OverheadActuals overhead = OverheadActuals.builder()
                        .financialYearPlan(plan)
                        .actualsMonth(month)
                        .actualsYear(year)
                        .overheadLine(item.getOverheadLine())
                        .actualAmount(item.getActualAmount())
                        .enteredBy(enteredBy)
                        .build();
                overheadActualsRepository.save(overhead);
            }
        }
    }

    public Optional<PeriodActuals> getPeriodActualsDetail(UUID planId, int month, int year) {
        return periodActualsRepository.findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);
    }

    public RollingForecastResult getRollingForecast(UUID financialYearPlanId) {
        FinancialYearPlan plan = financialYearPlanRepository.findById(financialYearPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + financialYearPlanId));

        ForecastVersion baseline = getActiveBaseline(financialYearPlanId)
                .orElseThrow(() -> new IllegalStateException(
                        "No ACTIVE baseline (Normal) version for plan: " + financialYearPlanId));

        List<MonthlyFinancials> months = new ArrayList<>();
        for (LocalDate month = plan.getFiscalYearStart();
             !month.isAfter(plan.getFiscalYearEnd());
             month = month.plusMonths(1)) {

            Optional<PeriodActuals> actualsOpt = periodActualsRepository
                    .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                            financialYearPlanId, month.getMonthValue(), month.getYear());

            if (actualsOpt.isPresent()) {
                months.add(buildMonthlyFinancialsFromActuals(plan, actualsOpt.get(), month));
            } else {
                months.add(buildMonthlyFinancialsFromPlan(plan, baseline, month));
            }
        }

        return new RollingForecastResult(financialYearPlanId, plan.getFiscalYear(), baseline.getId(), months);
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
        RollingForecastResult rolling = getRollingForecast(financialYearPlanId);
        FinancialYearPlan plan = financialYearPlanRepository.findById(financialYearPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        ForecastVersion baseline = getActiveBaseline(financialYearPlanId)
                .orElseThrow(() -> new IllegalStateException("No ACTIVE baseline"));

        List<MonthlyFinancials> deltaMonths = new ArrayList<>();
        for (MonthlyFinancials rollingMonth : rolling.months()) {
            LocalDate monthDate = LocalDate.of(rollingMonth.year(), rollingMonth.month(), 1);
            MonthlyFinancials baselineMonth = buildMonthlyFinancialsFromPlan(plan, baseline, monthDate);

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
                    subtract(rollingMonth.ebitda(), baselineMonth.ebitda())
            );
            deltaMonths.add(delta);
        }

        return new DeltaResult(financialYearPlanId, plan.getFiscalYear(), baseline.getId(), deltaMonths);
    }

    public PlanVsActualResult getPlanVsActual(UUID financialYearPlanId) {
        return getPlanVsActual(financialYearPlanId, null);
    }

    public PlanVsActualResult getPlanVsActual(UUID financialYearPlanId, UUID forecastTypeId) {
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

        List<MonthlyPlanVsActual> months = new ArrayList<>();
        MoneyTriad q1Rev = zero(), q2Rev = zero(), q3Rev = zero(), q4Rev = zero();
        MoneyTriad q1Sal = zero(), q2Sal = zero(), q3Sal = zero(), q4Sal = zero();
        MoneyTriad q1Ovh = zero(), q2Ovh = zero(), q3Ovh = zero(), q4Ovh = zero();
        MoneyTriad q1Cogs = zero(), q2Cogs = zero(), q3Cogs = zero(), q4Cogs = zero();
        MoneyTriad q1Gp = zero(), q2Gp = zero(), q3Gp = zero(), q4Gp = zero();
        MoneyTriad q1Ebitda = zero(), q2Ebitda = zero(), q3Ebitda = zero(), q4Ebitda = zero();

        for (LocalDate month = plan.getFiscalYearStart();
             !month.isAfter(plan.getFiscalYearEnd());
             month = month.plusMonths(1)) {

            MonthlyFinancials planMonth = buildMonthlyFinancialsFromPlan(plan, baseline, month);
            Optional<PeriodActuals> actualsOpt = periodActualsRepository
                    .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
                            financialYearPlanId, month.getMonthValue(), month.getYear());

            MonthlyFinancials actualMonth = actualsOpt.isPresent()
                    ? buildMonthlyFinancialsFromActuals(plan, actualsOpt.get(), month)
                    : nullMonth(month);

            MonthlyPlanVsActual pva = buildMonthlyPlanVsActual(planMonth, actualMonth, actualsOpt.isPresent());
            months.add(pva);

            int fiscalMonth = getFiscalMonth(month);
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

        return new PlanVsActualResult(financialYearPlanId, plan.getFiscalYear(), baseline.getId(), months, q1, q2, q3, q4, fy);
    }

    public CostPerEmployeeResult getCostPerEmployee(UUID planId, int month, int year) {
        return getCostPerEmployee(planId, month, year, null);
    }

    public CostPerEmployeeResult getCostPerEmployee(UUID planId, int month, int year, UUID forecastTypeId) {
        financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        Optional<PeriodActuals> actualsOpt = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);

        boolean fromActuals = actualsOpt.isPresent();
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
            overheadLines = overheadActualsRepository
                    .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year)
                    .stream()
                    .map(o -> new OverheadLineFigures(o.getOverheadLine(), o.getActualAmount()))
                    .toList();
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

        CategoryCost billableCost = computeCategoryLayers("Billable", hc.billableHc(),
                salary.billable(), overheadMap, hc.totalHc(), hc.billableHc());
        CategoryCost benchCost = computeCategoryLayers("Bench", hc.benchHc(),
                salary.bench(), overheadMap, hc.totalHc(), hc.billableHc());
        CategoryCost supportCost = computeCategoryLayers("Support", hc.supportHc(),
                salary.support(), overheadMap, hc.totalHc(), 0);
        CategoryCost leadershipCost = computeCategoryLayers("Leadership", hc.leadershipHc(),
                salary.seniorMgmt(), overheadMap, hc.totalHc(), 0);

        return new CostPerEmployeeResult(planId, month, year, fromActuals,
                billableCost, benchCost, supportCost, leadershipCost, billableCost.total());
    }

    public BuMetricsResult getBuMetrics(UUID planId, int month, int year) {
        return getBuMetrics(planId, month, year, null);
    }

    public BuMetricsResult getBuMetrics(UUID planId, int month, int year, UUID forecastTypeId) {
        financialYearPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

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

        List<ClientRevenueActual> actualRevenues = clientRevenueActualRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);
        Map<UUID, BigDecimal> actualRevenueMap = actualRevenues.stream()
                .collect(Collectors.toMap(
                        ClientRevenueActual::getCustomerId,
                        ClientRevenueActual::getActualRevenue,
                        BigDecimal::add
                ));

        HcFigures plannedHc = getPlannedHcForMonth(baseline.getId(), monthDate);
        int totalPlannedBillableHc = plannedHc.billableHc();
        BigDecimal totalPlannedExternalRevenue = customers.stream()
                .filter(c -> !c.internal())
                .map(c -> plannedRevenueMap.getOrDefault(c.id(), ZERO))
                .reduce(ZERO, BigDecimal::add);

        Optional<PeriodActuals> actualsOpt = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(planId, month, year);
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
            BigDecimal actualSalaryCost = buActual != null ? buActual.getTotalGrossPay() : null;

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

        return new BuMetricsResult(planId, month, year, rows);
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
                        .totalGrossPay(buActual.totalGrossPay())
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

        BigDecimal billableSalary = nullSafe(actuals.getActualBillableSalaries());
        BigDecimal benchSalary = nullSafe(actuals.getActualBenchSalaries());
        BigDecimal supportSalary = nullSafe(actuals.getActualSupportSalaries());
        BigDecimal cofoundersSalary = nullSafe(actuals.getActualManagementSalaries());
        BigDecimal seniorMgmtSalary = nullSafe(actuals.getActualLeadershipSalaries());

        SalaryFigures salary = new SalaryFigures(
                billableSalary, benchSalary, supportSalary, cofoundersSalary, seniorMgmtSalary,
                billableSalary.add(benchSalary).add(supportSalary).add(cofoundersSalary).add(seniorMgmtSalary)
        );

        List<ClientRevenueActual> revenueActuals = clientRevenueActualRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(plan.getId(), month, year);

        List<ClientRevenueFigures> revenueByClient;
        if (!revenueActuals.isEmpty()) {
            revenueByClient = revenueActuals.stream()
                    .map(ra -> {
                        Optional<CustomerRef> custOpt = customerService.findCustomerRef(ra.getCustomerId());
                        return new ClientRevenueFigures(
                                ra.getCustomerId(),
                                custOpt.map(CustomerRef::customerCode).orElse("UNKNOWN"),
                                custOpt.map(CustomerRef::customerName).orElse("Unknown Customer"),
                                ZERO,
                                ra.getActualRevenue(),
                                ra.getActualRevenue()
                        );
                    })
                    .toList();
        } else if (actuals.getActualRevenueManual() != null) {
            revenueByClient = List.of(new ClientRevenueFigures(
                    null, "MANUAL", "Manual revenue total", ZERO, actuals.getActualRevenueManual(),
                    actuals.getActualRevenueManual()
            ));
        } else {
            revenueByClient = List.of();
        }

        BigDecimal totalRevenue = revenueByClient.stream()
                .map(ClientRevenueFigures::totalRevenue)
                .reduce(ZERO, BigDecimal::add);

        List<OverheadActuals> overheadActuals = overheadActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(plan.getId(), month, year);
        List<OverheadLineFigures> overhead = overheadActuals.stream()
                .map(oa -> new OverheadLineFigures(oa.getOverheadLine(), oa.getActualAmount()))
                .toList();

        BigDecimal totalOverhead = overhead.stream()
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        return computeFinancials(month, year, true, hc, salary, revenueByClient, totalRevenue, overhead, totalOverhead);
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

        return computeFinancials(month, year, false, hc, salary, revenueByClient, totalRevenue, overhead, totalOverhead);
    }

    private MonthlyFinancials computeFinancials(int month, int year, boolean fromActuals,
                                                HcFigures hc, SalaryFigures salary,
                                                List<ClientRevenueFigures> revenueByClient, BigDecimal totalRevenue,
                                                List<OverheadLineFigures> overhead, BigDecimal totalOverhead) {
        BigDecimal totalSalaryCost = salary.total();
        BigDecimal statutoryBenefits = totalSalaryCost.multiply(STATUTORY_RATE).setScale(2, RoundingMode.HALF_UP);

        BigDecimal variablePay = ZERO;
        if (VARIABLE_PAY_MONTHS.contains(month)) {
            BigDecimal variableBase = salary.cofounders().add(salary.seniorMgmt());
            variablePay = variableBase.multiply(VARIABLE_PAY_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal deliveryOverhead = overhead.stream()
                .filter(o -> DELIVERY_OVERHEAD_LINES.contains(o.lineCode()))
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalCogs = salary.billable().add(salary.bench()).add(deliveryOverhead);
        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);

        BigDecimal nonDeliveryOverhead = overhead.stream()
                .filter(o -> !DELIVERY_OVERHEAD_LINES.contains(o.lineCode()))
                .map(OverheadLineFigures::amount)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalOpex = salary.support().add(salary.seniorMgmt()).add(salary.cofounders())
                .add(statutoryBenefits).add(variablePay).add(nonDeliveryOverhead);

        BigDecimal ebitda = grossProfit.subtract(totalOpex);

        return new MonthlyFinancials(
                month, year, fromActuals, hc, salary, revenueByClient, totalRevenue,
                overhead, totalOverhead, totalSalaryCost, statutoryBenefits, variablePay,
                totalCogs, grossProfit, totalOpex, ebitda
        );
    }

    private MonthlyFinancials nullMonth(LocalDate monthDate) {
        int month = monthDate.getMonthValue();
        int year = monthDate.getYear();
        HcFigures zeroHc = new HcFigures(0, 0, 0, 0, 0, 0);
        SalaryFigures zeroSalary = new SalaryFigures(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
        return new MonthlyFinancials(month, year, false, zeroHc, zeroSalary, List.of(), ZERO,
                List.of(), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
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
                                                         boolean hasActuals) {
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

        return new MonthlyPlanVsActual(plan.month(), plan.year(), hasActuals, hc, salary, revenueByClient,
                totalRevenue, overheadTriad, totalOverhead, totalSalaryCost, statutoryBenefits,
                totalCogs, grossProfit, ebitda);
    }

    private CategoryCost computeCategoryLayers(String category, int headcount, BigDecimal salary,
                                               Map<String, BigDecimal> overheadMap, int totalHc, int billableHc) {
        if (headcount == 0) {
            return new CategoryCost(category, 0, ZERO, ZERO, ZERO, ZERO);
        }

        BigDecimal avgSalary = divide(salary, new BigDecimal(Math.max(1, headcount)));
        BigDecimal layer1 = avgSalary.add(avgSalary.multiply(STATUTORY_RATE));

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
        return new CategoryCost(category, headcount, layer1, layer2, layer3, total);
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
}
