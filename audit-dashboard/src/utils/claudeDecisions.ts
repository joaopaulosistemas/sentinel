import type { IClaudeReview, IPendingAction, IRuleProposalView, ISystemSummaryResponse } from '../types';
import { startLabel } from './timeAgo';

/** O que acontece quando a pessoa clica em um botão de uma decisão. */
export type DecisionAction =
  | { type: 'pending'; systemId: number; actionId: number; decision: 'approve' | 'reject' | 'reopen' | 'undo' }
  | { type: 'review'; systemId: number; path: string; body?: unknown };

export interface IDecisionButton {
  label: string;
  action: DecisionAction;
}

export type DecisionKind = 'pedido' | 'regra' | 'sugestao';
type ResultTone = 'good' | 'bad' | 'warn' | 'idle';

/** O que foi decidido: o selo, quando, e se dá para voltar atrás (ou por que não dá). */
interface IDecisionResult {
  label: string;
  tone: ResultTone;
  /** Instante da decisão; nulo quando foi tomada antes de o Sentinel guardar a hora. */
  at: string | null;
  undo: IDecisionButton | null;
  /** Por que não dá para desfazer, quando não dá. */
  undoBlocked: string | null;
}

export interface IDecision {
  key: string;
  kind: DecisionKind;
  systemId: number;
  systemName: string;
  title: string;
  detail: string;
  /** Nulo nas decisões já tomadas. */
  primary: IDecisionButton | null;
  secondary: IDecisionButton | null;
  /** Presente só nas decisões já tomadas. */
  result: IDecisionResult | null;
}

export const KIND_LABEL: Record<DecisionKind, string> = { pedido: 'Pedido', regra: 'Regra', sugestao: 'Sugestão' };

const KIND_ORDER: Record<DecisionKind, number> = { pedido: 0, regra: 1, sugestao: 2 };

type ReviewAction = (path: string, body?: unknown) => DecisionAction;

const reviewAct = (systemId: number): ReviewAction => (path, body) => ({ type: 'review', systemId, path, body });

const waiting = (d: Omit<IDecision, 'result'>): IDecision => ({ ...d, result: null });

const decided = (d: Omit<IDecision, 'primary' | 'secondary' | 'result'>, result: IDecisionResult): IDecision => ({ ...d, primary: null, secondary: null, result });


function pedidoBase(system: ISystemSummaryResponse, a: IPendingAction) {
  return {
    key: `pedido-${system.id}-${a.id}`,
    kind: 'pedido' as const,
    systemId: system.id,
    systemName: system.name,
    title: a.summary,
  };
}

function pedidoResult(system: ISystemSummaryResponse, a: IPendingAction): IDecisionResult {
  const act = (decision: 'reopen' | 'undo'): DecisionAction => ({ type: 'pending', systemId: system.id, actionId: a.id, decision });
  const at = a.decidedAt ?? null;
  if (a.status === 'REJECTED') {
    return { label: 'Recusado', tone: 'bad', at, undo: { label: 'Reabrir', action: act('reopen') }, undoBlocked: null };
  }
  if (a.status === 'EXPIRED') {
    return { label: 'Venceu sem decisão', tone: 'idle', at, undo: null, undoBlocked: 'Venceu sem decisão: peça de novo ao Claude.' };
  }
  if (a.undoneAt) {
    return { label: 'Desfeito', tone: 'idle', at: a.undoneAt, undo: null, undoBlocked: 'Já foi desfeito.' };
  }
  if (a.undoable) {
    return { label: 'Aprovado', tone: 'good', at, undo: { label: 'Desfazer', action: act('undo') }, undoBlocked: null };
  }
  const blocked = a.kind === 'START_AUDIT'
    ? 'A auditoria já começou: não dá para desfazer.'
    : 'Aprovado antes de o Sentinel guardar o que criou: remova a supressão na lista de supressões do sistema.';
  return { label: 'Aprovado', tone: 'good', at, undo: null, undoBlocked: blocked };
}

function pedidoItem(system: ISystemSummaryResponse, a: IPendingAction): IDecision {
  const base = pedidoBase(system, a);
  if (a.status !== 'PENDING') {
    return decided({ ...base, detail: a.resultNote ?? `Pedido do Claude ${startLabel(a.createdAt)}.` }, pedidoResult(system, a));
  }
  const act = (decision: 'approve' | 'reject'): DecisionAction => ({ type: 'pending', systemId: system.id, actionId: a.id, decision });
  return waiting({
    ...base,
    detail: `Pedido do Claude ${startLabel(a.createdAt)}. Nada acontece até você aprovar.`,
    primary: { label: 'Aprovar', action: act('approve') },
    secondary: { label: 'Recusar', action: act('reject') },
  });
}


function evidenceText(p: IRuleProposalView): string {
  const evidencia = p.evidence;
  return `${evidencia.hits} achado(s), ${evidencia.falsePositives} falso(s) positivo(s), precisão ${Math.round(evidencia.precision * 100)}% (mínimo ${Math.round(p.minPrecision * 100)}% e ${p.minHits} achados).`;
}

interface IRuleStep {
  detail: string;
  primary: IDecisionButton;
  secondary: IDecisionButton | null;
}

