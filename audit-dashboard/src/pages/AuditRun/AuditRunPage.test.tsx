import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuditRunResponse, FixPlan } from '../../types';
import { AuditRunPage } from './AuditRunPage';

function renderPage(run: Partial<AuditRunResponse>, plan?: FixPlan) {
  const full: AuditRunResponse = {
    id: 215,
    systemName: 'conta-corrente-api',
    branch: 'main',
    status: 'RUNNING',
    riskScore: null,
    findingsSummary: { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 },
    startedAt: '2026-10-22T10:00:00',
    completedAt: null,
    ...run,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      new Response(JSON.stringify(String(url).endsWith('/fix-plan') ? plan : full), { status: 200 }),
    ),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/audits/215']}>
        <Routes>
          <Route path="/audits/:auditId" element={<AuditRunPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('AuditRunPage', () => {
  it('em execução mostra analisadores, eventos e o contador', async () => {
    renderPage({
      progress: {
        phase: 'ANALISE',
        analyzers: [
          { name: 'Semgrep', status: 'OK', findings: 12, durationMs: 900, note: null },
          { name: 'Trivy', status: 'EXECUTANDO', findings: 0, durationMs: 0, note: null },
          { name: 'PMD', status: 'INDISPONIVEL', findings: 0, durationMs: 0, note: 'ferramenta ausente neste ambiente' },
        ],
        events: [{ at: '2026-10-22T10:00:05', message: 'Semgrep concluído: 12 achados' }],
      },
    });
    expect(await screen.findByText('Semgrep')).toBeInTheDocument();
    expect(screen.getByText('Executando')).toBeInTheDocument();
    expect(screen.getByText('Ferramenta ausente')).toBeInTheDocument();
    expect(screen.getByText('Semgrep concluído: 12 achados')).toBeInTheDocument();
    expect(screen.getByText(/1 de 2 analisadores concluídos/)).toBeInTheDocument();
  });

  it('concluída mostra veredito, categorias e o que corrigir primeiro', async () => {
    renderPage(
      {
        status: 'COMPLETED',
        completedAt: '2026-10-22T10:04:12',
        riskScore: { overall: 63, security: 50, quality: 70, architecture: 66, dependencies: 52, observability: 80 },
      },
      {
        scoreNow: 63,
        scoreIfCriticalFixed: 74,
        criticalCount: 3,
        items: [
          { ruleId: 'sql-injection', title: 'SQL montado por concatenação', severity: 'CRITICAL', category: 'SECURITY', occurrences: 2, scoreGain: 4.5, exampleFile: 'ContaRepository.java', exampleLine: 212, howToFix: 'usar parâmetros nomeados' },
          { ruleId: 'cors', title: 'CORS aberto', severity: 'HIGH', category: 'DEPENDENCY', occurrences: 1, scoreGain: 1, exampleFile: 'main.ts', exampleLine: 12, howToFix: null },
        ],
      },
    );
    expect(await screen.findByText('3 problemas críticos para resolver primeiro')).toBeInTheDocument();
    expect(screen.getByText(/vai de 63 para 74/)).toBeInTheDocument();
    expect(screen.getByText('SQL montado por concatenação')).toBeInTheDocument();
    expect(screen.getByText('nota +4.5')).toBeInTheDocument();
    expect(screen.getByText(/\+1 ocorrências/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver todos os achados' })).toBeInTheDocument();
    expect(screen.getByText(/4 min 12 s/)).toBeInTheDocument();

    // filtrar por categoria mostra só os problemas dela
    fireEvent.click(screen.getByRole('button', { name: /Dependências/ }));
    expect(screen.queryByText('SQL montado por concatenação')).not.toBeInTheDocument();
    expect(screen.getByText('CORS aberto')).toBeInTheDocument();
  });

  it('falha mostra o motivo', async () => {
    renderPage({ status: 'FAILED', errorMessage: 'IllegalStateException: repositório inacessível' });
    expect(await screen.findByText(/repositório inacessível/)).toBeInTheDocument();
  });
});
