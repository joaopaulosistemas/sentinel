import type { ICrumb } from '../../components/PageShell';
import type { IAuditRunResponse, IFindingResponse, IFindingsOverview, Severity } from '../../types';
import { suggestionFor } from '../../utils/claudeHint';

type OverviewItem = IFindingsOverview['items'][number];

export interface IRowsView {
  rows: IFindingResponse[];
  total: number;
  pages: number;
  /** Achados de baixa confiança ocultos agora (só no modo "uma auditoria"). */
  lowHidden: number;
  /** Achados de baixa confiança dentro dos filtros (só no modo "uma auditoria"). */
  lowTotal: number;
}

interface IRowsInput {
  auditMode: boolean;
  /** Achados da auditoria aberta (modo "uma auditoria"). */
  single: IFindingResponse[];
  severities: Severity[];
  showLow: boolean;
  page: number;
  pageSize: number;
  overview: IFindingsOverview | undefined;
}

const pagesOf = (total: number, pageSize: number) => Math.max(1, Math.ceil(total / pageSize));

/**
 * Linhas da página atual. Com várias auditorias a API já pagina; com uma só, a filtragem por várias severidades
 * (ex.: crítica e alta) e a ocultação de baixa confiança (teste, exemplo, regra com muitos falsos positivos) são feitas aqui.
 */
export function pickRows({ auditMode, single, severities, showLow, page, pageSize, overview }: IRowsInput): IRowsView {
  if (!auditMode) {
    const total = overview?.total ?? 0;
    return { rows: overview?.items.map((i) => i.finding) ?? [], total, pages: pagesOf(total, pageSize), lowHidden: 0, lowTotal: 0 };
  }
  const bySeverity = single.filter((f) => severities.length === 0 || severities.includes(f.severity as Severity));
  const lowTotal = bySeverity.filter((f) => f.confidence === 'LOW').length;
  const visible = showLow ? bySeverity : bySeverity.filter((f) => f.confidence !== 'LOW');
  return {
    rows: visible.slice((page - 1) * pageSize, page * pageSize),
    total: visible.length,
    pages: pagesOf(visible.length, pageSize),
    lowHidden: showLow ? 0 : lowTotal,
    lowTotal,
  };
}

export interface IReviewScope {
  auditMode: boolean;
  auditSystemId: number | undefined;
  systemId: number | undefined;
  items: OverviewItem[];
}

export interface IClaudeNoteScope extends Omit<IReviewScope, 'items'> {
  itemByFinding: Map<number, OverviewItem>;
  suggestionsBySystem: Map<number, Parameters<typeof suggestionFor>[1]>;
}

export interface ICrumbsScope {
  auditMode: boolean;
  audit: IAuditRunResponse | undefined;
  auditId: number;
}

/** Quantidade de achados altos a partir da qual o cartão do sistema ganha a borda vermelha. */
export const HIGH_ALERT_THRESHOLD = 50;

/** Sistemas cujas sugestões do Claude marcam a lista: o da auditoria aberta, o escolhido ou todos os que aparecem. */
export function reviewSystemIds({ auditMode, auditSystemId, systemId, items }: IReviewScope): number[] {
  if (auditMode) return auditSystemId !== undefined ? [auditSystemId] : [];
  if (systemId !== undefined) return [systemId];
  return [...new Set(items.map((i) => i.systemId))];
}

/** Função que devolve a sugestão do Claude para um achado, procurando no sistema a que ele pertence. */
export function claudeNoteFinder({ auditMode, auditSystemId, systemId, itemByFinding, suggestionsBySystem }: IClaudeNoteScope) {
  return (f: IFindingResponse) => {
    const scopedSystemId = auditMode ? auditSystemId : (systemId ?? itemByFinding.get(f.id)?.systemId);
    return scopedSystemId === undefined ? undefined : suggestionFor(f, suggestionsBySystem.get(scopedSystemId) ?? []);
  };
}

export function findingsCrumbs({ auditMode, audit, auditId }: ICrumbsScope): ICrumb[] {
  if (!auditMode) return [{ label: 'Início', to: '/' }, { label: 'Informações de Auditoria' }];
  const system: ICrumb[] = audit
    ? [{ label: audit.systemName, to: audit.systemId !== undefined ? `/systems/${audit.systemId}` : undefined }]
    : [];
  return [{ label: 'Início', to: '/' }, { label: 'Sistemas', to: '/systems' }, ...system, { label: `Auditoria #${auditId}`, to: `/audits/${auditId}` }, { label: 'Achados' }];
}

/** Cor da borda do cartão do sistema: vermelho com críticos (ou muitos altos), laranja só com altos, verde sem nenhum. */
export function edgeColor(critical: number, high: number): string {
  if (critical > 0 || high >= HIGH_ALERT_THRESHOLD) return '#da1e28';
  return high > 0 ? '#ff832b' : '#198038';
}
