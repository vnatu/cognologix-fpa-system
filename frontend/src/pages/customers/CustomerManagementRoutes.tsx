import { Navigate, Route, Routes } from 'react-router-dom';
import CustomerManagementLayout from './CustomerManagementLayout';
import CustomersPage from './CustomersPage';
import RateCardsPage from './RateCardsPage';
import ProjectCodesPage from './ProjectCodesPage';

export default function CustomerManagementRoutes() {
  return (
    <Routes>
      <Route element={<CustomerManagementLayout />}>
        <Route index element={<Navigate to="customers" replace />} />
        <Route path="customers" element={<CustomersPage />} />
        <Route path="rate-cards" element={<RateCardsPage />} />
        <Route path="project-codes" element={<ProjectCodesPage />} />
      </Route>
    </Routes>
  );
}
