import { MONTH_NAMES, PERIOD_STATUS_LABELS } from './constants';
import type {
  MasterRecord,
  MasterSummary,
  PeriodResponse,
  PeriodStatus,
  PeriodVersionOption,
  PeriodVersionSummary,
} from './types';

export function formatPeriodLabel(month: number, year: number): string {
  return `${MONTH_NAMES[month - 1]} ${year}`;
}

export function formatVersionLabel(
  month: number,
  year: number,
  versionNumber: number,
): string {
  return `${formatPeriodLabel(month, year)} — v${versionNumber}`;
}

export function formatCurrencyInr(value: number | null | undefined): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(value);
}

export function getClassification(record: MasterRecord): string {
  if (record.isBillable) return 'Billable';
  if (record.isBench) return 'Bench';
  if (record.isSupport) return 'Support';
  if (record.isLeadership) return 'Leadership';
  if (record.isManagement) return 'Management';
  return '—';
}

export function totalHeadcount(summary: MasterSummary): number {
  return (
    summary.billable.headcount +
    summary.bench.headcount +
    summary.support.headcount +
    summary.leadership.headcount +
    summary.management.headcount
  );
}

export function totalGrossPay(summary: MasterSummary): number {
  return (
    Number(summary.billable.grossPay) +
    Number(summary.bench.grossPay) +
    Number(summary.support.grossPay) +
    Number(summary.leadership.grossPay) +
    Number(summary.management.grossPay)
  );
}

/** Session key: period id to auto-expand on Period Management after a version bump. */
export const EXPAND_PERIOD_AFTER_UPLOAD_KEY = 'people.expandPeriodId';

export function buildOpenVersionOptions(periods: PeriodResponse[]): PeriodVersionOption[] {
  return buildVersionOptionsForStatuses(periods, ['OPEN']);
}

/** Period versions that accept snapshot uploads (until the period is finalised). */
export function buildImportableVersionOptions(
  periods: PeriodResponse[],
): PeriodVersionOption[] {
  const uploadable = new Set<PeriodStatus>([
    'OPEN',
    'SNAPSHOTS_UPLOADED',
    'MASTER_BUILT',
  ]);
  const options: PeriodVersionOption[] = [];
  for (const period of periods) {
    const active = activeVersionForPeriod(period);
    if (active && uploadable.has(active.status)) {
      options.push({
        periodId: period.id,
        periodVersionId: active.id,
        label: `${formatVersionLabel(
          period.periodMonth,
          period.periodYear,
          active.versionNumber,
        )} (${PERIOD_STATUS_LABELS[active.status] ?? active.status})`,
        periodMonth: period.periodMonth,
        periodYear: period.periodYear,
        versionNumber: active.versionNumber,
        status: active.status,
        isLatestFinalised: active.isLatestFinalised,
      });
    }
  }
  return options.sort((a, b) => {
    if (a.periodYear !== b.periodYear) return b.periodYear - a.periodYear;
    if (a.periodMonth !== b.periodMonth) return b.periodMonth - a.periodMonth;
    return b.versionNumber - a.versionNumber;
  });
}

function buildVersionOptionsForStatuses(
  periods: PeriodResponse[],
  statuses: PeriodStatus[],
): PeriodVersionOption[] {
  const allowed = new Set(statuses);
  const options: PeriodVersionOption[] = [];
  for (const period of periods) {
    for (const version of period.versions) {
      if (allowed.has(version.status)) {
        options.push({
          periodId: period.id,
          periodVersionId: version.id,
          label: `${formatVersionLabel(
            period.periodMonth,
            period.periodYear,
            version.versionNumber,
          )} (${PERIOD_STATUS_LABELS[version.status] ?? version.status})`,
          periodMonth: period.periodMonth,
          periodYear: period.periodYear,
          versionNumber: version.versionNumber,
          status: version.status,
          isLatestFinalised: version.isLatestFinalised,
        });
      }
    }
  }
  return options.sort((a, b) => {
    if (a.periodYear !== b.periodYear) return b.periodYear - a.periodYear;
    if (a.periodMonth !== b.periodMonth) return b.periodMonth - a.periodMonth;
    return b.versionNumber - a.versionNumber;
  });
}

export function buildAllVersionOptions(periods: PeriodResponse[]): PeriodVersionOption[] {
  const options: PeriodVersionOption[] = [];
  for (const period of periods) {
    for (const version of period.versions) {
      options.push({
        periodId: period.id,
        periodVersionId: version.id,
        label: formatVersionLabel(
          period.periodMonth,
          period.periodYear,
          version.versionNumber,
        ),
        periodMonth: period.periodMonth,
        periodYear: period.periodYear,
        versionNumber: version.versionNumber,
        status: version.status,
        isLatestFinalised: version.isLatestFinalised,
      });
    }
  }
  return options.sort((a, b) => {
    if (a.periodYear !== b.periodYear) return b.periodYear - a.periodYear;
    if (a.periodMonth !== b.periodMonth) return b.periodMonth - a.periodMonth;
    return b.versionNumber - a.versionNumber;
  });
}

/**
 * Master Data period selector.
 * Default: latest MASTER_BUILT and/or latest FINALISED per period (both if present).
 * With includeSuperseded: all versions (any status).
 * Option labels are short ("v5 — Master Built"); period shown via Select optgroup.
 */
