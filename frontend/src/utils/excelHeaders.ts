/** Shared Excel header normalization — mirrors backend ExcelParserUtils (ADR-047). */
export function normalizeHeader(header: string | null | undefined): string {
  if (header == null) return '';
  return header
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, '_')
    .replace(/[^a-z0-9_]/g, '');
}
