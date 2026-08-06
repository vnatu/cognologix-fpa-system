import { Navigate, Route, Routes } from 'react-router-dom';
import BudgetingLayout from './BudgetingLayout';
import BudgetingDashboardPage from './BudgetingDashboardPage';
import BuAnalysisPage from './BuAnalysisPage';
import PlanSetupPage from './PlanSetupPage';
import PlanSummaryPage from './PlanSummaryPage';

export default function BudgetingRoutes() {
  return (
    <Routes>
      <Route element={<BudgetingLayout />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<BudgetingDashboardPage />} />
        <Route path="bu-analysis" element={<BuAnalysisPage />} />
        <Route path="plan-setup" element={<PlanSetupPage />} />
        <Route path="plan-summary" element={<PlanSummaryPage />} />
      </Route>
    </Routes>
  );
}