/** O próximo passo de uma regra no funil, ou nulo quando ela só espera o tempo passar (observação sem evidência suficiente). */
function ruleStep(p: IRuleProposalView, review: ReviewAction): IRuleStep | null {
  const discard: IDecisionButton = { label: 'Descartar', action: review(`rules/${p.id}/discard`, { note: '' }) };
  switch (p.stage) {
    case 'PROPOSED':
      return { detail: 'Proposta nova: leia a regra antes de testar.', primary: { label: 'Revisei a regra', action: review(`rules/${p.id}/review`) }, secondary: discard };
    case 'REVIEWED':
      return {
        detail: 'Rode o teste de amostra e diga o resultado.',
        primary: { label: 'Amostra passou', action: review(`rules/${p.id}/sample`, { passed: true, note: '' }) },
        secondary: { label: 'Amostra falhou', action: review(`rules/${p.id}/sample`, { passed: false, note: '' }) },
      };
    case 'SAMPLE_PASSED':
      return { detail: 'A amostra passou: pode começar a observar fora da nota.', primary: { label: 'Iniciar observação', action: review(`rules/${p.id}/observe`) }, secondary: discard };
    case 'OBSERVING':
      return p.evidence.hits >= p.minHits && p.evidence.precision >= p.minPrecision
        ? { detail: `Pronta para promover: ${evidenceText(p)}`, primary: { label: 'Promover', action: review(`rules/${p.id}/promote`, { note: '' }) }, secondary: discard }
        : null;
    default:
      return null;
  }
}

function ruleResult(p: IRuleProposalView, review: ReviewAction): IDecisionResult | null {
  const at = p.decidedAt ?? null;
  if (p.stage === 'PROMOTED') {
    return { label: 'Promovida', tone: 'good', at, undo: null, undoBlocked: 'Promover muda a nota: a regra já pesa nas auditorias e não se desfaz aqui.' };
  }
  if (p.stage === 'DISCARDED') {
    return p.restorable
      ? { label: 'Descartada', tone: 'bad', at, undo: { label: 'Restaurar', action: review(`rules/${p.id}/restore`) }, undoBlocked: null }
      : { label: 'Descartada', tone: 'bad', at, undo: null, undoBlocked: 'Descartada antes de o Sentinel guardar a etapa anterior: proponha de novo.' };
  }
  return null;
}

function ruleItems(system: ISystemSummaryResponse, review: IClaudeReview): IDecision[] {
  const act = reviewAct(system.id);
  return review.proposals.flatMap((p) => {
    const base = { key: `regra-${system.id}-${p.id}`, kind: 'regra' as const, systemId: system.id, systemName: system.name, title: `Regra ${p.title}` };
    const result = ruleResult(p, act);
    if (result) return [decided({ ...base, detail: p.decisionNote ?? '' }, result)];
    const step = ruleStep(p, act);
    return step ? [waiting({ ...base, detail: step.detail, primary: step.primary, secondary: step.secondary })] : [];
  });
}


function suggestionDetail(s: IClaudeReview['suggestions'][number]): string {
  const where = [s.ruleRef, s.file ? `${s.file}${s.line ? `:${s.line}` : ''}` : null].filter(Boolean).join(' · ');
  return [s.detail, where].filter(Boolean).join(' — ');
}

function suggestionItems(system: ISystemSummaryResponse, review: IClaudeReview): IDecision[] {
  const act = reviewAct(system.id);
  return review.suggestions.map((s) => {
    const base = { key: `sugestao-${system.id}-${s.id}`, kind: 'sugestao' as const, systemId: system.id, systemName: system.name, title: s.title, detail: suggestionDetail(s) };
    if (s.status === 'OPEN') {
      return waiting({
        ...base,
        primary: { label: 'Tratada', action: act(`suggestions/${s.id}/status`, { status: 'ADDRESSED' }) },
        secondary: { label: 'Dispensar', action: act(`suggestions/${s.id}/status`, { status: 'DISMISSED' }) },
      });
    }
    const reopen: IDecisionButton = { label: 'Reabrir', action: act(`suggestions/${s.id}/status`, { status: 'OPEN' }) };
    return decided(base, { label: s.status === 'ADDRESSED' ? 'Tratada' : 'Dispensada', tone: s.status === 'ADDRESSED' ? 'good' : 'idle', at: s.decidedAt ?? null, undo: reopen, undoBlocked: null });
  });
}


interface IBuildInput {
  pending: Array<{ system: ISystemSummaryResponse; actions: IPendingAction[] }>;
  reviews: Array<{ system: ISystemSummaryResponse; review: IClaudeReview | undefined }>;
}

const time = (iso: string | null | undefined): number => (iso ? Date.parse(iso) || 0 : 0);

/**
 * Junta, de todos os sistemas, o que espera uma decisão (pedidos, depois regras, depois sugestões) e o que já foi decidido
 * (o mais recente primeiro; o que não tem hora vai para o fim). Quem decide não perde o item: ele continua na lista, com o resultado.
 */
export function buildDecisions({ pending, reviews }: IBuildInput): IDecision[] {
  const all: IDecision[] = [
    ...pending.flatMap(({ system, actions }) => actions.map((a) => pedidoItem(system, a))),
    ...reviews.flatMap(({ system, review }) => (review ? [...ruleItems(system, review), ...suggestionItems(system, review)] : [])),
  ];
  const open = all.filter((d) => d.result === null).sort((a, b) => KIND_ORDER[a.kind] - KIND_ORDER[b.kind]);
  const done = all.filter((d) => d.result !== null).sort((a, b) => time(b.result?.at) - time(a.result?.at));
  return [...open, ...done];
}

export const isWaiting = (d: IDecision): boolean => d.result === null;
