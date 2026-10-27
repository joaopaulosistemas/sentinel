import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FindingsPage } from './FindingsPage';

const systems = [
  { id: 1, name: 'conta-corrente-api', stack: 'JAVA', team: 'A', lastAuditScore: 80, lastAuditDate: null, openCritical: 0, openHigh: 0 },
  { id: 2, name: 'portal-cooperado', stack: 'JAVASCRIPT', team: 'B', lastAuditScore: 70, lastAuditDate: null, openCritical: 0, openHigh: 0 },
];

function stub() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const u = String(url);
      if (u === '/api/v1/systems') return new Response(JSON.stringify(systems), { status: 200 });
      if (u === '/api/v1/audits/26') {
        return new Response(JSON.stringify({ id: 26, systemId: 2, systemName: 'portal-cooperado', branch: 'main', status: 'COMPLETED', riskScore: null, findingsSummary: {}, startedAt: '2026-10-20T10:00:00', completedAt: null }), { status: 200 });
      }
      return new Response('[]', { status: 200 });
    }),
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('FindingsPage: sistema vindo de "Ver todos os achados"', () => {
  it('o sistema da auditoria já vem escolhido na Revisão do Claude e na Reauditoria', async () => {
    stub();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/findings?auditId=26']}>
          <FindingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    // aba Achados e nota: o filtro de sistema mostra o sistema da auditoria
    const filtro = (await screen.findByLabelText('Sistema')) as HTMLSelectElement;
    await vi.waitFor(() => expect(filtro.value).toBe('2'));

    fireEvent.click(screen.getByRole('tab', { name: 'Revisão do Claude' }));
    const revisao = within(await screen.findByRole('region', { name: 'Revisão do Claude' })).getByRole('combobox') as HTMLSelectElement;
    expect(revisao.value).toBe('2');

    fireEvent.click(screen.getByRole('tab', { name: 'Reauditoria' }));
    const reauditoria = within(await screen.findByRole('region', { name: 'Reauditoria' })).getByRole('combobox') as HTMLSelectElement;
    expect(reauditoria.value).toBe('2');
  });
});
