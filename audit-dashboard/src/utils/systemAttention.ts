import type { SystemSummaryResponse } from '../types';
import { TARGET_SCORE, scoreColor as bandColor } from './scoreBand';

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

export interface AttentionRow {
  system: SystemSummaryResponse;
  /** Nulo quando nunca foi auditado */
  score: number | null;
  reason: string;
}

const points = (n: number) => `${n} ${n === 1 ? 'ponto' : 'pontos'}`;

/** Cor da nota: ver `scoreBand` (régua única). */
export const scoreColor = (n: number | null) => bandColor(n);

function attentionReason(system: SystemSummaryResponse, lastScore: number | null, now: number): string {
  if (lastScore === null) return 'nunca auditado';
  const critical = system.openCritical > 0 ? `${system.openCritical} ${system.openCritical === 1 ? 'crítico' : 'críticos'}` : 'Sem críticos';
  return `${critical} · ${auditDetail(system, lastScore, now)}`;
}

function auditDetail(system: SystemSummaryResponse, lastScore: number, now: number): string {
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
export function attentionRows(systems: SystemSummaryResponse[], now: number): AttentionRow[] {
  return systems
    .map((system): AttentionRow => ({
      system,
      score: system.lastAuditScore === null ? null : Math.round(system.lastAuditScore),
      reason: attentionReason(system, system.lastAuditScore, now),
    }))
    .sort((a, b) => (a.score ?? -1) - (b.score ?? -1) || a.system.name.localeCompare(b.system.name));
}
