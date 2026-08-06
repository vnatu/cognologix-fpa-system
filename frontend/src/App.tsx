import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { DateFormatProvider } from '@/context/DateFormatContext';
import { SecuritySettingsProvider } from '@/context/SecuritySettingsContext';
import { UnsavedChangesProvider } from '@/context/UnsavedChangesContext';
import { ExpenseCategoryProvider } from '@/contexts/ExpenseCategoryContext';
import { useInactivityTimer } from '@/hooks/useInactivityTimer';

const LoginPage = lazy(() => import('@/pages/LoginPage'));
const AppLayout = lazy(() => import('@/layouts/AppLayout'));
const DashboardPage = lazy(() => import('@/pages/DashboardPage'));
const PeoplePayrollRoutes = lazy(() => import('@/pages/people/PeoplePayrollRoutes'));
const CustomerManagementRoutes = lazy(
  () => import('@/pages/customers/CustomerManagementRoutes'),
);
const BudgetingRoutes = lazy(() => import('@/pages/budgeting/BudgetingRoutes'));
const ReportsRoutes = lazy(() => import('@/pages/reports/ReportsRoutes'));
const RevenueRoutes = lazy(() => import('@/pages/revenue/RevenueRoutes'));
const ExpensesRoutes = lazy(() => import('@/pages/expenses/ExpensesRoutes'));
const SettingsPage = lazy(() => import('@/pages/settings/SettingsPage'));
const AccountPage = lazy(() => import('@/pages/AccountPage'));

const routeFallback = (
  <div
    style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
    }}
  >
    <Spin size="large" tip="Loading..." />
  </div>
);

function InactivityGuard({ children }: { children: React.ReactNode }) {
  useInactivityTimer();
  return <>{children}</>;
}

function ProtectedRoute({ element }: { element: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? (
    <SecuritySettingsProvider>
      <UnsavedChangesProvider>
        <InactivityGuard>
          <ExpenseCategoryProvider>{element}</ExpenseCategoryProvider>
        </InactivityGuard>
      </UnsavedChangesProvider>
    </SecuritySettingsProvider>
  ) : (
    <Navigate to="/login" replace />
  );
}

export default function App() {
  return (
    <AuthProvider>
      <DateFormatProvider>
        <BrowserRouter>
          <Suspense fallback={routeFallback}>
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
                <Route path="reports/*" element={<ReportsRoutes />} />
                <Route path="revenue/*" element={<RevenueRoutes />} />
                <Route path="expenses/*" element={<ExpensesRoutes />} />
                <Route path="settings" element={<SettingsPage />} />
                <Route path="account" element={<AccountPage />} />
              </Route>
            </Routes>
          </Suspense>
        </BrowserRouter>
      </DateFormatProvider>
    </AuthProvider>
  );
}
