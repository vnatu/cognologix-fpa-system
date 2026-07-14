import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';

dayjs.extend(customParseFormat);

export const DATE_FORMAT_OPTIONS = [
  'DD MMM YYYY',
  'DD/MM/YYYY',
  'MM/DD/YYYY',
] as const;

export type DateFormatOption = (typeof DATE_FORMAT_OPTIONS)[number];

/** Maps UI date format strings to dayjs format tokens. */
const DAYJS_FORMAT: Record<DateFormatOption, string> = {
  'DD MMM YYYY': 'DD MMM YYYY',
  'DD/MM/YYYY': 'DD/MM/YYYY',
  'MM/DD/YYYY': 'MM/DD/YYYY',
};

export function formatDate(
  date: string | Date | null | undefined,
  format: string,
): string {
  if (date == null || date === '') return '—';
  const fmt = DAYJS_FORMAT[format as DateFormatOption] ?? format;
  const parsed = dayjs(date);
  if (!parsed.isValid()) return '—';
  return parsed.format(fmt);
}

export function formatDateTime(
  date: string | Date | null | undefined,
  format: string,
): string {
  if (date == null || date === '') return '—';
  const parsed = dayjs(date);
  if (!parsed.isValid()) return '—';
  return `${formatDate(date, format)} ${parsed.format('HH:mm')}`;
}
