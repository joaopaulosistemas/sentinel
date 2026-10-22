import type { SystemSummaryResponse } from '../types';
import { TARGET_SCORE } from './resultVerdict';

/** Depois de quantos dias sem auditar um sistema passa a pedir atenção. */
export const STALE_DAYS = 5;

export interface AttentionItem {
  system: SystemSummaryResponse;
  reasons: string[];
  /** Quanto maior, mais urgente; usado só para ordenar. */
  weight: number;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Dias desde a última auditoria concluída; nulo se nunca foi auditado. */
export function daysSinceAudit(system: Pick<SystemSummaryResponse, 'lastAuditDate'>, now: number): number | null {
  if (!system.lastAuditDate) return null;
  const t = Date.parse(system.lastAuditDate);
  return Number.isNaN(t) ? null : Math.max(0, Math.floor((now - t) / DAY_MS));
}

export const isStale = (system: SystemSummaryResponse, now: number) => {
  const days = daysSinceAudit(system, now);
  return days === null || days > STALE_DAYS;
};

/** Sistemas que pedem uma ação, do mais urgente ao menos: críticos abertos, nota abaixo da meta, auditoria antiga. */
export function systemsNeedingAttention(systems: SystemSummaryResponse[], now: number): AttentionItem[] {
  const items: AttentionItem[] = [];
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
