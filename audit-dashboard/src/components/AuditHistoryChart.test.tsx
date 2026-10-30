import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuditRunResponse } from '../types';
import { AuditHistoryChart } from './AuditHistoryChart';

const SCORE = { security: 0, quality: 0, architecture: 0, dependencies: 0, observability: 0 };

function run(id: number, day: number, overall: number | null, extra: Partial<AuditRunResponse> = {}): AuditRunResponse {
  const started = `2026-10-${String(day).padStart(2, '0')}T10:00:00`;
  return {
    id,
    systemId: 7,
    systemName: 'frequency',
    branch: 'main',
    status: overall === null ? 'FAILED' : 'COMPLETED',
    riskScore: overall === null ? null : { overall, ...SCORE },
    findingsSummary: { CRITICAL: 1, HIGH: 4, MEDIUM: 0, LOW: 0, INFO: 0 },
    startedAt: started,
    completedAt: started,
    ...extra,
  };
}

function Where() {
  return <span data-testid="onde">{useLocation().pathname}</span>;
}

function renderChart(history: AuditRunResponse[], currentId = 44) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(history), { status: 200 })));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/audits/${currentId}`]}>
        <Routes>
          <Route path="/audits/:auditId" element={<><AuditHistoryChart systemId={7} systemName="frequency" currentId={currentId} /><Where /></>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('AuditHistoryChart', () => {
  const history = [run(31, 28, 54), run(36, 29, 61), run(40, 29, null), run(44, 30, 77)];

  it('mostra uma coluna por auditoria, com a nota, e resume quantas estão na meta', async () => {
    renderChart(history);
    expect(await screen.findByRole('link', { name: /Abrir a auditoria 31/ })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /Abrir a auditoria/ })).toHaveLength(4);
    expect(screen.getByText('frequency · 1 de 3 na meta')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /auditoria 44.*Nota 77, Boa, 2 pontos acima da meta/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /auditoria 31.*Nota 54, Atenção, 21 pontos abaixo da meta/ })).toBeInTheDocument();
  });

  it('a auditoria aberta é marcada, e a que falhou aparece sem nota', async () => {
    renderChart(history);
    const current = await screen.findByRole('link', { name: /Abrir a auditoria 44/ });
    expect(current).toHaveAttribute('aria-current', 'page');
    expect(current).toHaveAccessibleName(/Esta é a auditoria aberta/);
    expect(screen.getByRole('link', { name: /auditoria 40.*Falhou/ })).not.toHaveAttribute('aria-current');
    expect(screen.getByText('Sem nota')).toBeInTheDocument();
  });

  it('clicar na coluna (ou no valor dela) abre o resultado daquela auditoria', async () => {
    window.scrollTo = vi.fn();
    renderChart(history);
    const column = await screen.findByRole('link', { name: /Abrir a auditoria 36/ });
    expect(column).toHaveAttribute('href', '/audits/36');
    fireEvent.click(within(column).getByText('61'));
    await waitFor(() => expect(screen.getByTestId('onde')).toHaveTextContent('/audits/36'));
    expect(window.scrollTo).toHaveBeenCalled();
  });

  it('a dica mostra nota, distância da meta, variação e críticos ao focar a coluna', async () => {
    renderChart(history);
    const column = await screen.findByRole('link', { name: /Abrir a auditoria 36/ });
    fireEvent.focus(column);
    const tip = await screen.findByRole('status');
    expect(tip).toHaveTextContent('Auditoria #36');
    expect(tip).toHaveTextContent('Nota61 · Atenção');
    expect(tip).toHaveTextContent('Meta (75)-14');
    expect(tip).toHaveTextContent('Anterior+7');
    expect(tip).toHaveTextContent('Críticos / altos1 / 4');
    expect(tip).toHaveTextContent('Clique para abrir o resultado');
    fireEvent.blur(column);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('na auditoria aberta a dica avisa que a pessoa já está nela', async () => {
    renderChart(history);
    fireEvent.focus(await screen.findByRole('link', { name: /Abrir a auditoria 44/ }));
    expect(await screen.findByRole('status')).toHaveTextContent('Você está nesta auditoria');
  });

  it('com uma auditoria só, explica que ainda não há evolução para mostrar', async () => {
    renderChart([run(44, 30, 77)]);
    expect(await screen.findByText(/primeira auditoria do sistema/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Abrir a auditoria/ })).not.toBeInTheDocument();
  });

  it('marca a meta e o limite de atenção no gráfico', async () => {
    renderChart(history);
    await screen.findByRole('group', { name: /Gráfico de colunas.*frequency/ });
    expect(screen.getByText('Meta 75')).toBeInTheDocument();
    expect(screen.getByText('Atenção 50')).toBeInTheDocument();
  });
});
