import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PendingAction, SystemSummaryResponse } from '../types';
import { ReauditPanel, scoreChange } from './ReauditPanel';

const systems = [{ id: 7, name: 'conta-corrente-api', scheduledBranch: 'main' }] as SystemSummaryResponse[];

function action(over: Partial<PendingAction> = {}): PendingAction {
  return {
    id: 1, kind: 'START_AUDIT', summary: 'Iniciar uma auditoria de conta-corrente-api na branch main', status: 'PENDING',
    createdAt: '2026-10-24T10:00:00', expiresAt: '2026-10-24T11:00:00', decidedAt: null, resultNote: null, auditRunId: null, ...over,
  };
}

function setup(actions: PendingAction[]) {
  const calls: Array<{ url: string; method: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      calls.push({ url: u, method: init?.method ?? 'GET' });
      if (u.includes('/pending-actions/') && init?.method === 'POST') return new Response(JSON.stringify([action({ status: 'APPROVED', resultNote: 'Auditoria #9 iniciada.', auditRunId: 9 })]), { status: 200 });
      if (u.includes('/pending-actions/')) return new Response(JSON.stringify(actions), { status: 200 });
      if (u.includes('/audits')) return new Response('[]', { status: 200 });
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
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe('ReauditPanel', () => {
  it('mostra o pedido esperando e deixa claro que nada acontece sem aprovação', async () => {
    setup([action()]);
    expect(await screen.findByText('Iniciar uma auditoria de conta-corrente-api na branch main')).toBeInTheDocument();
    expect(screen.getByText(/nada acontece até você aprovar/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprovar' })).toBeInTheDocument();
  });

  it('aprovar chama a API e mostra o resultado com o link da auditoria', async () => {
    const calls = setup([action()]);
    fireEvent.click(await screen.findByRole('button', { name: 'Aprovar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/pending-actions/7/1/approve'))).toBe(true));
    expect(await screen.findByText(/Auditoria #9 iniciada/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();
  });

  it('recusar chama a API de recusa', async () => {
    const calls = setup([action()]);
    fireEvent.click(await screen.findByRole('button', { name: 'Recusar' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/pending-actions/7/1/reject'))).toBe(true));
  });

  it('pedido já decidido não tem botões', async () => {
    setup([action({ status: 'EXPIRED', resultNote: 'Perdeu a validade sem decisão.' })]);
    expect(await screen.findByText(/Venceu sem decisão/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();
  });

  it('sem pedidos explica como liberar as ferramentas', async () => {
    setup([]);
    expect(await screen.findByText(/Libere iniciar_auditoria ou suprimir_achado/)).toBeInTheDocument();
  });

  it('formata a variação de nota', () => {
    expect(scoreChange(62, 71)).toBe('62 → 71 (+9)');
    expect(scoreChange(80, 74)).toBe('80 → 74 (-6)');
    expect(scoreChange(null, 70)).toBe('70');
    expect(scoreChange(60, null)).toBe('sem nota');
  });
});
