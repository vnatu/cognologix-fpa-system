import { Navigate, Route, Routes } from 'react-router-dom';
import RevenueLayout from './RevenueLayout';
import {
  ZohoBooksCreditNotesImportPage,
  ZohoBooksInvoicesImportPage,
} from './imports';
import InvoicesPage from './InvoicesPage';
import RevenueDashboardPage from './RevenueDashboardPage';

export default function RevenueRoutes() {
  return (
    <Routes>
      <Route element={<RevenueLayout />}>
        <Route
          index
          element={<Navigate to="imports/zoho-books-invoices" replace />}
        />
        <Route
          path="imports/zoho-books-invoices"
          element={<ZohoBooksInvoicesImportPage />}
        />
        <Route
          path="imports/zoho-books-credit-notes"
          element={<ZohoBooksCreditNotesImportPage />}
        />
        <Route path="invoices" element={<InvoicesPage />} />
        <Route path="dashboard" element={<RevenueDashboardPage />} />
      </Route>
    </Routes>
  );
}
