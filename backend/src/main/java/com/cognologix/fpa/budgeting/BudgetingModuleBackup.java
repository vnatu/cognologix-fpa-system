package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.repository.*;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.general.BackupSheet;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.cognologix.fpa.general.BackupGridHelper.*;

/**
 * Backup/restore grid operations for Budgeting &amp; Forecasting (ADR-044 Tier 2).
 */
@Component
@RequiredArgsConstructor
class BudgetingModuleBackup {

    static final String FILE_OVERHEAD_LINE_ITEMS = "overhead_line_items.xlsx";
    static final String FILE_FY_PLANS = "financial_year_plans.xlsx";
    static final String FILE_FORECAST_TYPES = "forecast_types.xlsx";
    static final String FILE_FORECAST_VERSIONS = "forecast_versions.xlsx";
    static final String FILE_HC_PLAN = "hc_plan.xlsx";
    static final String FILE_SALARY_BUDGET = "salary_budget.xlsx";
    static final String FILE_CLIENT_REVENUE_PLAN = "client_revenue_plan.xlsx";
    static final String FILE_OVERHEAD_BUDGET = "overhead_budget.xlsx";
    static final String FILE_PERIOD_ACTUALS = "period_actuals.xlsx";
    static final String FILE_OVERHEAD_ACTUALS = "overhead_actuals.xlsx";

    private final OverheadLineItemRepository overheadLineItemRepository;
    private final FinancialYearPlanRepository financialYearPlanRepository;
    private final ForecastTypeRepository forecastTypeRepository;
    private final ForecastVersionRepository forecastVersionRepository;
    private final HcPlanRepository hcPlanRepository;
    private final SalaryBudgetRepository salaryBudgetRepository;
    private final ClientRevenuePlanRepository clientRevenuePlanRepository;
    private final OverheadBudgetRepository overheadBudgetRepository;
    private final PeriodActualsRepository periodActualsRepository;
    private final PeriodBuActualsRepository periodBuActualsRepository;
    private final ClientRevenueActualRepository clientRevenueActualRepository;
    private final OverheadActualsRepository overheadActualsRepository;
    private final CustomerService customerService;

    List<BackupSheet> exportBackupSheets() {
        return List.of(
                exportOverheadLineItemsSheet(),
                exportFyPlansSheet(),
                exportForecastTypesSheet(),
                exportForecastVersionsSheet(),
                exportHcPlanSheet(),
                exportSalaryBudgetSheet(),
                exportClientRevenuePlanSheet(),
                exportOverheadBudgetSheet(),
                exportPeriodActualsSheet(),
                exportOverheadActualsSheet());
    }

    BackupSheet exportOverheadLineItemsSheet() {
        List<String[]> rows = overheadLineItemRepository.findAll().stream()
                .sorted(Comparator.comparing(OverheadLineItem::getSortOrder))
                .map(o -> row(o.getLineCode(), o.getCategory(), o.getDisplayName(), str(o.getSortOrder())))
                .toList();
        return new BackupSheet(FILE_OVERHEAD_LINE_ITEMS,
                new String[]{"line_code", "category", "display_name", "sort_order"}, rows);
    }

    BackupSheet exportFyPlansSheet() {
        List<String[]> rows = financialYearPlanRepository.findAll().stream()
                .sorted(Comparator.comparing(FinancialYearPlan::getFiscalYear))
                .map(p -> row(
                        p.getFiscalYear(),
                        p.getFiscalYearStart().toString(),
                        p.getFiscalYearEnd().toString(),
                        str(p.getOpeningHc())))
                .toList();
        return new BackupSheet(FILE_FY_PLANS,
                new String[]{"fiscal_year", "fiscal_year_start", "fiscal_year_end", "opening_hc"}, rows);
    }

    BackupSheet exportForecastTypesSheet() {
        List<String[]> rows = new ArrayList<>();
        for (FinancialYearPlan plan : financialYearPlanRepository.findAll()) {
            Hibernate.initialize(plan.getForecastTypes());
            for (ForecastType type : plan.getForecastTypes()) {
                rows.add(row(plan.getFiscalYear(), type.getTypeName(), String.valueOf(type.isPrimary())));
            }
        }
        return new BackupSheet(FILE_FORECAST_TYPES,
                new String[]{"fiscal_year", "type_name", "is_primary"}, rows);
    }

