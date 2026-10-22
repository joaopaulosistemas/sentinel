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

function renderPage(systems: SystemSummaryResponse[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const u = String(url);
      if (u.startsWith('/api/v1/audits')) {
        return new Response(
          JSON.stringify([
            { id: 9, systemId: 2, systemName: 'portal', branch: 'main', status: 'RUNNING', riskScore: null, findingsSummary: {}, startedAt: daysAgo(0), completedAt: null },
            { id: 8, systemId: 1, systemName: 'conta', branch: 'main', status: 'COMPLETED', riskScore: { overall: 61 }, findingsSummary: {}, startedAt: daysAgo(1), completedAt: daysAgo(1) },
          ]),
          { status: 200 },
        );
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

afterEach(() => vi.unstubAllGlobals());

describe('OverviewPage', () => {
  it('mostra os quatro indicadores e quem precisa de atenção, do mais urgente ao menos', async () => {
    renderPage([
      system({ id: 1, name: 'conta', openCritical: 3, lastAuditScore: 61 }),
      system({ id: 2, name: 'portal', lastAuditDate: daysAgo(9) }),
      system({ id: 3, name: 'saudavel' }),
    ]);
    expect(await screen.findByText('Precisam de atenção')).toBeInTheDocument();
    expect(screen.getByText('Nota média')).toBeInTheDocument();
    expect(screen.getByText('Críticos abertos')).toBeInTheDocument();
    expect(screen.getByText('Em execução agora')).toBeInTheDocument();
    expect(screen.getByText(/Sem auditar há mais de 5 dias/)).toBeInTheDocument();

    const panel = screen.getByText('Precisam de atenção').closest('.cds--tile') as HTMLElement;
    const items = within(panel).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('conta');
    expect(items[0]).toHaveTextContent('3 críticos abertos');
    expect(items[1]).toHaveTextContent('sem auditar há 9 dias');
    expect(within(panel).queryByText('saudavel')).not.toBeInTheDocument();
  });

  it('conta as auditorias em execução agora', async () => {
    renderPage([system({})]);
    const tile = (await screen.findByText('Em execução agora')).closest('.cds--tile') as HTMLElement;
    expect(await within(tile).findByText('1')).toBeInTheDocument();
  });

  it('só habilita Nova auditoria depois de escolher o sistema', async () => {
    renderPage([system({ id: 5, name: 'conta' })]);
    const button = await screen.findByRole('button', { name: 'Nova auditoria' });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Auditar um sistema'), { target: { value: '5' } });
    expect(button).toBeEnabled();
  });

  it('sem nenhum sistema com problema diz que está tudo em ordem', async () => {
    renderPage([system({})]);
    expect(await screen.findByText(/nenhum sistema pede ação agora/)).toBeInTheDocument();
  });
});
