import { Navigate, Route, Routes } from 'react-router-dom';
import BudgetingLayout from './BudgetingLayout';
import BudgetingDashboardPage from './BudgetingDashboardPage';
import PlanSetupPage from './PlanSetupPage';

export default function BudgetingRoutes() {
  return (
    <Routes>
      <Route element={<BudgetingLayout />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<BudgetingDashboardPage />} />
        <Route path="plan-setup" element={<PlanSetupPage />} />
      </Route>
    </Routes>
  );
}
