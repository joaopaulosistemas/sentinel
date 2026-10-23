import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ClaudeReview, RuleProposalView, SystemSummaryResponse } from '../types';
import { ClaudeReviewPanel, pct } from './ClaudeReviewPanel';

const systems = [
  { id: 7, name: 'conta-corrente-api' },
  { id: 8, name: 'portal-cooperado' },
] as SystemSummaryResponse[];

function proposal(over: Partial<RuleProposalView> = {}): RuleProposalView {
  return {
    id: 1, slug: 'sem-system-out', title: 'Sem System.out', rationale: 'Aparece em todo serviço', semgrepYaml: 'rules: []',
    stage: 'PROPOSED', stageLabel: 'Proposta', copiedFromId: null, sampleNote: null, observationStartedAt: null, decisionNote: null,
    evidence: { hits: 0, falsePositives: 0, auditsSinceStart: 0, precision: 0 }, minPrecision: 0.9, minHits: 5,
    sharedEligible: false, runsInAudits: false, ...over,
  };
}

function setup(initial: ClaudeReview) {
  const calls: Array<{ url: string; method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined });
      return new Response(JSON.stringify(initial), { status: 200 });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ClaudeReviewPanel systems={systems} initialSystemId={7} />
    </QueryClientProvider>,
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe('ClaudeReviewPanel', () => {
  it('deixa claro que nada entra na nota e explica como liberar as ferramentas quando está vazio', async () => {
    setup({ openSuggestions: 0, suggestions: [], proposals: [] });
    expect(await screen.findByText(/Nenhuma sugestão/)).toBeInTheDocument();
    expect(screen.getByText(/Nada daqui entra na nota/)).toBeInTheDocument();
    expect(screen.getByText(/Nenhuma regra proposta/)).toBeInTheDocument();
  });

  it('lista sugestões e permite dispensar', async () => {
    const calls = setup({
      openSuggestions: 1,
      suggestions: [{ id: 5, title: 'Trocar System.out por logger', detail: 'Em 12 classes', ruleRef: 'local-x', file: 'A.java', line: 10, status: 'OPEN', createdAt: '2026-10-23T10:00:00' }],
      proposals: [],
    });
    expect(await screen.findByText('Trocar System.out por logger')).toBeInTheDocument();
    expect(screen.getByText('Sugestões (1 aberta)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Dispensar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/suggestions/5/status'))).toBe(true));
    expect(JSON.parse(calls.find((c) => c.method === 'POST')?.body ?? '{}')).toEqual({ status: 'DISMISSED' });
  });

  it('cada etapa do funil mostra só o passo seguinte', async () => {
    setup({ openSuggestions: 0, suggestions: [], proposals: [proposal()] });
    expect(await screen.findByRole('button', { name: 'Revisei a regra' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Promover' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Iniciar observação' })).not.toBeInTheDocument();
  });

  it('teste de amostra manda o resultado e a anotação', async () => {
    const calls = setup({ openSuggestions: 0, suggestions: [], proposals: [proposal({ stage: 'REVIEWED', stageLabel: 'Revisada' })] });
    fireEvent.change(await screen.findByLabelText('Anotação sobre sem-system-out'), { target: { value: 'semgrep --test ok' } });
    fireEvent.click(screen.getByRole('button', { name: 'Teste de amostra passou' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/rules/1/sample'))).toBe(true));
    expect(JSON.parse(calls.find((c) => c.url.endsWith('/sample'))?.body ?? '{}')).toEqual({ passed: true, note: 'semgrep --test ok' });
  });

  it('em observação mostra a evidência e o mínimo para promover', async () => {
    setup({
      openSuggestions: 0, suggestions: [],
      proposals: [proposal({ stage: 'OBSERVING', stageLabel: 'Em observação', observationStartedAt: '2026-10-23T10:00:00', evidence: { hits: 20, falsePositives: 2, auditsSinceStart: 1, precision: 0.9 }, runsInAudits: true })],
    });
    expect(await screen.findByText(/20 achados, 2 falso\(s\) positivo\(s\), precisão 90%/)).toBeInTheDocument();
    expect(screen.getByText(/precisão de 90% ou mais e 5\+ achados/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Promover' })).toBeInTheDocument();
  });

  it('regra promovida pode ser copiada para outro sistema, nunca para o mesmo', async () => {
    const calls = setup({ openSuggestions: 0, suggestions: [], proposals: [proposal({ stage: 'PROMOTED', stageLabel: 'Promovida', sharedEligible: true })] });
    const select = await screen.findByLabelText('Copiar sem-system-out para');
    expect(Array.from(select.querySelectorAll('option')).map((o) => o.textContent)).toEqual(['Copiar para outro sistema…', 'portal-cooperado']);
    expect(screen.getByText(/pronta para ir ao pacote compartilhado/)).toBeInTheDocument();
    fireEvent.change(select, { target: { value: '8' } });
    fireEvent.click(screen.getByRole('button', { name: 'Copiar em observação' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/rules/1/copy'))).toBe(true));
    expect(JSON.parse(calls.find((c) => c.url.endsWith('/copy'))?.body ?? '{}')).toEqual({ targetSystemId: 8 });
  });

  it('formata porcentagem', () => {
    expect(pct(0.9)).toBe('90%');
    expect(pct(0.857)).toBe('86%');
  });
});
