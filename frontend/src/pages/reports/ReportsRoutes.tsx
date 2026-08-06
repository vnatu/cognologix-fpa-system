import { Navigate, Route, Routes } from 'react-router-dom';
import ReportsLayout from './ReportsLayout';
import StandardReportsPage from './StandardReportsPage';

export default function ReportsRoutes() {
  return (
    <Routes>
      <Route element={<ReportsLayout />}>
        <Route index element={<Navigate to="standard" replace />} />
        <Route path="standard" element={<StandardReportsPage />} />
      </Route>
    </Routes>
  );
}
