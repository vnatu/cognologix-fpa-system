import { Navigate, Route, Routes } from 'react-router-dom';
import BankReconLayout from './BankReconLayout';
import NewRunPage from './NewRunPage';
import RunReviewPage from './RunReviewPage';
import RunHistoryPage from './RunHistoryPage';
import LedgerMasterPage from './config/LedgerMasterPage';
import ColumnMappingPage from './config/ColumnMappingPage';
import LearnedMappingsPage from './config/LearnedMappingsPage';
import LlmHintsPage from './config/LlmHintsPage';
import ContraRulesPage from './config/ContraRulesPage';
import OllamaSettingsPage from './config/OllamaSettingsPage';

export default function BankReconRoutes() {
  return (
    <Routes>
      <Route element={<BankReconLayout />}>
        <Route index element={<Navigate to="new-run" replace />} />
        <Route path="new-run" element={<NewRunPage />} />
        <Route path="runs/:runId" element={<RunReviewPage />} />
        <Route path="run-history" element={<RunHistoryPage />} />
        <Route path="config/ledgers" element={<LedgerMasterPage />} />
        <Route path="config/column-mapping" element={<ColumnMappingPage />} />
        <Route path="config/mappings" element={<LearnedMappingsPage />} />
        <Route path="config/hints" element={<LlmHintsPage />} />
        <Route path="config/contra-rules" element={<ContraRulesPage />} />
        <Route path="config/ollama" element={<OllamaSettingsPage />} />
      </Route>
    </Routes>
  );
}
