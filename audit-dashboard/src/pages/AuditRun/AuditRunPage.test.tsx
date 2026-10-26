import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuditRunResponse, FixPlan, McpCall, PendingAction } from '../../types';
import { AuditRunPage } from './AuditRunPage';

interface McpMock {
  enabled: boolean;
  calls?: McpCall[];
  pending?: Array<Partial<PendingAction>>;
}

function renderPage(run: Partial<AuditRunResponse>, plan?: FixPlan, history: Array<Partial<AuditRunResponse>> = [], mcp?: McpMock) {
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
  const calls: Array<{ url: string; method: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      calls.push({ url: u, method: init?.method ?? 'GET' });
      if (u.endsWith('/fix-plan')) return new Response(JSON.stringify(plan), { status: 200 });
      if (u.includes('/systems/') && u.endsWith('/audits')) return new Response(JSON.stringify(history), { status: 200 });
      if (u.endsWith('/cancel')) return new Response(JSON.stringify({ ...full, status: 'CANCELLED' }), { status: 202 });
      if (mcp && u.includes('/mcp-access/') && u.includes('/calls')) return new Response(JSON.stringify(mcp.calls ?? []), { status: 200 });
      if (mcp && u.includes('/mcp-access/')) {
        return new Response(JSON.stringify({ enabled: mcp.enabled, hasKey: true, keyPrefix: 'snt_', keyCreatedAt: null, dataLevel: 'TITLES', tools: [] }), { status: 200 });
      }
      if (mcp && u.includes('/pending-actions/')) return new Response(JSON.stringify(mcp.pending ?? []), { status: 200 });
      return new Response(JSON.stringify(full), { status: 200 });
    }),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/audits/215']}>
        <Routes>
          <Route path="/audits/:auditId" element={<AuditRunPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...view, calls };
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
    expect(screen.getByRole('img', { name: 'Concluído' })).toBeInTheDocument();
    expect(screen.getByText('Resultado parcial')).toBeInTheDocument();
    expect(screen.getAllByText('executando').length).toBeGreaterThan(0);
    expect(screen.getByText('ferramenta ausente')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Executando' })).toBeInTheDocument();
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
    expect(await screen.findByText('3 problemas críticos')).toBeInTheDocument();
    expect(screen.getByText('74')).toBeInTheDocument();
    expect(screen.getByText('Etapa 1 · Veredito')).toBeInTheDocument();
    expect(screen.getByText(/resultado da auditoria #215/)).toBeInTheDocument();
    expect(screen.getByText('SQL montado por concatenação')).toBeInTheDocument();
    expect(screen.getByText('nota +4.5')).toBeInTheDocument();
    expect(screen.getByText(/\+1 ocorrências/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver todos os achados' })).toBeInTheDocument();
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

  it('cancelar pede confirmação e chama a API', async () => {
    const { calls } = renderPage({ systemId: 3, progress: { phase: 'CLONE', analyzers: [], events: [] } });
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar auditoria' }));
    expect(await screen.findByText('Cancelar esta auditoria?')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST')).toBe(false); // ainda não cancelou

    fireEvent.click(screen.getByRole('button', { name: 'Sim, cancelar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/audits/215/cancel'))).toBe(true));
  });

  it('cancelada mostra o aviso e oferece auditar de novo', async () => {
    renderPage({ status: 'CANCELLED', systemId: 3, completedAt: '2026-10-22T10:00:30', errorMessage: 'Cancelada pelo usuário' });
    expect(await screen.findByText('Auditoria cancelada', { selector: '.cds--inline-notification__title' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Auditar de novo' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancelar auditoria' })).not.toBeInTheDocument();
  });

  it('em execução estima o que falta pela média das auditorias anteriores do sistema', async () => {
    const start = new Date(Date.now() - 60_000).toISOString().slice(0, 19);
    const fmt = (ms: number) => new Date(ms).toISOString().slice(0, 19);
    renderPage(
      { systemId: 3, startedAt: start, progress: { phase: 'ANALISE', analyzers: [], events: [{ at: fmt(Date.now()), message: 'x' }] } },
      undefined,
      [{ id: 200, status: 'COMPLETED', startedAt: '2026-10-21T10:00:00', completedAt: '2026-10-21T10:04:00' }],
    );
    expect(await screen.findByText(/faltam cerca de/)).toBeInTheDocument();
  });

  it('concluída oferece baixar o relatório e auditar de novo', async () => {
    renderPage(
      { status: 'COMPLETED', systemId: 3, completedAt: '2026-10-22T10:04:12', riskScore: { overall: 63, security: 50, quality: 70, architecture: 66, dependencies: 52, observability: 80 } },
      { scoreNow: 63, scoreIfCriticalFixed: 63, criticalCount: 0, items: [] },
    );
    const link = await screen.findByRole('link', { name: 'Baixar relatório' });
    expect(link).toHaveAttribute('href', '/api/v1/audits/215/report');
    expect(screen.getByRole('button', { name: 'Auditar de novo' })).toBeInTheDocument();
  });

  describe('Claude (MCP) durante a execução', () => {
    const running = { systemId: 3, progress: { phase: 'ANALISE' as const, analyzers: [], events: [{ at: '2026-10-22T10:00:05', message: 'Clone concluído' }] } };

    it('mostra a faixa e as chamadas do Claude no quadro de eventos', async () => {
      renderPage(
        running,
        undefined,
        [],
        {
          enabled: true,
          calls: [
            { tool: 'buscar_achados', ok: true, durationMs: 20, detail: null, at: '2026-10-22T10:00:30' },
            { tool: 'status_auditoria', ok: true, durationMs: 11, detail: null, at: '2026-10-22T09:00:00' }, // antes do início: não conta
          ],
        },
      );
      expect(await screen.findByText(/1 consulta nesta auditoria/)).toBeInTheDocument();
      expect(screen.getByText('Claude (MCP) chamou buscar_achados')).toBeInTheDocument();
      expect(screen.getByText('Clone concluído')).toBeInTheDocument();
      expect(screen.queryByText('Claude (MCP) chamou status_auditoria')).not.toBeInTheDocument();
    });

    it('com a consulta desligada avisa e oferece configurar', async () => {
      renderPage(running, undefined, [], { enabled: false });
      expect(await screen.findByText(/desligado para este sistema/)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Configurar' })).toHaveAttribute('href', '/systems?edit=3&tab=mcp');
    });

    it('pedido do Claude aguardando aprovação aparece na faixa', async () => {
      renderPage(running, undefined, [], { enabled: true, pending: [{ id: 1, status: 'PENDING' }] });
      expect(await screen.findByRole('link', { name: '1 pedido do Claude aguarda aprovação' })).toBeInTheDocument();
    });

    it('sem sistema conhecido não mostra a faixa nem consulta o MCP', async () => {
      const { calls } = renderPage({ progress: { phase: 'CLONE', analyzers: [], events: [] } });
      expect(await screen.findByText('Eventos')).toBeInTheDocument();
      expect(screen.queryByRole('region', { name: 'Claude (MCP)' })).not.toBeInTheDocument();
      expect(calls.some((c) => c.url.includes('/mcp-access/'))).toBe(false);
    });

    it('auditoria concluída não mostra a faixa', async () => {
      renderPage(
        { status: 'COMPLETED', systemId: 3, completedAt: '2026-10-22T10:04:12', riskScore: { overall: 63, security: 50, quality: 70, architecture: 66, dependencies: 52, observability: 80 } },
        { scoreNow: 63, scoreIfCriticalFixed: 63, criticalCount: 0, items: [] },
        [],
        { enabled: true },
      );
      expect(await screen.findByRole('link', { name: 'Baixar relatório' })).toBeInTheDocument();
      expect(screen.queryByRole('region', { name: 'Claude (MCP)' })).not.toBeInTheDocument();
    });
  });
});
