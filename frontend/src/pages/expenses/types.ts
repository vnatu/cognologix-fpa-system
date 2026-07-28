export interface ExpenseCategory {
  id: string;
  lineCode: string;
  categoryGroup: string;
  displayName: string;
  description: string | null;
  active: boolean;
  sortOrder: number;
}

export interface CategoryGroup {
  categoryGroup: string;
  categories: ExpenseCategory[];
}

export interface ExpenseEntry {
  categoryId: string;
  lineCode: string;
  categoryGroup: string;
  displayName: string;
  sortOrder: number;
  amount: number;
  notes: string | null;
  actualId: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface MonthlyExpenses {
  month: number;
  year: number;
  locked: boolean;
  lockedAt: string | null;
  lockedBy: string | null;
  entries: ExpenseEntry[];
}

export interface MonthHistory {
  month: number;
  year: number;
  totalAmount: number;
  locked: boolean;
}

export interface ExpenseImportResult {
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errors: Array<{ rowNumber: number; reason: string }>;
}
