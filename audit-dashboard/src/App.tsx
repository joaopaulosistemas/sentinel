import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/Layout/AppShell';
import { AuditRunPage } from './pages/AuditRun/AuditRunPage';
import { FindingsPage } from './pages/Findings/FindingsPage';
import { GuidePage } from './pages/Guide/GuidePage';
import { OverviewPage } from './pages/Overview/OverviewPage';
import { RankingPage } from './pages/Ranking/RankingPage';
import { ReportsPage } from './pages/Reports/ReportsPage';
import { SettingsPage } from './pages/Settings/SettingsPage';
import { SystemsPage } from './pages/Systems/SystemsPage';
import { SystemDetailPage } from './pages/SystemDetail/SystemDetailPage';

export function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/systems" element={<SystemsPage />} />
        <Route path="/systems/:systemId" element={<SystemDetailPage />} />
        <Route path="/audits/:auditId" element={<AuditRunPage />} />
        <Route path="/ranking" element={<RankingPage />} />
        <Route path="/guide" element={<GuidePage />} />
        <Route path="/findings" element={<FindingsPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>
    </AppShell>
  );
}
