import type { ISystemSummaryResponse } from '../types';
import { TARGET_SCORE, scoreColor as bandColor } from './scoreBand';

/** Depois de quantos dias sem auditar um sistema passa a pedir atenção. */
export const STALE_DAYS = 5;

interface IAttentionItem {
  system: ISystemSummaryResponse;
  reasons: string[];
  /** Quanto maior, mais urgente; usado só para ordenar. */
  weight: number;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Dias desde a última auditoria concluída; nulo se nunca foi auditado. */
export function daysSinceAudit(system: Pick<ISystemSummaryResponse, 'lastAuditDate'>, now: number): number | null {
  if (!system.lastAuditDate) return null;
  const auditTimestamp = Date.parse(system.lastAuditDate);
  return Number.isNaN(auditTimestamp) ? null : Math.max(0, Math.floor((now - auditTimestamp) / DAY_MS));
}

/** Depois de quantos dias sem auditar a lista de Sistemas mostra o selo (mais de uma semana). */
export const AUDIT_BADGE_DAYS = 7;
/** Depois de quantos dias o selo passa de "atenção" para "crítico". */
const AUDIT_BADGE_CRITICAL_DAYS = 14;

type AuditAgeLevel = 'attention' | 'critical' | 'never';

export interface IAuditAge {
  level: AuditAgeLevel;
  /** Nulo só em "never" */
  days: number | null;
}

/**
 * Idade da última auditoria para o selo da lista de Sistemas; nulo enquanto estiver em dia (até 7 dias).
 * Sem data de auditoria concluída: "never" se o sistema não tem auditoria nenhuma; se tem (todas falharam ou ainda
 * correm), nulo, porque não dá para dizer há quantos dias.
 */
export function auditAge(system: Pick<ISystemSummaryResponse, 'lastAuditDate' | 'auditCount'>, now: number): IAuditAge | null {
  const days = daysSinceAudit(system, now);
  if (days === null) {
    const hasAudits = (system.auditCount ?? 0) > 0;
    return hasAudits ? null : { level: 'never', days: null };
  }
  if (days <= AUDIT_BADGE_DAYS) return null;
  return { level: days > AUDIT_BADGE_CRITICAL_DAYS ? 'critical' : 'attention', days };
}

export const isStale = (system: ISystemSummaryResponse, now: number) => {
  const days = daysSinceAudit(system, now);
  return days === null || days > STALE_DAYS;
};

/** Sistemas que pedem uma ação, do mais urgente ao menos: críticos abertos, nota abaixo da meta, auditoria antiga. */
export function systemsNeedingAttention(systems: ISystemSummaryResponse[], now: number): IAttentionItem[] {
  const items: IAttentionItem[] = [];
  for (const system of systems) {
    const reasons: string[] = [];
    let weight = 0;
    if (system.openCritical > 0) {
      reasons.push(`${system.openCritical} ${system.openCritical === 1 ? 'crítico aberto' : 'críticos abertos'}`);
      weight += 100 + system.openCritical;
    }
    if (system.lastAuditScore !== null && system.lastAuditScore < TARGET_SCORE) {
      reasons.push(`nota ${Math.round(system.lastAuditScore)}, abaixo da meta ${TARGET_SCORE}`);
      weight += 50 + (TARGET_SCORE - system.lastAuditScore) / 10;
    }
    const days = daysSinceAudit(system, now);
    if (days === null) {
      reasons.push('nunca auditado');
      weight += 40;
    } else if (days > STALE_DAYS) {
      reasons.push(`sem auditar há ${days} dias`);
      weight += 20 + Math.min(days, 30) / 10;
    }
    if (reasons.length > 0) items.push({ system, reasons, weight });
  }
  return items.sort((a, b) => b.weight - a.weight || a.system.name.localeCompare(b.system.name));
}

interface IAttentionRow {
  system: ISystemSummaryResponse;
  /** Nulo quando nunca foi auditado */
  score: number | null;
  reason: string;
}

const points = (n: number) => `${n} ${n === 1 ? 'ponto' : 'pontos'}`;

/** Cor da nota: ver `scoreBand` (régua única). */
export const scoreColor = (n: number | null) => bandColor(n);

interface IAuditContext {
  system: ISystemSummaryResponse;
  now: number;
}

function attentionReason({ system, now }: IAuditContext): string {
  const lastScore = system.lastAuditScore;
  if (lastScore === null) return 'nunca auditado';
  const critical = system.openCritical > 0 ? `${system.openCritical} ${system.openCritical === 1 ? 'crítico' : 'críticos'}` : 'Sem críticos';
  return `${critical} · ${auditDetail({ system, now }, lastScore)}`;
}

function auditDetail({ system, now }: IAuditContext, lastScore: number): string {
  const days = daysSinceAudit(system, now);
  if (days !== null && days > STALE_DAYS) return `não é auditado há ${days} dias`;
  const delta = system.scoreDelta == null ? 0 : Math.round(system.scoreDelta);
  if (delta !== 0) return `nota ${delta > 0 ? 'subiu' : 'caiu'} ${points(Math.abs(delta))}`;
  return lastScore >= TARGET_SCORE ? 'dentro da meta' : 'abaixo da meta';
}

/**
 * Todos os sistemas do menor para o maior nota (nunca auditados primeiro), cada um com o motivo em uma linha:
 * críticos abertos, e depois o dado mais relevante (auditoria velha, variação da nota ou meta).
 */
export function attentionRows(systems: ISystemSummaryResponse[], now: number): IAttentionRow[] {
  return systems
    .map((system): IAttentionRow => ({
      system,
      score: system.lastAuditScore === null ? null : Math.round(system.lastAuditScore),
      reason: attentionReason({ system, now }),
    }))
    .sort((a, b) => (a.score ?? -1) - (b.score ?? -1) || a.system.name.localeCompare(b.system.name));
}
