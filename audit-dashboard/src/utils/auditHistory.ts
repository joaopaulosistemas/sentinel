import type { IAuditRunResponse } from '../types';
import { scoreBand, type ScoreBand } from './scoreBand';

/** Quantas auditorias o gráfico mostra: as mais recentes do sistema. */
const HISTORY_SIZE = 10;

/** O que a coluna representa: nota, falha, cancelamento ou auditoria ainda em andamento. */
type HistoryKind = 'scored' | 'failed' | 'cancelled' | 'active';

export interface IHistoryPoint {
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

const KIND_BY_STATUS: Record<IAuditRunResponse['status'], HistoryKind> = {
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

const startedAt = (run: IAuditRunResponse) => {
  const parsed = Date.parse(run.startedAt);
  return Number.isNaN(parsed) ? 0 : parsed;
};

/**
 * As últimas auditorias do sistema, da mais antiga para a mais recente, prontas para o gráfico.
 * A variação vem do servidor (`scoreDelta`); sem ela, compara com a auditoria anterior que gerou nota.
 */
export function buildHistory(runs: IAuditRunResponse[], size: number = HISTORY_SIZE): IHistoryPoint[] {
  const ordered = [...runs].sort((a, b) => startedAt(a) - startedAt(b) || a.id - b.id);
  let previousScore: number | null = null;
  const points = ordered.map((run): IHistoryPoint => {
    const point = toPoint(run, previousScore);
    if (point.score !== null) previousScore = point.score;
    return point;
  });
  return points.slice(-size);
}

/** Nota arredondada da auditoria, ou `null` quando ela não gerou nota. */
function scoreOf(run: IAuditRunResponse, kind: IHistoryPoint['kind']): number | null {
  return kind === 'scored' && run.riskScore ? Math.round(run.riskScore.overall) : null;
}

/** Variação da nota: a do servidor quando existe; senão, a diferença para a última auditoria que gerou nota. */
function deltaOf({ run, score, previousScore }: { run: IAuditRunResponse; score: number | null; previousScore: number | null }): number | null {
  if (run.scoreDelta != null) return Math.round(run.scoreDelta);
  return score !== null && previousScore !== null ? score - previousScore : null;
}

function toPoint(run: IAuditRunResponse, previousScore: number | null): IHistoryPoint {
  const kind = KIND_BY_STATUS[run.status];
  const score = scoreOf(run, kind);
  const when = new Date(run.completedAt ?? run.startedAt);
  return {
    id: run.id,
    kind,
    score,
    band: scoreBand(score),
    dayLabel: dayLabel(when),
    timeLabel: timeLabel(when),
    delta: deltaOf({ run, score, previousScore }),
    critical: run.findingsSummary?.CRITICAL ?? 0,
    high: run.findingsSummary?.HIGH ?? 0,
  };
}

/** Quantas das auditorias com nota estão na meta (nota boa) e quantas têm nota. */
export function historySummary(points: IHistoryPoint[]): { onTarget: number; scored: number } {
  const scored = points.filter((p) => p.score !== null);
  return { onTarget: scored.filter((p) => p.band === 'good').length, scored: scored.length };
}