export function buildMasterVersionOptions(
  periods: PeriodResponse[],
  includeSuperseded = false,
): PeriodVersionOption[] {
  const all = buildAllVersionOptions(periods).map((o) => ({
    ...o,
    label: `v${o.versionNumber} — ${PERIOD_STATUS_LABELS[o.status] ?? o.status}`,
  }));

  if (includeSuperseded) {
    return all;
  }

  const byPeriod = new Map<string, PeriodVersionOption[]>();
  for (const o of all) {
    const key = `${o.periodYear}-${o.periodMonth}`;
    const list = byPeriod.get(key) ?? [];
    list.push(o);
    byPeriod.set(key, list);
  }

  const selected: PeriodVersionOption[] = [];
  for (const list of byPeriod.values()) {
    const masterBuilt = list
      .filter((o) => o.status === 'MASTER_BUILT')
      .sort((a, b) => b.versionNumber - a.versionNumber)[0];
    if (masterBuilt) {
      selected.push(masterBuilt);
    }
    const latestFinalised = list.find(
      (o) => o.status === 'FINALISED' && o.isLatestFinalised,
    );
    if (latestFinalised) {
      selected.push(latestFinalised);
    }
  }

  return selected.sort((a, b) => {
    if (a.periodYear !== b.periodYear) return b.periodYear - a.periodYear;
    if (a.periodMonth !== b.periodMonth) return b.periodMonth - a.periodMonth;
    return b.versionNumber - a.versionNumber;
  });
}

export type MasterVersionSelectGroup = {
  label: string;
  periodMonth: number;
  periodYear: number;
  options: PeriodVersionOption[];
};

/** Group master version options by period for Ant Design Select optgroups. */
export function buildMasterVersionSelectGroups(
  periods: PeriodResponse[],
  includeSuperseded = false,
): MasterVersionSelectGroup[] {
  const options = buildMasterVersionOptions(periods, includeSuperseded);
  const groups = new Map<string, MasterVersionSelectGroup>();

  for (const o of options) {
    const key = `${o.periodYear}-${o.periodMonth}`;
    let group = groups.get(key);
    if (!group) {
      group = {
        label: formatPeriodLabel(o.periodMonth, o.periodYear),
        periodMonth: o.periodMonth,
        periodYear: o.periodYear,
        options: [],
      };
      groups.set(key, group);
    }
    group.options.push(o);
  }

  return [...groups.values()].sort((a, b) => {
    if (a.periodYear !== b.periodYear) return b.periodYear - a.periodYear;
    return b.periodMonth - a.periodMonth;
  });
}

/**
 * Default Master Data selection: MASTER_BUILT for the most recent period if present,
 * otherwise that period's latest FINALISED.
 */
export function pickDefaultMasterVersion(
  periods: PeriodResponse[],
): PeriodVersionOption | null {
  const options = buildMasterVersionOptions(periods, false);
  if (options.length === 0) return null;

  const mostRecentKey = `${options[0].periodYear}-${options[0].periodMonth}`;
  const forMostRecent = options.filter(
    (o) => `${o.periodYear}-${o.periodMonth}` === mostRecentKey,
  );
  const masterBuilt = forMostRecent.find((o) => o.status === 'MASTER_BUILT');
  if (masterBuilt) return masterBuilt;

  const finalised = forMostRecent.find((o) => o.status === 'FINALISED');
  if (finalised) return finalised;

  return options[0];
}

export function isMasterDefaultVisible(option: PeriodVersionOption): boolean {
  return (
    option.status === 'MASTER_BUILT' ||
    (option.status === 'FINALISED' && option.isLatestFinalised)
  );
}

export function formatMasterVersionSelectionLabel(
  option: PeriodVersionOption,
): string {
  return `${formatPeriodLabel(option.periodMonth, option.periodYear)} — ${option.label}`;
}

export function activeVersionForPeriod(
  period: PeriodResponse,
): PeriodVersionSummary | null {
  const nonSuperseded = period.versions.filter((v) => v.status !== 'SUPERSEDED');
  if (nonSuperseded.length === 0) return null;
  return [...nonSuperseded].sort(
    (a, b) => b.versionNumber - a.versionNumber,
  )[0];
}

export function latestVersionForPeriod(
  period: PeriodResponse,
): PeriodVersionSummary | null {
  return activeVersionForPeriod(period);
}

export function isActiveVersion(
  period: PeriodResponse,
  version: PeriodVersionSummary,
): boolean {
  const active = activeVersionForPeriod(period);
  return active?.id === version.id;
}

export function periodStatusBadgeColor(status: PeriodStatus): string {
  switch (status) {
    case 'OPEN':
      return 'default';
    case 'SNAPSHOTS_UPLOADED':
      return 'blue';
    case 'MASTER_BUILT':
      return 'orange';
    case 'FINALISED':
      return 'green';
    case 'SUPERSEDED':
      return 'default';
    default:
      return 'default';
  }
}

export function reconciliationTagColor(status: string): string {
  switch (status) {
    case 'MATCHED':
      return 'green';
    case 'PAYROLL_PENDING':
      return 'gold';
    case 'AUTO_MATCHED_EXITED':
      return 'default';
    case 'UNMATCHED':
      return 'red';
    case 'MANUALLY_MAPPED':
      return 'blue';
    default:
      return 'default';
  }
}
