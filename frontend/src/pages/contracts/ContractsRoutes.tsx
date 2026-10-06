import { Navigate, Route, Routes } from 'react-router-dom';
import ContractsLayout from './ContractsLayout';
import ContractDashboardPage from './ContractDashboardPage';
import ContractListPage from './ContractListPage';
import ContractDetailPage from './ContractDetailPage';
import TemplatesPage from './TemplatesPage';

export default function ContractsRoutes() {
  return (
    <Routes>
      <Route element={<ContractsLayout />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<ContractDashboardPage />} />
        <Route path="list" element={<ContractListPage />} />
        <Route path="templates" element={<TemplatesPage />} />
        <Route path=":id" element={<ContractDetailPage />} />
      </Route>
    </Routes>
  );
}
