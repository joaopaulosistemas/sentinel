import type { IClaudeReview, IPendingAction, IRuleProposalView, ISystemSummaryResponse } from '../types';
import { startLabel } from './timeAgo';

/** O que acontece quando a pessoa clica em um botão de uma decisão. */
export type DecisionAction =
  | { type: 'pending'; systemId: number; actionId: number; decision: 'approve' | 'reject' }
  | { type: 'review'; systemId: number; path: string; body?: unknown };

export interface IDecisionButton {
  label: string;
  action: DecisionAction;
}

export type DecisionKind = 'pedido' | 'regra' | 'sugestao';

export interface IDecision {
  key: string;
  kind: DecisionKind;
  systemId: number;
  systemName: string;
  title: string;
  detail: string;
  primary: IDecisionButton;
  secondary: IDecisionButton | null;
}

export const KIND_LABEL: Record<DecisionKind, string> = { pedido: 'Pedido', regra: 'Regra', sugestao: 'Sugestão' };

const KIND_ORDER: Record<DecisionKind, number> = { pedido: 0, regra: 1, sugestao: 2 };

type ReviewAction = (path: string, body?: unknown) => DecisionAction;

function pendingItem(system: ISystemSummaryResponse, a: IPendingAction): IDecision {
  const act = (decision: 'approve' | 'reject'): DecisionAction => ({ type: 'pending', systemId: system.id, actionId: a.id, decision });
  return {
    key: `pedido-${system.id}-${a.id}`,
    kind: 'pedido',
    systemId: system.id,
    systemName: system.name,
    title: a.summary,
    detail: `Pedido do Claude ${startLabel(a.createdAt)}. Nada acontece até você aprovar.`,
    primary: { label: 'Aprovar', action: act('approve') },
    secondary: { label: 'Recusar', action: act('reject') },
  };
}

function evidenceText(p: IRuleProposalView): string {
  const e = p.evidence;
  return `${e.hits} achado(s), ${e.falsePositives} falso(s) positivo(s), precisão ${Math.round(e.precision * 100)}% (mínimo ${Math.round(p.minPrecision * 100)}% e ${p.minHits} achados).`;
}

/** O próximo passo de uma regra no funil, ou nulo quando ela só espera o tempo passar (observação sem evidência suficiente). */
function ruleStep(p: IRuleProposalView, review: ReviewAction): { detail: string; primary: IDecisionButton; secondary: IDecisionButton | null } | null {
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

function ruleItems(system: ISystemSummaryResponse, review: IClaudeReview): IDecision[] {
  const act: ReviewAction = (path, body) => ({ type: 'review', systemId: system.id, path, body });
  return review.proposals.flatMap((p) => {
    const step = ruleStep(p, act);
    if (!step) return [];
    return [{ key: `regra-${system.id}-${p.id}`, kind: 'regra' as const, systemId: system.id, systemName: system.name, title: `Regra ${p.title}`, detail: step.detail, primary: step.primary, secondary: step.secondary }];
  });
}

function suggestionItems(system: ISystemSummaryResponse, review: IClaudeReview): IDecision[] {
  const act: ReviewAction = (path, body) => ({ type: 'review', systemId: system.id, path, body });
  return review.suggestions
    .filter((s) => s.status === 'OPEN')
    .map((s) => ({
      key: `sugestao-${system.id}-${s.id}`,
      kind: 'sugestao' as const,
      systemId: system.id,
      systemName: system.name,
      title: s.title,
      detail: [s.detail, [s.ruleRef, s.file ? `${s.file}${s.line ? `:${s.line}` : ''}` : null].filter(Boolean).join(' · ')].filter(Boolean).join(' — '),
      primary: { label: 'Tratada', action: act(`suggestions/${s.id}/status`, { status: 'ADDRESSED' }) },
      secondary: { label: 'Dispensar', action: act(`suggestions/${s.id}/status`, { status: 'DISMISSED' }) },
    }));
}

interface IBuildInput {
  pending: Array<{ system: ISystemSummaryResponse; actions: IPendingAction[] }>;
  reviews: Array<{ system: ISystemSummaryResponse; review: IClaudeReview | undefined }>;
}

/** Junta, de todos os sistemas, o que espera uma decisão: pedidos primeiro, depois regras, depois sugestões. */
export function buildDecisions({ pending, reviews }: IBuildInput): IDecision[] {
  const items: IDecision[] = [
    ...pending.flatMap(({ system, actions }) => actions.filter((a) => a.status === 'PENDING').map((a) => pendingItem(system, a))),
    ...reviews.flatMap(({ system, review }) => (review ? [...ruleItems(system, review), ...suggestionItems(system, review)] : [])),
  ];
  return items.sort((a, b) => KIND_ORDER[a.kind] - KIND_ORDER[b.kind]);
}
