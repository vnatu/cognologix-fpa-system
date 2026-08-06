/** Shared Excel header normalization — mirrors backend ExcelParserUtils. */
export function normalizeHeader(header: string | null | undefined): string {
  if (header == null) return '';
  return header
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, '_')
    .replace(/[^a-z0-9_]/g, '');
}
