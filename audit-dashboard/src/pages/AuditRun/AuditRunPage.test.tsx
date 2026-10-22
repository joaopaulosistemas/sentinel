import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuditRunResponse } from '../../types';
import { AuditRunPage } from './AuditRunPage';

function renderPage(run: Partial<AuditRunResponse>) {
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
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(full), { status: 200 })));
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

  it('concluída mostra a nota e o caminho para corrigir', async () => {
    renderPage({
      status: 'COMPLETED',
      completedAt: '2026-10-22T10:04:12',
      riskScore: { overall: 63, security: 50, quality: 70, architecture: 66, dependencies: 52, observability: 80 },
      findingsSummary: { CRITICAL: 3, HIGH: 38, MEDIUM: 5, LOW: 1, INFO: 0 },
    });
    await waitFor(() => expect(screen.getByText('63')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Ver o que corrigir' })).toBeInTheDocument();
    expect(screen.getByText(/4 min 12 s/)).toBeInTheDocument();
  });

  it('falha mostra o motivo', async () => {
    renderPage({ status: 'FAILED', errorMessage: 'IllegalStateException: repositório inacessível' });
    expect(await screen.findByText(/repositório inacessível/)).toBeInTheDocument();
  });
});
