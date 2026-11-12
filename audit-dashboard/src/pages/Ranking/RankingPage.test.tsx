import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ISystemSummaryResponse } from '../../types';
import { RankingPage } from './RankingPage';

const system = (over: Partial<ISystemSummaryResponse>): ISystemSummaryResponse => ({
  id: 1, name: 'x', stack: 'JAVA', team: 'T', lastAuditScore: 70, lastAuditDate: null, openCritical: 0, openHigh: 0, ...over,
});

function renderPage(systems: ISystemSummaryResponse[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      new Response(JSON.stringify(String(url).includes('/rules/quality') ? [] : systems), { status: 200 })),
  );
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <RankingPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('ranking de sistemas: selo de escopo ao lado da nota', () => {
  it('mostra o selo quando a última auditoria rodou com um lado desligado', async () => {
    renderPage([system({ id: 1, name: 'api-java', disabledSides: ['front'] }), system({ id: 2, name: 'portal', disabledSides: ['back'] })]);

    expect(await screen.findByText('front-end desligado')).toBeInTheDocument();
    expect(screen.getByText('back-end desligado')).toBeInTheDocument();
  });

  it('não mostra selo quando os dois lados estavam ligados, quando o escopo é desconhecido ou o campo não veio', async () => {
    renderPage([
      system({ id: 1, name: 'ligados', disabledSides: [] }),
      system({ id: 2, name: 'antiga', disabledSides: null }),
      system({ id: 3, name: 'sem-campo' }),
    ]);

    expect(await screen.findByText('ligados')).toBeInTheDocument();
    expect(screen.queryByText(/desligado/)).not.toBeInTheDocument();
  });
});
