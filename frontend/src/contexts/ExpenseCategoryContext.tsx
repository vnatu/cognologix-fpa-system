import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { fetchAllCategories } from '@/pages/expenses/api';
import type { ExpenseCategory } from '@/pages/expenses/types';

interface ExpenseCategoryContextType {
  categories: ExpenseCategory[];
  reloadCategories: () => void;
}

const ExpenseCategoryContext = createContext<ExpenseCategoryContextType | null>(
  null,
);

export function ExpenseCategoryProvider({ children }: { children: ReactNode }) {
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);

  const reloadCategories = useCallback(() => {
    void fetchAllCategories()
      .then(setCategories)
      .catch(() => {
        /* keep last known list on failure */
      });
  }, []);

  useEffect(() => {
    reloadCategories();
  }, [reloadCategories]);

  const value = useMemo(
    () => ({ categories, reloadCategories }),
    [categories, reloadCategories],
  );

  return (
    <ExpenseCategoryContext.Provider value={value}>
      {children}
    </ExpenseCategoryContext.Provider>
  );
}

export function useExpenseCategories(): ExpenseCategoryContextType {
  const ctx = useContext(ExpenseCategoryContext);
  if (!ctx) {
    throw new Error(
      'useExpenseCategories must be used within ExpenseCategoryProvider',
    );
  }
  return ctx;
}
