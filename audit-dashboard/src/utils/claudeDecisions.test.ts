import { describe, expect, it } from 'vitest';
import type { IClaudeReview, IPendingAction, IReviewSuggestion, IRuleProposalView, ISystemSummaryResponse } from '../types';
import { buildDecisions } from './claudeDecisions';

type RuleStage = IRuleProposalView['stage'];

const system = (id: number, name: string): ISystemSummaryResponse => ({ id, name, stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 });

const pedido = (over: Partial<IPendingAction> = {}): IPendingAction => ({
  id: 7, kind: 'START_AUDIT', summary: 'Auditar a branch main', status: 'PENDING', createdAt: '2026-11-27T01:00:00', expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null, ...over,
});

const regra = (stage: RuleStage, over: Partial<IRuleProposalView> = {}): IRuleProposalView => ({
  id: 3, slug: 'r', title: 'sem-sql-concatenado', rationale: null, semgrepYaml: '', stage, stageLabel: '', copiedFromId: null, sampleNote: null,
  observationStartedAt: null, decisionNote: null, evidence: { hits: 0, falsePositives: 0, auditsSinceStart: 0, precision: 0 }, minPrecision: 0.8, minHits: 5,
  sharedEligible: false, runsInAudits: false, ...over,
});

const sugestao = (over: Partial<IReviewSuggestion> = {}): IReviewSuggestion => ({
  id: 9, title: 'Extrair a validação', detail: 'Está repetida', ruleRef: 'dup-1', file: 'A.java', line: 12, status: 'OPEN', createdAt: '', ...over,
});

const review = (over: Partial<IClaudeReview> = {}): IClaudeReview => ({ openSuggestions: 0, suggestions: [], proposals: [], ...over });

const sys = system(1, 'sentinel');

function only(r: IClaudeReview) {
  return buildDecisions({ pending: [], reviews: [{ system: sys, review: r }] });
}

describe('buildDecisions', () => {
  it('sem nada esperando, devolve lista vazia', () => {
    expect(buildDecisions({ pending: [], reviews: [{ system: sys, review: review() }, { system: sys, review: undefined }] })).toEqual([]);
  });

  it('pedido pendente vira Aprovar/Recusar e pedido já decidido não aparece', () => {
    const items = buildDecisions({ pending: [{ system: sys, actions: [pedido(), pedido({ id: 8, status: 'APPROVED' })] }], reviews: [] });
    expect(items).toHaveLength(1);
    expect(items[0].primary).toEqual({ label: 'Aprovar', action: { type: 'pending', systemId: 1, actionId: 7, decision: 'approve' } });
    expect(items[0].secondary?.action).toEqual({ type: 'pending', systemId: 1, actionId: 7, decision: 'reject' });
  });

  it('regra proposta pede revisão e oferece descartar', () => {
    const [d] = only(review({ proposals: [regra('PROPOSED')] }));
    expect(d.primary.action).toEqual({ type: 'review', systemId: 1, path: 'rules/3/review', body: undefined });
    expect(d.secondary).toMatchObject({ label: 'Descartar', action: { path: 'rules/3/discard', body: { note: '' } } });
  });

  it('regra revisada pede o resultado da amostra: passou ou falhou', () => {
    const [d] = only(review({ proposals: [regra('REVIEWED')] }));
    expect(d.primary).toMatchObject({ label: 'Amostra passou', action: { path: 'rules/3/sample', body: { passed: true, note: '' } } });
    expect(d.secondary).toMatchObject({ label: 'Amostra falhou', action: { body: { passed: false, note: '' } } });
  });

  it('amostra que passou oferece iniciar a observação', () => {
    const [d] = only(review({ proposals: [regra('SAMPLE_PASSED')] }));
    expect(d.primary).toMatchObject({ label: 'Iniciar observação', action: { path: 'rules/3/observe' } });
  });

  it('regra em observação só aparece quando tem achados e precisão suficientes', () => {
    expect(only(review({ proposals: [regra('OBSERVING', { evidence: { hits: 4, falsePositives: 0, auditsSinceStart: 3, precision: 1 } })] }))).toEqual([]);
    expect(only(review({ proposals: [regra('OBSERVING', { evidence: { hits: 9, falsePositives: 5, auditsSinceStart: 3, precision: 0.4 } })] }))).toEqual([]);
    const [d] = only(review({ proposals: [regra('OBSERVING', { evidence: { hits: 9, falsePositives: 1, auditsSinceStart: 3, precision: 0.9 } })] }));
    expect(d.primary).toMatchObject({ label: 'Promover', action: { path: 'rules/3/promote', body: { note: '' } } });
    expect(d.detail).toContain('precisão 90%');
  });

  it('regra promovida ou descartada não espera ninguém', () => {
    expect(only(review({ proposals: [regra('PROMOTED'), regra('DISCARDED', { id: 4 })] }))).toEqual([]);
  });

  it('só sugestões abertas aparecem, com Tratada e Dispensar', () => {
    const items = only(review({ suggestions: [sugestao(), sugestao({ id: 10, status: 'DISMISSED' }), sugestao({ id: 11, status: 'ADDRESSED' })] }));
    expect(items).toHaveLength(1);
    expect(items[0].detail).toBe('Está repetida — dup-1 · A.java:12');
    expect(items[0].primary.action).toMatchObject({ path: 'suggestions/9/status', body: { status: 'ADDRESSED' } });
    expect(items[0].secondary?.action).toMatchObject({ body: { status: 'DISMISSED' } });
  });

  it('ordena pedidos, depois regras, depois sugestões, juntando todos os sistemas', () => {
    const other = system(2, 'outro');
    const items = buildDecisions({
      pending: [{ system: other, actions: [pedido({ id: 20 })] }],
      reviews: [{ system: sys, review: review({ suggestions: [sugestao()], proposals: [regra('PROPOSED')] }) }],
    });
    expect(items.map((d) => d.kind)).toEqual(['pedido', 'regra', 'sugestao']);
    expect(items.map((d) => d.systemName)).toEqual(['outro', 'sentinel', 'sentinel']);
    expect(new Set(items.map((d) => d.key)).size).toBe(3);
  });
});
