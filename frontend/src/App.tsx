import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { DateFormatProvider } from '@/context/DateFormatContext';
import AppLayout from '@/layouts/AppLayout';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/DashboardPage';
import PeoplePayrollRoutes from '@/pages/people/PeoplePayrollRoutes';
import CustomerManagementRoutes from '@/pages/customers/CustomerManagementRoutes';
import BudgetingRoutes from '@/pages/budgeting/BudgetingRoutes';
import RevenueRoutes from '@/pages/revenue/RevenueRoutes';
import ExpensesRoutes from '@/pages/expenses/ExpensesRoutes';
import SettingsPage from '@/pages/settings/SettingsPage';
import AccountPage from '@/pages/AccountPage';

function ProtectedRoute({ element }: { element: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{element}</> : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <DateFormatProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/"
              element={<ProtectedRoute element={<AppLayout />} />}
            >
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route
                path="people-payroll/*"
                element={<PeoplePayrollRoutes />}
              />
              <Route
                path="customer-management/*"
                element={<CustomerManagementRoutes />}
              />
              <Route path="budgeting/*" element={<BudgetingRoutes />} />
              <Route path="revenue/*" element={<RevenueRoutes />} />
              <Route path="expenses/*" element={<ExpensesRoutes />} />
              <Route path="settings" element={<SettingsPage />} />
              <Route path="account" element={<AccountPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </DateFormatProvider>
    </AuthProvider>
  );
}
