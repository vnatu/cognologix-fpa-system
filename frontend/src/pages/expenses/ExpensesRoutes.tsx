import { Navigate, Route, Routes } from 'react-router-dom';
import ExpensesLayout from './ExpensesLayout';
import ExpenseEntryPage from './ExpenseEntryPage';
import ExpenseHistoryPage from './ExpenseHistoryPage';

export default function ExpensesRoutes() {
  return (
    <Routes>
      <Route element={<ExpensesLayout />}>
        <Route index element={<Navigate to="entry" replace />} />
        <Route path="entry" element={<ExpenseEntryPage />} />
        <Route path="history" element={<ExpenseHistoryPage />} />
      </Route>
    </Routes>
  );
}