    BackupSheet exportForecastVersionsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ForecastVersion v : forecastVersionRepository.findAll()) {
            Hibernate.initialize(v.getForecastType());
            Hibernate.initialize(v.getForecastType().getFinancialYearPlan());
            rows.add(row(
                    v.getForecastType().getFinancialYearPlan().getFiscalYear(),
                    v.getForecastType().getTypeName(),
                    str(v.getVersionNumber()),
                    v.getStatus().name(),
                    v.getPublishedAt() != null ? v.getPublishedAt().toString() : "",
                    str(v.getPublishedBy()),
                    v.getSupersededAt() != null ? v.getSupersededAt().toString() : ""));
        }
        return new BackupSheet(FILE_FORECAST_VERSIONS,
                new String[]{"fiscal_year", "type_name", "version_number", "status",
                        "published_at", "published_by", "superseded_at"},
                rows);
    }

    BackupSheet exportHcPlanSheet() {
        return exportVersionKeyedPlan(FILE_HC_PLAN, hcPlanRepository.findAll(), (v, h) -> {
            String[] vk = versionKey(v);
            return row(vk[0], vk[1], vk[2], str(h.getPlanMonth()), str(h.getPlanYear()),
                    str(h.getPlannedHires()), str(h.getPlannedExits()),
                    str(h.getPlannedBillableHc()), str(h.getPlannedBenchHc()),
                    str(h.getPlannedSupportHc()), str(h.getPlannedLeadershipHc()),
                    str(h.getPlannedManagementHc()));
        });
    }

    BackupSheet exportSalaryBudgetSheet() {
        return exportVersionKeyedPlan(FILE_SALARY_BUDGET, salaryBudgetRepository.findAll(), (v, s) -> {
            String[] vk = versionKey(v);
            return row(vk[0], vk[1], vk[2], str(s.getPlanMonth()), str(s.getPlanYear()),
                    s.getBillableSalaries().toPlainString(), s.getBenchSalaries().toPlainString(),
                    s.getSupportSalaries().toPlainString(), s.getCofoundersSalaries().toPlainString(),
                    s.getSeniorMgmtSalaries().toPlainString());
        });
    }

    BackupSheet exportClientRevenuePlanSheet() {
        List<String[]> rows = new ArrayList<>();
        for (ClientRevenuePlan crp : clientRevenuePlanRepository.findAll()) {
            Hibernate.initialize(crp.getForecastVersion());
            ForecastVersion v = crp.getForecastVersion();
            Hibernate.initialize(v.getForecastType());
            Hibernate.initialize(v.getForecastType().getFinancialYearPlan());
            String[] vk = versionKey(v);
            String customerCode = customerService.findCustomerRef(crp.getCustomerId())
                    .map(CustomerService.CustomerRef::customerCode)
                    .orElse("");
            rows.add(row(vk[0], vk[1], vk[2], str(crp.getPlanMonth()), str(crp.getPlanYear()),
                    customerCode,
                    crp.getPlannedTmRevenue().toPlainString(),
                    crp.getPlannedFixedBidRevenue().toPlainString()));
        }
        return new BackupSheet(FILE_CLIENT_REVENUE_PLAN,
                new String[]{"fiscal_year", "type_name", "version_number", "plan_month", "plan_year",
                        "customer_code", "planned_tm_revenue", "planned_fixed_bid_revenue"},
                rows);
    }

    BackupSheet exportOverheadBudgetSheet() {
        List<String[]> rows = new ArrayList<>();
        for (OverheadBudget ob : overheadBudgetRepository.findAll()) {
            Hibernate.initialize(ob.getForecastVersion());
            ForecastVersion v = ob.getForecastVersion();
            Hibernate.initialize(v.getForecastType());
            Hibernate.initialize(v.getForecastType().getFinancialYearPlan());
            String[] vk = versionKey(v);
            rows.add(row(vk[0], vk[1], vk[2], str(ob.getPlanMonth()), str(ob.getPlanYear()),
                    ob.getOverheadLine(), ob.getAmount().toPlainString()));
        }
        return new BackupSheet(FILE_OVERHEAD_BUDGET,
                new String[]{"fiscal_year", "type_name", "version_number", "plan_month", "plan_year",
                        "overhead_line", "amount"},
                rows);
    }

    BackupSheet exportPeriodActualsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (PeriodActuals pa : periodActualsRepository.findAll()) {
            Hibernate.initialize(pa.getFinancialYearPlan());
            rows.add(row(
                    pa.getFinancialYearPlan().getFiscalYear(),
                    str(pa.getActualsMonth()), str(pa.getActualsYear()),
                    str(pa.getActualBillableHc()), str(pa.getActualBenchHc()), str(pa.getActualSupportHc()),
                    str(pa.getActualLeadershipHc()), str(pa.getActualManagementHc()), str(pa.getActualTotalHc()),
                    decimal(pa.getActualBillableSalaries()), decimal(pa.getActualBenchSalaries()),
                    decimal(pa.getActualSupportSalaries()), decimal(pa.getActualLeadershipSalaries()),
                    decimal(pa.getActualManagementSalaries())));
        }
        return new BackupSheet(FILE_PERIOD_ACTUALS,
                new String[]{"fiscal_year", "actuals_month", "actuals_year",
                        "actual_billable_hc", "actual_bench_hc", "actual_support_hc",
                        "actual_leadership_hc", "actual_management_hc", "actual_total_hc",
                        "actual_billable_salaries", "actual_bench_salaries", "actual_support_salaries",
                        "actual_leadership_salaries", "actual_management_salaries"},
                rows);
    }

    BackupSheet exportOverheadActualsSheet() {
        List<String[]> rows = new ArrayList<>();
        for (OverheadActuals oa : overheadActualsRepository.findAll()) {
            Hibernate.initialize(oa.getFinancialYearPlan());
            rows.add(row(
                    oa.getFinancialYearPlan().getFiscalYear(),
                    str(oa.getActualsMonth()), str(oa.getActualsYear()),
                    oa.getOverheadLine(), oa.getActualAmount().toPlainString()));
        }
        return new BackupSheet(FILE_OVERHEAD_ACTUALS,
                new String[]{"fiscal_year", "actuals_month", "actuals_year", "overhead_line", "actual_amount"},
                rows);
    }

    @Transactional
    void wipeBudgetingData() {
        overheadActualsRepository.deleteAllInBatch();
        periodBuActualsRepository.deleteAllInBatch();
        clientRevenueActualRepository.deleteAllInBatch();
        periodActualsRepository.deleteAllInBatch();
        overheadBudgetRepository.deleteAllInBatch();
        clientRevenuePlanRepository.deleteAllInBatch();
        salaryBudgetRepository.deleteAllInBatch();
        hcPlanRepository.deleteAllInBatch();
        forecastVersionRepository.deleteAllInBatch();
        forecastTypeRepository.deleteAllInBatch();
        financialYearPlanRepository.deleteAllInBatch();
        overheadLineItemRepository.deleteAllInBatch();
    }

    @Transactional
    Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(FILE_OVERHEAD_LINE_ITEMS,
                restoreOverheadLineItems(rowsByFile.getOrDefault(FILE_OVERHEAD_LINE_ITEMS, List.of())));
        Map<String, FinancialYearPlan> plans = restoreFyPlans(
                rowsByFile.getOrDefault(FILE_FY_PLANS, List.of()));
        counts.put(FILE_FY_PLANS, plans.size());
        Map<ForecastTypeKey, ForecastType> types = restoreForecastTypes(
                rowsByFile.getOrDefault(FILE_FORECAST_TYPES, List.of()), plans);
        counts.put(FILE_FORECAST_TYPES, types.size());
        Map<VersionKey, ForecastVersion> versions = restoreForecastVersions(
                rowsByFile.getOrDefault(FILE_FORECAST_VERSIONS, List.of()), types);
        counts.put(FILE_FORECAST_VERSIONS, versions.size());
        counts.put(FILE_HC_PLAN, restoreHcPlan(rowsByFile.getOrDefault(FILE_HC_PLAN, List.of()), versions));
        counts.put(FILE_SALARY_BUDGET,
                restoreSalaryBudget(rowsByFile.getOrDefault(FILE_SALARY_BUDGET, List.of()), versions));
        counts.put(FILE_CLIENT_REVENUE_PLAN,
                restoreClientRevenuePlan(rowsByFile.getOrDefault(FILE_CLIENT_REVENUE_PLAN, List.of()), versions));
        counts.put(FILE_OVERHEAD_BUDGET,
                restoreOverheadBudget(rowsByFile.getOrDefault(FILE_OVERHEAD_BUDGET, List.of()), versions));
        counts.put(FILE_PERIOD_ACTUALS,
                restorePeriodActuals(rowsByFile.getOrDefault(FILE_PERIOD_ACTUALS, List.of()), plans));
        counts.put(FILE_OVERHEAD_ACTUALS,
                restoreOverheadActuals(rowsByFile.getOrDefault(FILE_OVERHEAD_ACTUALS, List.of()), plans));
        return counts;
    }

    private int restoreOverheadLineItems(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                OverheadLineItem item = new OverheadLineItem();
                item.setLineCode(requireCell(row, 0, "line_code"));
                item.setCategory(requireCell(row, 1, "category"));
                item.setDisplayName(requireCell(row, 2, "display_name"));
                item.setSortOrder(parseIntRequired(cell(row, 3), "sort_order"));
                overheadLineItemRepository.save(item);
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private Map<String, FinancialYearPlan> restoreFyPlans(List<String[]> rows) {
        Map<String, FinancialYearPlan> result = new HashMap<>();
        for (String[] row : rows) {
            try {
                FinancialYearPlan plan = FinancialYearPlan.builder()
                        .fiscalYear(requireCell(row, 0, "fiscal_year"))
                        .fiscalYearStart(parseDate(requireCell(row, 1, "fiscal_year_start"), "fiscal_year_start"))
                        .fiscalYearEnd(parseDate(requireCell(row, 2, "fiscal_year_end"), "fiscal_year_end"))
                        .openingHc(parseIntRequired(cell(row, 3), "opening_hc"))
                        .createdBy("restore")
                        .build();
                plan = financialYearPlanRepository.save(plan);
                result.put(plan.getFiscalYear(), plan);
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return result;
    }

    private Map<ForecastTypeKey, ForecastType> restoreForecastTypes(
            List<String[]> rows, Map<String, FinancialYearPlan> plans) {
        Map<ForecastTypeKey, ForecastType> result = new HashMap<>();
        for (String[] row : rows) {
            try {
                String fy = requireCell(row, 0, "fiscal_year");
                FinancialYearPlan plan = plans.get(fy);
                if (plan == null) {
                    continue;
                }
                ForecastType type = ForecastType.builder()
                        .financialYearPlan(plan)
                        .typeName(requireCell(row, 1, "type_name"))
                        .primary(parseBoolean(cell(row, 2)))
                        .build();
                type = forecastTypeRepository.save(type);
                result.put(new ForecastTypeKey(fy, type.getTypeName()), type);
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return result;
    }

    private Map<VersionKey, ForecastVersion> restoreForecastVersions(
            List<String[]> rows, Map<ForecastTypeKey, ForecastType> types) {
        Map<VersionKey, ForecastVersion> result = new HashMap<>();
        for (String[] row : rows) {
            try {
                String fy = requireCell(row, 0, "fiscal_year");
                String typeName = requireCell(row, 1, "type_name");
                ForecastType type = types.get(new ForecastTypeKey(fy, typeName));
                if (type == null) {
                    continue;
                }
                ForecastVersion version = ForecastVersion.builder()
                        .forecastType(type)
                        .versionNumber(parseIntRequired(cell(row, 2), "version_number"))
                        .status(ForecastVersionStatus.valueOf(requireCell(row, 3, "status")))
                        .publishedAt(parseInstant(cell(row, 4), "published_at"))
                        .publishedBy(cell(row, 5))
                        .supersededAt(parseInstant(cell(row, 6), "superseded_at"))
                        .createdBy("restore")
                        .build();
                version = forecastVersionRepository.save(version);
                result.put(new VersionKey(fy, typeName, version.getVersionNumber()), version);
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return result;
    }

    private int restoreHcPlan(List<String[]> rows, Map<VersionKey, ForecastVersion> versions) {
        int count = 0;
        for (String[] row : rows) {
            try {
                ForecastVersion version = resolveVersion(row, versions);
                if (version == null) {
                    continue;
                }
                hcPlanRepository.save(HcPlan.builder()
                        .forecastVersion(version)
                        .planMonth(parseIntRequired(cell(row, 3), "plan_month"))
                        .planYear(parseIntRequired(cell(row, 4), "plan_year"))
                        .plannedHires(parseIntRequired(cell(row, 5), "planned_hires"))
                        .plannedExits(parseIntRequired(cell(row, 6), "planned_exits"))
                        .plannedBillableHc(parseIntRequired(cell(row, 7), "planned_billable_hc"))
                        .plannedBenchHc(parseIntRequired(cell(row, 8), "planned_bench_hc"))
                        .plannedSupportHc(parseIntRequired(cell(row, 9), "planned_support_hc"))
                        .plannedLeadershipHc(parseIntRequired(cell(row, 10), "planned_leadership_hc"))
                        .plannedManagementHc(parseIntRequired(cell(row, 11), "planned_management_hc"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreSalaryBudget(List<String[]> rows, Map<VersionKey, ForecastVersion> versions) {
        int count = 0;
        for (String[] row : rows) {
            try {
                ForecastVersion version = resolveVersion(row, versions);
                if (version == null) {
                    continue;
                }
                salaryBudgetRepository.save(SalaryBudget.builder()
                        .forecastVersion(version)
                        .planMonth(parseIntRequired(cell(row, 3), "plan_month"))
                        .planYear(parseIntRequired(cell(row, 4), "plan_year"))
                        .billableSalaries(parseDecimalRequired(cell(row, 5), "billable_salaries"))
                        .benchSalaries(parseDecimalRequired(cell(row, 6), "bench_salaries"))
                        .supportSalaries(parseDecimalRequired(cell(row, 7), "support_salaries"))
                        .cofoundersSalaries(parseDecimalRequired(cell(row, 8), "cofounders_salaries"))
                        .seniorMgmtSalaries(parseDecimalRequired(cell(row, 9), "senior_mgmt_salaries"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreClientRevenuePlan(List<String[]> rows, Map<VersionKey, ForecastVersion> versions) {
        int count = 0;
        for (String[] row : rows) {
            try {
                ForecastVersion version = resolveVersion(row, versions);
                if (version == null) {
                    continue;
                }
                String customerCode = requireCell(row, 5, "customer_code");
                UUID customerId = customerService.resolveBuCustomer(customerCode)
                        .map(CustomerService.BuCustomerRef::id)
                        .orElse(null);
                if (customerId == null) {
                    continue;
                }
                clientRevenuePlanRepository.save(ClientRevenuePlan.builder()
                        .forecastVersion(version)
                        .customerId(customerId)
                        .planMonth(parseIntRequired(cell(row, 3), "plan_month"))
                        .planYear(parseIntRequired(cell(row, 4), "plan_year"))
                        .plannedTmRevenue(parseDecimalRequired(cell(row, 6), "planned_tm_revenue"))
                        .plannedFixedBidRevenue(parseDecimalRequired(cell(row, 7), "planned_fixed_bid_revenue"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreOverheadBudget(List<String[]> rows, Map<VersionKey, ForecastVersion> versions) {
        int count = 0;
        for (String[] row : rows) {
            try {
                ForecastVersion version = resolveVersion(row, versions);
                if (version == null) {
                    continue;
                }
                overheadBudgetRepository.save(OverheadBudget.builder()
                        .forecastVersion(version)
                        .planMonth(parseIntRequired(cell(row, 3), "plan_month"))
                        .planYear(parseIntRequired(cell(row, 4), "plan_year"))
                        .overheadLine(requireCell(row, 5, "overhead_line"))
                        .amount(parseDecimalRequired(cell(row, 6), "amount"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restorePeriodActuals(List<String[]> rows, Map<String, FinancialYearPlan> plans) {
        int count = 0;
        for (String[] row : rows) {
            try {
                FinancialYearPlan plan = plans.get(requireCell(row, 0, "fiscal_year"));
                if (plan == null) {
                    continue;
                }
                periodActualsRepository.save(PeriodActuals.builder()
                        .financialYearPlan(plan)
                        .actualsMonth(parseIntRequired(cell(row, 1), "actuals_month"))
                        .actualsYear(parseIntRequired(cell(row, 2), "actuals_year"))
                        .actualBillableHc(parseInt(cell(row, 3), "actual_billable_hc"))
                        .actualBenchHc(parseInt(cell(row, 4), "actual_bench_hc"))
                        .actualSupportHc(parseInt(cell(row, 5), "actual_support_hc"))
                        .actualLeadershipHc(parseInt(cell(row, 6), "actual_leadership_hc"))
                        .actualManagementHc(parseInt(cell(row, 7), "actual_management_hc"))
                        .actualTotalHc(parseInt(cell(row, 8), "actual_total_hc"))
                        .actualBillableSalaries(parseDecimal(cell(row, 9), "actual_billable_salaries"))
                        .actualBenchSalaries(parseDecimal(cell(row, 10), "actual_bench_salaries"))
                        .actualSupportSalaries(parseDecimal(cell(row, 11), "actual_support_salaries"))
                        .actualLeadershipSalaries(parseDecimal(cell(row, 12), "actual_leadership_salaries"))
                        .actualManagementSalaries(parseDecimal(cell(row, 13), "actual_management_salaries"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreOverheadActuals(List<String[]> rows, Map<String, FinancialYearPlan> plans) {
        int count = 0;
        for (String[] row : rows) {
            try {
                FinancialYearPlan plan = plans.get(requireCell(row, 0, "fiscal_year"));
                if (plan == null) {
                    continue;
                }
                overheadActualsRepository.save(OverheadActuals.builder()
                        .financialYearPlan(plan)
                        .actualsMonth(parseIntRequired(cell(row, 1), "actuals_month"))
                        .actualsYear(parseIntRequired(cell(row, 2), "actuals_year"))
                        .overheadLine(requireCell(row, 3, "overhead_line"))
                        .actualAmount(parseDecimalRequired(cell(row, 4), "actual_amount"))
                        .enteredBy("restore")
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private ForecastVersion resolveVersion(String[] row, Map<VersionKey, ForecastVersion> versions) {
        String fy = cell(row, 0);
        String typeName = cell(row, 1);
        Integer versionNumber = parseInt(cell(row, 2), "version_number");
        if (fy == null || typeName == null || versionNumber == null) {
            return null;
        }
        return versions.get(new VersionKey(fy, typeName, versionNumber));
    }

    private static String[] versionKey(ForecastVersion v) {
        return new String[]{
                v.getForecastType().getFinancialYearPlan().getFiscalYear(),
                v.getForecastType().getTypeName(),
                str(v.getVersionNumber())
        };
    }

    private static String decimal(BigDecimal v) {
        return v != null ? v.toPlainString() : "";
    }

    private interface VersionPlanRowMapper<T> {
        String[] map(ForecastVersion version, T entity);
    }

    private <T> BackupSheet exportVersionKeyedPlan(
            String fileName, List<T> entities, VersionPlanRowMapper<T> mapper) {
        List<String[]> rows = new ArrayList<>();
        for (T entity : entities) {
            ForecastVersion v = extractVersion(entity);
            if (v == null) {
                continue;
            }
            Hibernate.initialize(v.getForecastType());
            Hibernate.initialize(v.getForecastType().getFinancialYearPlan());
            rows.add(mapper.map(v, entity));
        }
        String[] headers = switch (fileName) {
            case FILE_HC_PLAN -> new String[]{"fiscal_year", "type_name", "version_number", "plan_month", "plan_year",
                    "planned_hires", "planned_exits", "planned_billable_hc", "planned_bench_hc",
                    "planned_support_hc", "planned_leadership_hc", "planned_management_hc"};
            case FILE_SALARY_BUDGET -> new String[]{"fiscal_year", "type_name", "version_number", "plan_month", "plan_year",
                    "billable_salaries", "bench_salaries", "support_salaries", "cofounders_salaries", "senior_mgmt_salaries"};
            default -> throw new IllegalArgumentException("Unknown plan file: " + fileName);
        };
        return new BackupSheet(fileName, headers, rows);
    }

    @SuppressWarnings("unchecked")
    private <T> ForecastVersion extractVersion(T entity) {
        if (entity instanceof HcPlan hc) {
            return hc.getForecastVersion();
        }
        if (entity instanceof SalaryBudget sb) {
            return sb.getForecastVersion();
        }
        return null;
    }

    private record ForecastTypeKey(String fiscalYear, String typeName) {}
    private record VersionKey(String fiscalYear, String typeName, int versionNumber) {}
}
