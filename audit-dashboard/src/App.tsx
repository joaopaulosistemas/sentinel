import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/Layout/AppShell';
import { AuditRunPage } from './pages/AuditRun/AuditRunPage';
import { FindingsPage } from './pages/Findings/FindingsPage';
import { OverviewPage } from './pages/Overview/OverviewPage';
import { RankingPage } from './pages/Ranking/RankingPage';
import { ReportsPage } from './pages/Reports/ReportsPage';
import { SystemDetailPage } from './pages/SystemDetail/SystemDetailPage';

export function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/systems" element={<OverviewPage />} />
        <Route path="/systems/:systemId" element={<SystemDetailPage />} />
        <Route path="/audits/:auditId" element={<AuditRunPage />} />
        <Route path="/ranking" element={<RankingPage />} />
        <Route path="/findings" element={<FindingsPage />} />
        <Route path="/reports" element={<ReportsPage />} />
      </Routes>
    </AppShell>
  );
}
