import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { IPendingAction, ISystemSummaryResponse } from '../types';
import { ReauditPanel, scoreChange } from './ReauditPanel';

const systems = [{ id: 7, name: 'conta-corrente-api', scheduledBranch: 'main' }] as ISystemSummaryResponse[];

function action(over: Partial<IPendingAction> = {}): IPendingAction {
  return {
    id: 1, kind: 'START_AUDIT', summary: 'Iniciar uma auditoria de conta-corrente-api na branch main', status: 'PENDING',
    createdAt: '2026-10-24T10:00:00', expiresAt: '2026-10-24T11:00:00', decidedAt: null, resultNote: null, auditRunId: null, ...over,
  };
}

function setup(actions: IPendingAction[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const requestUrl = String(url);
      if (requestUrl.includes('/pending-actions/')) return new Response(JSON.stringify(actions), { status: 200 });
      if (requestUrl.includes('/audits')) return new Response('[]', { status: 200 });
      return new Response('{}', { status: 200 });
    }),
  );
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ReauditPanel systems={systems} initialSystemId={7} />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('ReauditPanel', () => {
  it('os pedidos não se decidem aqui: mostra quantos esperam e leva às Decisões do sistema', async () => {
    setup([action(), action({ id: 2, status: 'APPROVED' })]);
    const aviso = await screen.findByRole('region', { name: 'Pedidos do Claude' });
    expect(await within(aviso).findByText('1 pedido do Claude espera sua decisão.')).toBeInTheDocument();
    expect(within(aviso).getByRole('link', { name: /Abrir Decisões/ })).toHaveAttribute('href', '/claude?aba=decisoes&systemId=7');
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recusar' })).not.toBeInTheDocument();
  });

  it('com vários pedidos, fala no plural', async () => {
    setup([action(), action({ id: 2 })]);
    expect(await screen.findByText('2 pedidos do Claude esperam sua decisão.')).toBeInTheDocument();
  });

  it('sem pedidos esperando, diz isso (pedido já decidido não conta)', async () => {
    setup([action({ status: 'EXPIRED' })]);
    expect(await screen.findByText('Nenhum pedido do Claude esperando.')).toBeInTheDocument();
  });

  it('formata a variação de nota', () => {
    expect(scoreChange(62, 71)).toBe('62 → 71 (+9)');
    expect(scoreChange(80, 74)).toBe('80 → 74 (-6)');
    expect(scoreChange(null, 70)).toBe('70');
    expect(scoreChange(60, null)).toBe('sem nota');
  });
});
