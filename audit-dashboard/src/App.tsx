import { Suspense, lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
import { SkeletonText } from '@carbon/react';
import { AppShell } from './components/Layout/AppShell';

// Cada tela vira um pacote próprio: quem abre só a visão geral não baixa gráficos, relatórios nem configurações.
const OverviewPage = lazy(() => import('./pages/Overview/OverviewPage').then((m) => ({ default: m.OverviewPage })));
const SystemsPage = lazy(() => import('./pages/Systems/SystemsPage').then((m) => ({ default: m.SystemsPage })));
const SystemDetailPage = lazy(() =>
  import('./pages/SystemDetail/SystemDetailPage').then((m) => ({ default: m.SystemDetailPage })),
);
const AuditRunPage = lazy(() => import('./pages/AuditRun/AuditRunPage').then((m) => ({ default: m.AuditRunPage })));
const RankingPage = lazy(() => import('./pages/Ranking/RankingPage').then((m) => ({ default: m.RankingPage })));
const GuidePage = lazy(() => import('./pages/Guide/GuidePage').then((m) => ({ default: m.GuidePage })));
const FindingsPage = lazy(() => import('./pages/Findings/FindingsPage').then((m) => ({ default: m.FindingsPage })));
const ReportsPage = lazy(() => import('./pages/Reports/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const SettingsPage = lazy(() => import('./pages/Settings/SettingsPage').then((m) => ({ default: m.SettingsPage })));

export function App() {
  return (
    <AppShell>
      <Suspense fallback={<SkeletonText paragraph lineCount={4} />}>
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
      </Suspense>
    </AppShell>
  );
}
