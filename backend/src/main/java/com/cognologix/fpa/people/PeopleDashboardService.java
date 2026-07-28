package com.cognologix.fpa.people;

import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.people.domain.MasterRecord;
import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.domain.ReconciliationStatus;
import com.cognologix.fpa.people.dto.DashboardPeriodResponse;
import com.cognologix.fpa.people.dto.DashboardSummaryResponse;
import com.cognologix.fpa.people.dto.DashboardTrendMetric;
import com.cognologix.fpa.people.dto.DashboardTrendPointResponse;
import com.cognologix.fpa.people.repository.MasterRecordRepository;
import com.cognologix.fpa.people.repository.PeriodRepository;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * People Analytics Dashboard — aggregates master_record rows per period version (spec §10 / ADR-045).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleDashboardService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PeriodRepository periodRepository;
    private final PeriodVersionRepository periodVersionRepository;
    private final MasterRecordRepository masterRecordRepository;
    private final CustomerService customerService;

    public List<DashboardPeriodResponse> listPeriodsForSelector() {
        List<Period> periods = periodRepository.findAll().stream()
                .sorted(Comparator
                        .comparingInt(Period::getPeriodYear).reversed()
                        .thenComparing(Comparator.comparingInt(Period::getPeriodMonth).reversed()))
                .toList();
        return periods.stream()
                .map(p -> DashboardPeriodResponse.from(
                        p, periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(p.getId())))
                .toList();
    }

    public DashboardSummaryResponse getSummary(UUID periodVersionId) {
        PeriodVersion version = periodVersionRepository.findById(periodVersionId)
                .orElseThrow(() -> new NotFoundException("Period version not found: " + periodVersionId));
        Hibernate.initialize(version.getPeriod());
        Period period = version.getPeriod();

        List<MasterRecord> records = masterRecordRepository.findByPeriodVersionId(periodVersionId);
        List<MasterRecord> active = records.stream().filter(this::countsTowardHeadcount).toList();

        ClassificationBuckets buckets = classify(active);
        BigDecimal billableRatio = pct(buckets.billableHc, buckets.totalHc());

        DashboardSummaryResponse.SalaryMetrics salary = buildSalaryMetrics(buckets);
        List<DashboardSummaryResponse.PuBreakdown> puBreakdown = buildPuBreakdown(active);
        BuBreakdowns buBreakdowns = buildBuBreakdowns(active, salary.total().totalPayrollCost());

        return new DashboardSummaryResponse(
                period.getPeriodMonth(),
                period.getPeriodYear(),
                version.getVersionNumber(),
                version.getStatus(),
                new DashboardSummaryResponse.HeadcountSummary(
                        buckets.totalHc(),
                        buckets.billableHc,
                        buckets.benchHc,
                        buckets.supportHc,
                        buckets.leadershipHc,
                        buckets.managementHc,
                        billableRatio),
                salary,
                puBreakdown,
                buBreakdowns.clients(),
                buBreakdowns.internalBus(),
                buildReconciliationSummary(records),
                buildDataQualitySummary(records));
    }

    public List<DashboardTrendPointResponse> getTrend(
            DashboardTrendMetric metric,
            String practiceUnit,
            String businessUnit) {
        List<PeriodVersion> finalised = periodVersionRepository
                .findByStatusWithPeriodOrdered(PeriodStatus.FINALISED);

        List<DashboardTrendPointResponse> points = new ArrayList<>();
        for (PeriodVersion version : finalised) {
            Period period = version.getPeriod();
            List<MasterRecord> active = masterRecordRepository.findByPeriodVersionId(version.getId())
                    .stream()
                    .filter(this::countsTowardHeadcount)
                    .filter(r -> matchesTrendFilter(r, practiceUnit, businessUnit))
                    .toList();

            ClassificationBuckets buckets = classify(active);
            BigDecimal value = metricValue(metric, buckets);
            points.add(new DashboardTrendPointResponse(
                    period.getPeriodMonth(),
                    period.getPeriodYear(),
                    version.getVersionNumber(),
                    value));
        }
        return points;
    }

    private boolean countsTowardHeadcount(MasterRecord r) {
        return r.getReconciliationStatus() != ReconciliationStatus.AUTO_MATCHED_EXITED
                && r.getReconciliationStatus() != ReconciliationStatus.UNMATCHED;
    }

    private boolean matchesTrendFilter(MasterRecord r, String practiceUnit, String businessUnit) {
        if (practiceUnit != null && !practiceUnit.isBlank()
                && !practiceUnit.equalsIgnoreCase(nullToEmpty(r.getPracticeUnit()))) {
            return false;
        }
        if (businessUnit != null && !businessUnit.isBlank()
                && !businessUnit.equalsIgnoreCase(nullToEmpty(r.getBusinessUnit()))) {
            return false;
        }
        return true;
    }

    private BigDecimal metricValue(DashboardTrendMetric metric, ClassificationBuckets buckets) {
        return switch (metric) {
            case BILLABLE_HC -> BigDecimal.valueOf(buckets.billableHc);
            case BENCH_HC -> BigDecimal.valueOf(buckets.benchHc);
            case TOTAL_HC -> BigDecimal.valueOf(buckets.totalHc());
            case BILLABLE_RATIO_PCT -> pct(buckets.billableHc, buckets.totalHc());
            case TOTAL_GROSS_PAY -> buckets.totalPayrollCost();
            case BILLABLE_GROSS_PAY -> buckets.billablePayrollCost;
        };
    }

    private ClassificationBuckets classify(List<MasterRecord> records) {
        ClassificationBuckets b = new ClassificationBuckets();
        for (MasterRecord r : records) {
            BigDecimal gross = r.getGrossPay(); // null excluded from pay totals/averages
            BigDecimal contrib = r.getTotalEmployerContributions() != null
                    ? r.getTotalEmployerContributions() : BigDecimal.ZERO;
            BigDecimal payrollCost = r.resolvedTotalPayrollCost();
            // Salary bucketing priority: Leadership > Management > Billable > Bench > Support (spec §8)
            if (r.isLeadership()) {
                b.leadershipHc++;
                if (gross != null) {
                    b.leadershipGross = b.leadershipGross.add(gross);
                    b.leadershipContrib = b.leadershipContrib.add(contrib);
                    b.leadershipPayrollCost = b.leadershipPayrollCost.add(payrollCost);
                    b.leadershipPaidHc++;
                }
            } else if (r.isManagement()) {
                b.managementHc++;
                if (gross != null) {
                    b.managementGross = b.managementGross.add(gross);
                    b.managementContrib = b.managementContrib.add(contrib);
                    b.managementPayrollCost = b.managementPayrollCost.add(payrollCost);
                    b.managementPaidHc++;
                }
            } else if (r.isBillable()) {
                b.billableHc++;
                if (gross != null) {
                    b.billableGross = b.billableGross.add(gross);
                    b.billableContrib = b.billableContrib.add(contrib);
                    b.billablePayrollCost = b.billablePayrollCost.add(payrollCost);
                    b.billablePaidHc++;
                }
            } else if (r.isBench()) {
                b.benchHc++;
                if (gross != null) {
                    b.benchGross = b.benchGross.add(gross);
                    b.benchContrib = b.benchContrib.add(contrib);
                    b.benchPayrollCost = b.benchPayrollCost.add(payrollCost);
                    b.benchPaidHc++;
                }
            } else {
                b.supportHc++;
                if (gross != null) {
                    b.supportGross = b.supportGross.add(gross);
                    b.supportContrib = b.supportContrib.add(contrib);
                    b.supportPayrollCost = b.supportPayrollCost.add(payrollCost);
                    b.supportPaidHc++;
                }
            }
        }
        return b;
    }

    private DashboardSummaryResponse.SalaryMetrics buildSalaryMetrics(ClassificationBuckets b) {
        return new DashboardSummaryResponse.SalaryMetrics(
                metrics(b.totalGross(), b.totalContrib(), b.totalPayrollCost(), b.totalPaidHc()),
                metrics(b.billableGross, b.billableContrib, b.billablePayrollCost, b.billablePaidHc),
                metrics(b.benchGross, b.benchContrib, b.benchPayrollCost, b.benchPaidHc),
                metrics(b.supportGross, b.supportContrib, b.supportPayrollCost, b.supportPaidHc),
                metrics(b.leadershipGross, b.leadershipContrib, b.leadershipPayrollCost, b.leadershipPaidHc),
                metrics(b.managementGross, b.managementContrib, b.managementPayrollCost, b.managementPaidHc));
    }

    private static DashboardSummaryResponse.ClassificationSalaryMetrics metrics(
            BigDecimal gross, BigDecimal contrib, BigDecimal payrollCost, int paidHc) {
        return new DashboardSummaryResponse.ClassificationSalaryMetrics(
                gross, contrib, payrollCost, avg(payrollCost, paidHc));
    }

    private List<DashboardSummaryResponse.PuBreakdown> buildPuBreakdown(List<MasterRecord> records) {
        Map<String, PuAgg> byPu = new LinkedHashMap<>();
        for (MasterRecord r : records) {
            String pu = r.getPracticeUnit() != null && !r.getPracticeUnit().isBlank()
                    ? r.getPracticeUnit() : "(Unassigned)";
            PuAgg agg = byPu.computeIfAbsent(pu, k -> new PuAgg());
            agg.totalHc++;
            BigDecimal pay = r.getGrossPay();
            if (pay != null) {
                agg.totalPay = agg.totalPay.add(pay);
            }
            if (r.isBillable()) {
                agg.billableHc++;
                if (pay != null) {
                    agg.billablePay = agg.billablePay.add(pay);
                }
            }
            if (r.isBench()) {
                agg.benchHc++;
                if (pay != null) {
                    agg.benchPay = agg.benchPay.add(pay);
                }
            }
        }
        return byPu.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    PuAgg a = e.getValue();
                    return new DashboardSummaryResponse.PuBreakdown(
                            e.getKey(),
                            a.totalHc,
                            a.billableHc,
                            a.benchHc,
                            pct(a.billableHc, a.totalHc),
                            pct(a.benchHc, a.totalHc),
                            a.totalPay,
                            a.billablePay,
                            a.benchPay);
                })
                .toList();
    }

    private BuBreakdowns buildBuBreakdowns(List<MasterRecord> records, BigDecimal companyTotalPayrollCost) {
        Map<String, BuAgg> byBu = new LinkedHashMap<>();
        for (MasterRecord r : records) {
            String bu = r.getBusinessUnit() != null && !r.getBusinessUnit().isBlank()
                    ? r.getBusinessUnit() : "(Unassigned)";
            BuAgg agg = byBu.computeIfAbsent(bu, k -> new BuAgg());
            agg.totalHc++;
            if (r.isBillable()) {
                agg.billableHc++;
            }
            BigDecimal pay = r.getGrossPay();
            if (pay != null) {
                agg.totalPay = agg.totalPay.add(pay);
            }
        }

        List<DashboardSummaryResponse.ClientBreakdown> clients = new ArrayList<>();
        List<DashboardSummaryResponse.InternalBuBreakdown> internalBus = new ArrayList<>();

        for (Map.Entry<String, BuAgg> e : byBu.entrySet()) {
            String buName = e.getKey();
            BuAgg a = e.getValue();
            var ref = customerService.resolveBuCustomer(buName);
            boolean internal = ref.map(CustomerService.BuCustomerRef::internal).orElse(false);
            String customerCode = ref.map(CustomerService.BuCustomerRef::customerCode).orElse(null);
            int nonBillable = a.totalHc - a.billableHc;

            if (internal) {
                internalBus.add(new DashboardSummaryResponse.InternalBuBreakdown(
                        buName,
                        customerCode,
                        a.totalHc,
                        a.billableHc,
                        nonBillable,
                        a.totalPay,
                        pctAmount(a.totalPay, companyTotalPayrollCost)));
            } else {
                clients.add(new DashboardSummaryResponse.ClientBreakdown(
                        buName,
                        customerCode,
                        false,
                        a.totalHc,
                        a.billableHc,
                        nonBillable,
                        pct(a.billableHc, a.totalHc),
                        a.totalPay));
            }
        }

        clients.sort(Comparator.comparingInt(DashboardSummaryResponse.ClientBreakdown::totalHc).reversed());
        internalBus.sort(Comparator.comparingInt(DashboardSummaryResponse.InternalBuBreakdown::totalHc).reversed());
        return new BuBreakdowns(clients, internalBus);
    }

    private DashboardSummaryResponse.ReconciliationSummary buildReconciliationSummary(
            List<MasterRecord> records) {
        long payrollPending = 0;
        long autoMatchedExited = 0;
        long unmatched = 0;
        long manuallyMapped = 0;
        for (MasterRecord r : records) {
            switch (r.getReconciliationStatus()) {
                case PAYROLL_PENDING -> payrollPending++;
                case AUTO_MATCHED_EXITED -> autoMatchedExited++;
                case UNMATCHED -> unmatched++;
                case MANUALLY_MAPPED -> manuallyMapped++;
                default -> { }
            }
        }
        return new DashboardSummaryResponse.ReconciliationSummary(
                payrollPending, autoMatchedExited, unmatched, manuallyMapped);
    }

    private DashboardSummaryResponse.DataQualitySummary buildDataQualitySummary(
            List<MasterRecord> records) {
        int missingProjectCode = 0;
        int projectCodeNotFound = 0;
        int billingClientUnresolved = 0;
        int totalWarnings = 0;
        for (MasterRecord r : records) {
            String flags = r.getDataQualityFlags();
            if (flags == null || flags.isBlank()) {
                continue;
            }
            totalWarnings++;
            for (String flag : flags.split(",")) {
                switch (flag.trim()) {
                    case "MISSING_PROJECT_CODE" -> missingProjectCode++;
                    case "PROJECT_CODE_NOT_FOUND" -> projectCodeNotFound++;
                    case "BILLING_CLIENT_UNRESOLVED" -> billingClientUnresolved++;
                    default -> { }
                }
            }
        }
        return new DashboardSummaryResponse.DataQualitySummary(
                totalWarnings, missingProjectCode, projectCodeNotFound, billingClientUnresolved);
    }

    private static BigDecimal pct(int part, int whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pctAmount(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal avg(BigDecimal total, int paidHeadcount) {
        if (paidHeadcount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(paidHeadcount), 2, RoundingMode.HALF_UP);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static final class ClassificationBuckets {
        int billableHc;
        int benchHc;
        int supportHc;
        int leadershipHc;
        int managementHc;
        int billablePaidHc;
        int benchPaidHc;
        int supportPaidHc;
        int leadershipPaidHc;
        int managementPaidHc;
        BigDecimal billableGross = BigDecimal.ZERO;
        BigDecimal benchGross = BigDecimal.ZERO;
        BigDecimal supportGross = BigDecimal.ZERO;
        BigDecimal leadershipGross = BigDecimal.ZERO;
        BigDecimal managementGross = BigDecimal.ZERO;
        BigDecimal billableContrib = BigDecimal.ZERO;
        BigDecimal benchContrib = BigDecimal.ZERO;
        BigDecimal supportContrib = BigDecimal.ZERO;
        BigDecimal leadershipContrib = BigDecimal.ZERO;
        BigDecimal managementContrib = BigDecimal.ZERO;
        BigDecimal billablePayrollCost = BigDecimal.ZERO;
        BigDecimal benchPayrollCost = BigDecimal.ZERO;
        BigDecimal supportPayrollCost = BigDecimal.ZERO;
        BigDecimal leadershipPayrollCost = BigDecimal.ZERO;
        BigDecimal managementPayrollCost = BigDecimal.ZERO;

        int totalHc() {
            return billableHc + benchHc + supportHc + leadershipHc + managementHc;
        }

        int totalPaidHc() {
            return billablePaidHc + benchPaidHc + supportPaidHc + leadershipPaidHc + managementPaidHc;
        }

        BigDecimal totalGross() {
            return billableGross.add(benchGross).add(supportGross).add(leadershipGross).add(managementGross);
        }

        BigDecimal totalContrib() {
            return billableContrib.add(benchContrib).add(supportContrib)
                    .add(leadershipContrib).add(managementContrib);
        }

        BigDecimal totalPayrollCost() {
            return billablePayrollCost.add(benchPayrollCost).add(supportPayrollCost)
                    .add(leadershipPayrollCost).add(managementPayrollCost);
        }
    }

    private static final class PuAgg {
        int totalHc;
        int billableHc;
        int benchHc;
        BigDecimal totalPay = BigDecimal.ZERO;
        BigDecimal billablePay = BigDecimal.ZERO;
        BigDecimal benchPay = BigDecimal.ZERO;
    }

    private static final class BuAgg {
        int totalHc;
        int billableHc;
        BigDecimal totalPay = BigDecimal.ZERO;
    }

    private record BuBreakdowns(
            List<DashboardSummaryResponse.ClientBreakdown> clients,
            List<DashboardSummaryResponse.InternalBuBreakdown> internalBus
    ) {}
}
