import type { AuditRunResponse } from '../types';
import { scoreBand, type ScoreBand } from './scoreBand';

/** Quantas auditorias o gráfico mostra: as mais recentes do sistema. */
export const HISTORY_SIZE = 10;

/** O que a coluna representa: nota, falha, cancelamento ou auditoria ainda em andamento. */
export type HistoryKind = 'scored' | 'failed' | 'cancelled' | 'active';

export interface HistoryPoint {
  id: number;
  kind: HistoryKind;
  /** Nota geral arredondada; nula quando a auditoria não gerou nota. */
  score: number | null;
  band: ScoreBand;
  /** Dia e mês, como "30/10". */
  dayLabel: string;
  /** Hora, como "13:20"; vazio quando a data é inválida. */
  timeLabel: string;
  /** Variação da nota em relação à auditoria anterior; nula na primeira. */
  delta: number | null;
  critical: number;
  high: number;
}

const KIND_BY_STATUS: Record<AuditRunResponse['status'], HistoryKind> = {
  COMPLETED: 'scored',
  FAILED: 'failed',
  CANCELLED: 'cancelled',
  PENDING: 'active',
  RUNNING: 'active',
};

const pad = (n: number) => String(n).padStart(2, '0');

function dayLabel(date: Date): string {
  return Number.isNaN(date.getTime()) ? '-' : `${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
}

function timeLabel(date: Date): string {
  return Number.isNaN(date.getTime()) ? '' : `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const startedAt = (run: AuditRunResponse) => {
  const t = Date.parse(run.startedAt);
  return Number.isNaN(t) ? 0 : t;
};

/**
 * As últimas auditorias do sistema, da mais antiga para a mais recente, prontas para o gráfico.
 * A variação vem do servidor (`scoreDelta`); sem ela, compara com a auditoria anterior que gerou nota.
 */
export function buildHistory(runs: AuditRunResponse[], size: number = HISTORY_SIZE): HistoryPoint[] {
  const ordered = [...runs].sort((a, b) => startedAt(a) - startedAt(b) || a.id - b.id);
  let previousScore: number | null = null;
  const points = ordered.map((run): HistoryPoint => {
    const kind = KIND_BY_STATUS[run.status];
    const score = kind === 'scored' && run.riskScore ? Math.round(run.riskScore.overall) : null;
    const computed = score !== null && previousScore !== null ? score - previousScore : null;
    if (score !== null) previousScore = score;
    const when = new Date(run.completedAt ?? run.startedAt);
    return {
      id: run.id,
      kind,
      score,
      band: scoreBand(score),
      dayLabel: dayLabel(when),
      timeLabel: timeLabel(when),
      delta: run.scoreDelta != null ? Math.round(run.scoreDelta) : computed,
      critical: run.findingsSummary?.CRITICAL ?? 0,
      high: run.findingsSummary?.HIGH ?? 0,
    };
  });
  return points.slice(-size);
}

/** Quantas das auditorias com nota estão na meta (nota boa) e quantas têm nota. */
export function historySummary(points: HistoryPoint[]): { onTarget: number; scored: number } {
  const scored = points.filter((p) => p.score !== null);
  return { onTarget: scored.filter((p) => p.band === 'good').length, scored: scored.length };
}
