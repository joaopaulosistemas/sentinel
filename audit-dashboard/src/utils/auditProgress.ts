import type { AnalyzerProgress, AnalyzerProgressStatus, AuditPhase, AuditRunResponse } from '../types';

export const PHASES: Array<{ id: AuditPhase; label: string; hint: string }> = [
  { id: 'FILA', label: 'Fila', hint: 'Aguardando um lugar para rodar' },
  { id: 'CLONE', label: 'Clone', hint: 'Baixando o código do repositório' },
  { id: 'ANALISE', label: 'Análise', hint: 'Rodando as ferramentas de análise' },
  { id: 'PONTUACAO', label: 'Pontuação', hint: 'Calculando a nota' },
  { id: 'RELATORIO', label: 'Relatório', hint: 'Gravando o resultado' },
];

export const analyzerStatusLabel: Record<AnalyzerProgressStatus, string> = {
  ESPERANDO: 'Na fila',
  EXECUTANDO: 'Executando',
  OK: 'Concluído',
  FALHOU: 'Falhou',
  INDISPONIVEL: 'Ferramenta ausente',
  NAO_SUPORTADO: 'Não se aplica',
  SUBSTITUIDO: 'Coberto por outro',
  DESLIGADO: 'Desligado neste sistema',
  SEM_EVIDENCIA: 'Sem evidência',
};

/** Analisadores que realmente rodam (os demais não contam para o andamento). */
const isCounted = (a: AnalyzerProgress) =>
  a.status === 'ESPERANDO' || a.status === 'EXECUTANDO' || a.status === 'OK' || a.status === 'FALHOU';

const isSettled = (a: AnalyzerProgress) => a.status === 'OK' || a.status === 'FALHOU';

/** Posição da etapa atual (0 a 4). Concluída = 5, ou seja, todas as etapas prontas. */
export function phaseIndex(run: Pick<AuditRunResponse, 'status' | 'progress'>): number {
  if (run.status === 'COMPLETED') return PHASES.length;
  if (run.progress) {
    const i = PHASES.findIndex((p) => p.id === run.progress!.phase);
    return i < 0 ? 0 : i;
  }
  return run.status === 'PENDING' ? 0 : 1;
}

export function analyzerCounts(analyzers: AnalyzerProgress[]): { done: number; total: number } {
  const counted = analyzers.filter(isCounted);
  return { done: counted.filter(isSettled).length, total: counted.length };
}

/**
 * Percentual aproximado: a análise é a etapa longa, então ela ocupa a maior faixa e anda com os analisadores
 * que terminam. Nunca chega a 100 antes de a auditoria concluir.
 */
export function progressPercent(run: Pick<AuditRunResponse, 'status' | 'progress'>): number {
  if (run.status === 'COMPLETED') return 100;
  const i = phaseIndex(run);
  switch (i) {
    case 0:
      return 3;
    case 1:
      return 12;
    case 2: {
      const { done, total } = analyzerCounts(run.progress?.analyzers ?? []);
      return total === 0 ? 20 : Math.min(80, 20 + Math.round((60 * done) / total));
    }
    case 3:
      return 88;
    default:
      return 96;
  }
}

/** Duração em segundos, usando só horários do servidor (evita diferença de fuso com o navegador). */
export function elapsedSeconds(run: Pick<AuditRunResponse, 'startedAt' | 'completedAt' | 'progress'>): number | null {
  if (!run.startedAt) return null;
  const start = Date.parse(run.startedAt);
  const lastEvent = run.progress?.events[run.progress.events.length - 1]?.at;
  const end = Date.parse(run.completedAt ?? lastEvent ?? run.startedAt);
  if (Number.isNaN(start) || Number.isNaN(end)) return null;
  return Math.max(0, Math.round((end - start) / 1000));
}

export function formatDuration(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return m > 0 ? `${m} min ${String(s).padStart(2, '0')} s` : `${s} s`;
}

export const isActiveRun = (run: Pick<AuditRunResponse, 'status'>) => run.status === 'PENDING' || run.status === 'RUNNING';

/** Duração média, em segundos, das últimas auditorias concluídas (até 5); nulo se não houver nenhuma. */
export function averageDurationSeconds(runs: Array<Pick<AuditRunResponse, 'status' | 'startedAt' | 'completedAt'>>): number | null {
  const durations = runs
    .filter((r) => r.status === 'COMPLETED' && r.startedAt && r.completedAt)
    .slice(0, 5)
    .map((r) => (Date.parse(r.completedAt as string) - Date.parse(r.startedAt)) / 1000)
    .filter((d) => Number.isFinite(d) && d > 0);
  if (durations.length === 0) return null;
  return Math.round(durations.reduce((a, b) => a + b, 0) / durations.length);
}

/** Quanto falta, pela média das auditorias anteriores; nulo quando não dá para estimar ou já passou da média. */
export function remainingSeconds(elapsed: number | null, average: number | null): number | null {
  if (elapsed === null || average === null) return null;
  const left = average - elapsed;
  return left > 0 ? left : null;
}

/** mm:ss a partir de segundos (o relógio da tela de execução). */
export function clock(totalSeconds: number): string {
  const s = Math.max(0, Math.round(totalSeconds));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/** Segundos entre dois horários do servidor; zero se algum for inválido. */
export function secondsBetween(from: string, to: string): number {
  const a = Date.parse(from);
  const b = Date.parse(to);
  return Number.isNaN(a) || Number.isNaN(b) ? 0 : Math.max(0, Math.round((b - a) / 1000));
}

/** "github.com/org/repo" a partir de uma URL de clone. */
export function shortRepo(url: string | null | undefined): string | null {
  if (!url) return null;
  return url.replace(/^[a-z]+:\/\//i, '').replace(/^[^@/]+@/, '').replace(/\.git$/, '') || null;
}

/** Texto curto de quanto falta, em minutos ou segundos. */
export function etaText(left: number | null): string {
  if (left === null) return 'estimando o tempo restante';
  const s = Math.max(5, Math.round(left / 5) * 5);
  return `faltam cerca de ${s >= 60 ? `${Math.ceil(s / 60)} min` : `${s} s`}`;
}

export interface PhaseView {
  n: number;
  id: AuditPhase;
  name: string;
  sub: string;
  state: 'done' | 'active' | 'pending' | 'failed';
}

/** As cinco fases com o estado e o texto de apoio de cada uma, como no protótipo. */
export function phaseViews(
  run: Pick<AuditRunResponse, 'status' | 'progress'>,
  counts: { done: number; total: number },
): PhaseView[] {
  const index = phaseIndex(run);
  const failed = run.status === 'FAILED' || run.status === 'CANCELLED';
  const repo = shortRepo(run.progress?.repoUrl);
  const subs: Record<AuditPhase, string> = {
    FILA: 'aguardando vaga',
    CLONE: repo ?? 'baixando o código',
    ANALISE: counts.total > 0 ? `${counts.done} de ${counts.total} analisadores` : 'rodando as ferramentas',
    PONTUACAO: 'nota por dimensão',
    RELATORIO: 'insights e plano de ação',
  };
  return PHASES.map((p, i) => {
    const state: PhaseView['state'] =
      i < index ? 'done' : i === index ? (failed ? 'failed' : 'active') : 'pending';
    return {
      n: i + 1,
      id: p.id,
      name: p.label,
      state,
      sub: state === 'done' ? 'concluído' : state === 'pending' ? 'pendente' : state === 'failed' ? 'interrompida' : subs[p.id],
    };
  });
}
