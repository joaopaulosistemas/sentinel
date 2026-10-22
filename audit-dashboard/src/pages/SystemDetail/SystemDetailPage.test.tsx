import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SystemDetailPage } from './SystemDetailPage';

const system = {
  id: 3, name: 'conta-corrente-api', stack: 'JAVA', team: 'Squad Contas', profile: 'corporativo-java-ee-angular',
  lastAuditScore: 61, lastAuditDate: null, openCritical: 0, openHigh: 0,
};
const sum = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 };
const runs = [
  { id: 215, systemId: 3, systemName: 'conta-corrente-api', branch: 'main', status: 'RUNNING', riskScore: null, findingsSummary: sum, startedAt: new Date().toISOString(), completedAt: null, progress: { phase: 'ANALISE', analyzers: [{ name: 'A', status: 'OK', findings: 1, durationMs: 1, note: null }, { name: 'B', status: 'EXECUTANDO', findings: 0, durationMs: 0, note: null }], events: [] } },
  { id: 214, systemId: 3, systemName: 'conta-corrente-api', branch: 'main', status: 'COMPLETED', riskScore: { overall: 61 }, findingsSummary: sum, totalFindings: 252, newFindings: 4, resolvedFindings: 9, startedAt: '2026-10-21T10:00:00', completedAt: '2026-10-21T10:04:00' },
  { id: 207, systemId: 3, systemName: 'conta-corrente-api', branch: 'main', status: 'FAILED', riskScore: null, findingsSummary: sum, errorMessage: 'repositório inacessível', startedAt: '2026-10-19T14:00:00', completedAt: null },
];

function Where() {
  return <span data-testid="where">{useLocation().pathname + useLocation().search}</span>;
}

function renderPage() {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    const u = String(url);
    if (u.endsWith('/systems/3/audits')) return new Response(JSON.stringify(runs), { status: 200 });
    if (u.endsWith('/systems/3')) return new Response(JSON.stringify(system), { status: 200 });
    return new Response(JSON.stringify({}), { status: 200 });
  }));
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/systems/3']}>
        <Routes>
          <Route path="/systems/:systemId" element={<SystemDetailPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('SystemDetailPage', () => {
  it('mostra o cabeçalho e a tabela de auditorias com situação, achados e início', async () => {
    renderPage();
    expect(await screen.findByText('Java · Squad Contas · perfil corporativo-java-ee-angular')).toBeInTheDocument();
    expect(screen.getByText(/Em execução · Análise 1\/2/)).toBeInTheDocument();
    expect(screen.getByText('252 (4 novos, 9 resolvidos)')).toBeInTheDocument();
    expect(screen.getByText('Concluída')).toBeInTheDocument();
    expect(screen.getByText(/Falhou · repositório inacessível/)).toBeInTheDocument();
    expect(screen.getByText('Atualizando a cada 3 s enquanto houver auditoria em execução')).toBeInTheDocument();
    expect(screen.getByText('19/10 14:00')).toBeInTheDocument();
  });

  it('Editar sistema leva ao painel de edição desse sistema', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Editar sistema' }));
    expect(await screen.findByTestId('where')).toHaveTextContent('/systems?edit=3');
  });
});
