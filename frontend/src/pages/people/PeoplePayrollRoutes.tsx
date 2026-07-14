import { Navigate, Route, Routes } from 'react-router-dom';
import PeoplePayrollLayout from '@/pages/people/PeoplePayrollLayout';
import {
  ZohoPeopleImportPage,
  ZohoPayrollImportPage,
  ZohoPayrollFnfImportPage,
  ZohoPeopleExitedImportPage,
} from '@/pages/people/imports';
import PeriodManagementPage from '@/pages/people/periods/PeriodManagementPage';
import SnapshotDetailPage from '@/pages/people/SnapshotDetailPage';
import MasterDataPage from '@/pages/people/master/MasterDataPage';
import PeopleDashboardPage from '@/pages/people/dashboard/PeopleDashboardPage';

export default function PeoplePayrollRoutes() {
  return (
    <Routes>
      <Route element={<PeoplePayrollLayout />}>
        <Route index element={<Navigate to="imports/zoho-people" replace />} />
        <Route path="imports/zoho-people" element={<ZohoPeopleImportPage />} />
        <Route path="imports/zoho-payroll" element={<ZohoPayrollImportPage />} />
        <Route
          path="imports/zoho-payroll-fnf"
          element={<ZohoPayrollFnfImportPage />}
        />
        <Route
          path="imports/zoho-people-exited"
          element={<ZohoPeopleExitedImportPage />}
        />
        <Route path="periods" element={<PeriodManagementPage />} />
        <Route
          path="imports/snapshots/:periodVersionId/:importType"
          element={<SnapshotDetailPage />}
        />
        <Route path="master" element={<MasterDataPage />} />
        <Route path="dashboard" element={<PeopleDashboardPage />} />
      </Route>
    </Routes>
  );
}
