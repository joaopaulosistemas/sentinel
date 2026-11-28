import { describe, expect, it } from 'vitest';
import type { IClaudeReview, IPendingAction, IReviewSuggestion, IRuleProposalView, ISystemSummaryResponse } from '../types';
import { buildDecisions, isWaiting } from './claudeDecisions';

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

  it('pedido pendente vira Aprovar/Recusar e o já aprovado sai de esperando, mas continua na lista', () => {
    const all = buildDecisions({ pending: [{ system: sys, actions: [pedido(), pedido({ id: 8, status: 'APPROVED' })] }], reviews: [] });
    expect(all).toHaveLength(2);
    const items = all.filter(isWaiting);
    expect(items).toHaveLength(1);
    expect(items[0].primary).toEqual({ label: 'Aprovar', action: { type: 'pending', systemId: 1, actionId: 7, decision: 'approve' } });
    expect(items[0].secondary?.action).toEqual({ type: 'pending', systemId: 1, actionId: 7, decision: 'reject' });
  });

  it('regra proposta pede revisão e oferece descartar', () => {
    const [d] = only(review({ proposals: [regra('PROPOSED')] }));
    expect(d.primary?.action).toEqual({ type: 'review', systemId: 1, path: 'rules/3/review', body: undefined });
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

  it('regra promovida ou descartada não espera ninguém, mas fica como decidida', () => {
    const items = only(review({ proposals: [regra('PROMOTED'), regra('DISCARDED', { id: 4 })] }));
    expect(items.filter(isWaiting)).toEqual([]);
    expect(items).toHaveLength(2);
  });

  it('só sugestões abertas esperam, com Tratada e Dispensar', () => {
    const all = only(review({ suggestions: [sugestao(), sugestao({ id: 10, status: 'DISMISSED' }), sugestao({ id: 11, status: 'ADDRESSED' })] }));
    expect(all).toHaveLength(3);
    const items = all.filter(isWaiting);
    expect(items).toHaveLength(1);
    expect(items[0].detail).toBe('Está repetida — dup-1 · A.java:12');
    expect(items[0].primary?.action).toMatchObject({ path: 'suggestions/9/status', body: { status: 'ADDRESSED' } });
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

  it('sugestão decidida continua na lista, com resultado, hora e Reabrir', () => {
    const items = only(review({ suggestions: [sugestao({ status: 'DISMISSED', decidedAt: '2026-11-27T10:00:00' })] }));
    expect(items).toHaveLength(1);
    expect(items[0].primary).toBeNull();
    expect(items[0].result).toMatchObject({ label: 'Dispensada', at: '2026-11-27T10:00:00', undoBlocked: null });
    expect(items[0].result?.undo).toMatchObject({ label: 'Reabrir', action: { path: 'suggestions/9/status', body: { status: 'OPEN' } } });
    expect(isWaiting(items[0])).toBe(false);
  });

  it('pedido recusado pode ser reaberto; vencido não', () => {
    const items = buildDecisions({
      pending: [{ system: sys, actions: [pedido({ id: 1, status: 'REJECTED', decidedAt: '2026-11-27T09:00:00' }), pedido({ id: 2, status: 'EXPIRED' })] }],
      reviews: [],
    });
    const rejected = items.find((d) => d.key === 'pedido-1-1');
    const expired = items.find((d) => d.key === 'pedido-1-2');
    expect(rejected?.result).toMatchObject({ label: 'Recusado', tone: 'bad' });
    expect(rejected?.result?.undo).toMatchObject({ label: 'Reabrir', action: { type: 'pending', actionId: 1, decision: 'reopen' } });
    expect(expired?.result?.undo).toBeNull();
    expect(expired?.result?.undoBlocked).toContain('Venceu');
  });

  it('pedido aprovado: desfaz a supressão, mas não a auditoria que já começou', () => {
    const items = buildDecisions({
      pending: [{
        system: sys,
        actions: [
          pedido({ id: 1, kind: 'SUPPRESS_FINDING', status: 'APPROVED', undoable: true, decidedAt: '2026-11-27T09:00:00' }),
          pedido({ id: 2, kind: 'START_AUDIT', status: 'APPROVED', undoable: false, decidedAt: '2026-11-27T09:10:00' }),
          pedido({ id: 3, kind: 'SUPPRESS_FINDING', status: 'APPROVED', undoable: false, undoneAt: '2026-11-27T11:00:00' }),
          pedido({ id: 4, kind: 'SUPPRESS_FINDING', status: 'APPROVED', undoable: false }),
        ],
      }],
      reviews: [],
    });
    const by = (id: number) => items.find((d) => d.key === `pedido-1-${id}`)?.result;
    expect(by(1)?.undo).toMatchObject({ label: 'Desfazer', action: { decision: 'undo', actionId: 1 } });
    expect(by(2)?.undo).toBeNull();
    expect(by(2)?.undoBlocked).toContain('já começou');
    expect(by(3)).toMatchObject({ label: 'Desfeito', at: '2026-11-27T11:00:00', undo: null });
    expect(by(4)?.undoBlocked).toContain('lista de supressões');
  });

  it('regra descartada pode ser restaurada quando o Sentinel guardou a etapa; promovida não se desfaz', () => {
    const items = only(review({
      proposals: [
        regra('DISCARDED', { id: 3, restorable: true, decidedAt: '2026-11-27T10:00:00' }),
        regra('DISCARDED', { id: 4, restorable: false }),
        regra('PROMOTED', { id: 5, decidedAt: '2026-11-27T08:00:00' }),
      ],
    }));
    const by = (id: number) => items.find((d) => d.key === `regra-1-${id}`)?.result;
    expect(by(3)?.undo).toMatchObject({ label: 'Restaurar', action: { path: 'rules/3/restore' } });
    expect(by(4)?.undo).toBeNull();
    expect(by(4)?.undoBlocked).toContain('proponha de novo');
    expect(by(5)).toMatchObject({ label: 'Promovida', undo: null });
    expect(by(5)?.undoBlocked).toContain('nota');
  });

  it('esperando vem primeiro (pedidos, regras, sugestões); decididas depois, da mais recente para a mais antiga', () => {
    const items = buildDecisions({
      pending: [{ system: sys, actions: [pedido({ id: 1, status: 'REJECTED', decidedAt: '2026-11-27T09:00:00' }), pedido({ id: 2 })] }],
      reviews: [{
        system: sys,
        review: review({
          suggestions: [sugestao({ id: 9, status: 'ADDRESSED', decidedAt: '2026-11-27T12:00:00' }), sugestao({ id: 10 }), sugestao({ id: 11, status: 'DISMISSED' })],
          proposals: [regra('PROPOSED')],
        }),
      }],
    });
    expect(items.map((d) => d.key)).toEqual(['pedido-1-2', 'regra-1-3', 'sugestao-1-10', 'sugestao-1-9', 'pedido-1-1', 'sugestao-1-11']);
  });
});
