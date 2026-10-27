import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SystemSummaryResponse } from '../../types';
import { OverviewPage } from './OverviewPage';

const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString();
const system = (over: Partial<SystemSummaryResponse>): SystemSummaryResponse => ({
  id: 1, name: 'x', stack: 'JAVA', team: 'T', lastAuditScore: 90, lastAuditDate: daysAgo(1), openCritical: 0, openHigh: 0, ...over,
});

let pendingFor: (u: string) => unknown[] = () => [];

function renderPage(systems: SystemSummaryResponse[], onCall?: (url: string, init?: RequestInit) => void) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      onCall?.(u, init);
      if (init?.method === 'POST' && u === '/api/v1/audits') {
        return new Response(JSON.stringify({ id: 77, systemId: 5, systemName: 'conta', branch: 'develop', status: 'PENDING', riskScore: null, findingsSummary: {}, startedAt: daysAgo(0), completedAt: null }), { status: 202 });
      }
      if (u.startsWith('/api/v1/audits')) {
        return new Response(
          JSON.stringify([
            { id: 9, systemId: 2, systemName: 'portal', branch: 'main', status: 'PENDING', riskScore: null, findingsSummary: {}, startedAt: daysAgo(0), completedAt: null },
            { id: 8, systemId: 1, systemName: 'conta', branch: 'main', status: 'COMPLETED', riskScore: { overall: 61 }, findingsSummary: {}, startedAt: daysAgo(1), completedAt: daysAgo(1) },
          ]),
          { status: 200 },
        );
      }
      if (u.includes('/pending-actions/')) {
        return new Response(JSON.stringify(pendingFor(u)), { status: 200 });
      }
      return new Response(JSON.stringify(systems), { status: 200 });
    }),
  );
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <OverviewPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  pendingFor = () => [];
});

describe('OverviewPage', () => {
  it('mostra os quatro indicadores e todos os sistemas, do menor para o maior nota', async () => {
    renderPage([
      system({ id: 1, name: 'conta', openCritical: 3, lastAuditScore: 61 }),
      system({ id: 2, name: 'portal', lastAuditScore: 54, lastAuditDate: daysAgo(9) }),
      system({ id: 3, name: 'saudavel', lastAuditScore: 90 }),
    ]);
    expect(await screen.findByText('Precisam de atenção')).toBeInTheDocument();
    expect(screen.getByText('Nota média')).toBeInTheDocument();
    expect(screen.getByText('Achados críticos abertos')).toBeInTheDocument();
    expect(screen.getByText('Em execução agora')).toBeInTheDocument();
    expect(screen.getByText(/Sem auditar há mais de 5 dias/)).toBeInTheDocument();

    const panel = screen.getByLabelText('Precisam de atenção');
    const items = within(panel).getAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent('portal');
    expect(items[0]).toHaveTextContent('não é auditado há 9 dias');
    expect(items[1]).toHaveTextContent('conta');
    expect(items[1]).toHaveTextContent('3 críticos');
    expect(items[2]).toHaveTextContent('Sem críticos · dentro da meta');
  });

  it('conta as auditorias em execução agora e mostra o passo na tabela', async () => {
    renderPage([system({ id: 2, name: 'portal' })]);
    const kpi = (await screen.findByText('Em execução agora')).parentElement as HTMLElement;
    expect(await within(kpi).findByText('1')).toBeInTheDocument();
    expect(within(kpi).getByText('portal')).toBeInTheDocument();
    expect(await screen.findByText('Na fila')).toBeInTheDocument();
    expect(screen.getByText('Atualizando sozinha a cada 3 s')).toBeInTheDocument();
  });

  it('Iniciar auditoria dispara a auditoria do sistema escolhido e avisa com link para acompanhar', async () => {
    const calls: Array<{ url: string; method: string; body?: string }> = [];
    renderPage([system({ id: 5, name: 'conta', scheduledBranch: 'develop' })], (u, init) => {
      calls.push({ url: u, method: init?.method ?? 'GET', body: init?.body as string | undefined });
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Nova auditoria' }));
    expect(await screen.findByLabelText('Branch')).toHaveValue('develop');
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar auditoria' }));
    expect(await screen.findByText('Auditoria #77 iniciada')).toBeInTheDocument();
    const post = calls.find((c) => c.method === 'POST');
    expect(post?.url).toBe('/api/v1/audits');
    expect(JSON.parse(post?.body ?? '{}')).toMatchObject({ systemId: 5, branch: 'develop' });
    expect(screen.getByRole('link', { name: 'Acompanhar execução' })).toHaveAttribute('href', '/audits/77');
  });

  it('avisa na visão geral quantos pedidos do Claude esperam decisão, com link por sistema', async () => {
    pendingFor = () => [{ id: 1, kind: 'START_AUDIT', summary: 's', status: 'PENDING', createdAt: '', expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null }];
    renderPage([system({ id: 5, name: 'conta' })]);
    expect(await screen.findByText('1 pedido do Claude espera sua decisão')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'conta (1)' })).toHaveAttribute('href', '/findings?aba=reauditoria&systemId=5');
  });

  it('com a nota subindo mostra a variação no resumo', async () => {
    renderPage([system({ id: 1, name: 'conta', lastAuditScore: 63, scoreDelta: 2 })]);
    expect(await screen.findByText('+2 desde a auditoria anterior')).toBeInTheDocument();
    expect(screen.getByText('nota subiu 2 pontos', { exact: false })).toBeInTheDocument();
  });
});
