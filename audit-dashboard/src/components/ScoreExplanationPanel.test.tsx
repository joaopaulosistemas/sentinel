import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuditRunResponse, ScoreExplanation } from '../types';
import { ScoreExplanationPanel } from './ScoreExplanationPanel';

const dims = (groups: ScoreExplanation['dimensions'][number]['groups']): ScoreExplanation['dimensions'] => [
  { category: 'QUALITY', weight: 0.2, score: 60, penalty: 40, hidden: 2, groups },
  { category: 'SECURITY', weight: 0.3, score: 100, penalty: 0, hidden: 0, groups: [] },
];

const real: ScoreExplanation = {
  curve: 'suave', sizeFactor: 0.5, linesOfCode: 80000, overallBefore: 40, overall: 40, removedRules: [],
  dimensions: dims([{ ruleId: 'pmd-SystemPrintln', severity: 'MEDIUM', count: 39, raw: 195, counted: 20, capped: true }]),
};
const simulated: ScoreExplanation = { ...real, overall: 43.5, removedRules: ['pmd-SystemPrintln'], dimensions: dims([]) };

function wrap(run: Partial<AuditRunResponse> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const audit = { id: 7, status: 'COMPLETED', ...run } as AuditRunResponse;
  return render(<QueryClientProvider client={client}><ScoreExplanationPanel run={audit} /></QueryClientProvider>);
}

afterEach(() => vi.unstubAllGlobals());

describe('ScoreExplanationPanel', () => {
  it('só consulta a API depois de aberto e mostra o teto por regra', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(real), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    wrap();
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /Por que esta nota/ }));

    expect(await screen.findByText('pmd-SystemPrintln')).toBeInTheDocument();
    expect(screen.getByText(/só 20 entram/)).toBeInTheDocument();
    expect(screen.getByText(/projeto grande: achados abaixo de crítico contam 50%/)).toBeInTheDocument();
    expect(screen.getByText('+ 2 regras menores')).toBeInTheDocument();
  });

  it('simula a nota sem uma regra e permite desfazer', async () => {
    const urls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      urls.push(String(url));
      return new Response(JSON.stringify(String(url).includes('without=') ? simulated : real), { status: 200 });
    }));
    wrap();
    fireEvent.click(screen.getByRole('button', { name: /Por que esta nota/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Simular sem a regra pmd-SystemPrintln' }));

    expect(await screen.findByText(/sem essas regras/)).toBeInTheDocument();
    expect(screen.getByText('(+3,5)')).toBeInTheDocument();
    expect(urls.some((u) => u.endsWith('/score-explanation?without=pmd-SystemPrintln'))).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Voltar a contar a regra pmd-SystemPrintln' }));
    await waitFor(() => expect(screen.queryByText(/sem essas regras/)).toBeNull());
  });

  it('mostra o erro quando a API falha', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"message":"quebrou"}', { status: 500 })));
    wrap();
    fireEvent.click(screen.getByRole('button', { name: /Por que esta nota/ }));
    expect(await screen.findByText('Não foi possível explicar a nota')).toBeInTheDocument();
  });
});
