import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { IClaudeReview, IRuleProposalView, ISystemSummaryResponse } from '../types';
import { ClaudeReviewSummary, funnelCounts, nextStep } from './ClaudeReviewSummary';

const system = { id: 7, name: 'conta-corrente-api' } as ISystemSummaryResponse;

function proposal(over: Partial<IRuleProposalView> = {}): IRuleProposalView {
  return {
    id: 1, slug: 'sem-system-out', title: 'Sem System.out', rationale: null, semgrepYaml: '', stage: 'PROPOSED', stageLabel: 'Proposta', copiedFromId: null,
    sampleNote: null, observationStartedAt: null, decisionNote: null, evidence: { hits: 0, falsePositives: 0, auditsSinceStart: 0, precision: 0 },
    minPrecision: 0.9, minHits: 5, sharedEligible: false, runsInAudits: false, ...over,
  };
}

const review = (over: Partial<IClaudeReview> = {}): IClaudeReview => ({ openSuggestions: 0, suggestions: [], proposals: [], ...over });

describe('funnelCounts e nextStep', () => {
  it('conta as regras por etapa e deixa as descartadas de fora', () => {
    const counts = funnelCounts([proposal(), proposal({ id: 2, stage: 'OBSERVING' }), proposal({ id: 3, stage: 'OBSERVING' }), proposal({ id: 4, stage: 'DISCARDED' })]);
    expect(counts.map((c) => `${c.label} ${c.count}`)).toEqual(['Proposta 1', 'Revisada 0', 'Amostra ok 0', 'Observando 2', 'Promovida 0']);
  });

  it('o próximo passo segue a ordem do funil e depois olha as sugestões', () => {
    expect(nextStep(review({ proposals: [proposal({ stage: 'SAMPLE_PASSED' }), proposal({ id: 2, title: 'Outra', stage: 'PROPOSED' })] }))).toBe('Regra Outra: revisar a regra antes de testar.');
    expect(nextStep(review({ proposals: [proposal({ stage: 'REVIEWED' })] }))).toContain('teste de amostra');
    expect(nextStep(review({ proposals: [proposal({ stage: 'SAMPLE_PASSED' })] }))).toContain('iniciar a observação');
    const pronta = proposal({ stage: 'OBSERVING', evidence: { hits: 9, falsePositives: 0, auditsSinceStart: 2, precision: 1 } });
    expect(nextStep(review({ proposals: [pronta] }))).toBe('Regra Sem System.out: pronta para promover.');
    expect(nextStep(review({ proposals: [proposal({ stage: 'OBSERVING' })], openSuggestions: 2 }))).toBe('2 sugestão(ões) aberta(s) do Claude.');
    expect(nextStep(review())).toBeNull();
  });
});

describe('ClaudeReviewSummary', () => {
  afterEach(() => vi.unstubAllGlobals());

  function renderSummary(data: IClaudeReview, pending: unknown[] = []) {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(String(url).includes('/pending-actions/') ? pending : data), { status: 200 })));
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <ClaudeReviewSummary system={system} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
  }

  it('mostra só o resumo, sem botões de decidir, e leva à revisão completa do sistema', async () => {
    renderSummary(review({ openSuggestions: 4, proposals: [proposal(), proposal({ id: 2, stage: 'PROMOTED' })] }), [
      { id: 1, kind: 'START_AUDIT', summary: 's', status: 'PENDING', createdAt: '', expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null },
    ]);
    const resumo = await screen.findByRole('region', { name: 'Resumo da revisão do Claude' });
    expect(await within(resumo).findByText('Regra Sem System.out: revisar a regra antes de testar.', { exact: false })).toBeInTheDocument();
    expect(within(resumo).getByText('Pedidos esperando').nextSibling).toHaveTextContent('1');
    expect(within(resumo).getByText('Regras no funil').nextSibling).toHaveTextContent('1');
    expect(within(resumo).getByText('Sugestões abertas').nextSibling).toHaveTextContent('4');
    expect(within(resumo).queryByRole('button')).not.toBeInTheDocument();
    expect(within(resumo).getByRole('link', { name: /Abrir revisão completa/ })).toHaveAttribute('href', '/claude?aba=decisoes&systemId=7');
  });

  it('sem nada esperando, diz isso', async () => {
    renderSummary(review());
    expect(await screen.findByText('Nada esperando você neste sistema.')).toBeInTheDocument();
  });
});
