import type { SystemSummaryResponse } from '../types';

export interface TeamSummary {
  team: string;
  systems: number;
  audited: number;
  avgScore: number | null;
  critical: number;
  high: number;
  worst: SystemSummaryResponse | null;
}

const NO_TEAM = 'Sem time';

/**
 * Ordem de atenção: sistemas nunca auditados primeiro (risco desconhecido), depois por
 * críticos abertos, altos abertos e, por último, score mais baixo.
 */
export function rankSystems(systems: SystemSummaryResponse[]): SystemSummaryResponse[] {
  return [...systems].sort(compareAttention);
}

function compareAttention(a: SystemSummaryResponse, b: SystemSummaryResponse): number {
  return (
    compareNeverAudited(a, b) ||
    compareOpenIssues(a, b) ||
    (a.lastAuditScore ?? 0) - (b.lastAuditScore ?? 0) ||
    a.name.localeCompare(b.name)
  );
}

/** Nunca auditado vem antes: o risco dele é desconhecido. */
function compareNeverAudited(a: SystemSummaryResponse, b: SystemSummaryResponse): number {
  const aNever = a.lastAuditScore == null;
  const bNever = b.lastAuditScore == null;
  if (aNever === bNever) return 0;
  return aNever ? -1 : 1;
}

function compareOpenIssues(a: SystemSummaryResponse, b: SystemSummaryResponse): number {
  return (b.openCritical ?? 0) - (a.openCritical ?? 0) || (b.openHigh ?? 0) - (a.openHigh ?? 0);
}

/** Resume por time. A média considera só sistemas já auditados; times piores aparecem primeiro. */
export function summarizeTeams(systems: SystemSummaryResponse[]): TeamSummary[] {
  const rows = [...groupByTeam(systems)].map(([team, list]) => summarizeTeam(team, list));
  return rows.sort(compareTeams);
}

function groupByTeam(systems: SystemSummaryResponse[]): Map<string, SystemSummaryResponse[]> {
  const groups = new Map<string, SystemSummaryResponse[]>();
  for (const s of systems) {
    const key = s.team?.trim() || NO_TEAM;
    groups.set(key, [...(groups.get(key) ?? []), s]);
  }
  return groups;
}

function summarizeTeam(team: string, list: SystemSummaryResponse[]): TeamSummary {
  const scored = list.filter((s) => s.lastAuditScore != null);
  return {
    team,
    systems: list.length,
    audited: scored.length,
    avgScore: averageScore(scored),
    critical: list.reduce((acc, s) => acc + (s.openCritical ?? 0), 0),
    high: list.reduce((acc, s) => acc + (s.openHigh ?? 0), 0),
    worst: rankSystems(list)[0] ?? null,
  };
}

function averageScore(scored: SystemSummaryResponse[]): number | null {
  if (scored.length === 0) return null;
  return Math.round(scored.reduce((acc, s) => acc + (s.lastAuditScore as number), 0) / scored.length);
}

function compareTeams(a: TeamSummary, b: TeamSummary): number {
  return (
    b.critical - a.critical ||
    b.high - a.high ||
    (a.avgScore ?? -1) - (b.avgScore ?? -1) ||
    a.team.localeCompare(b.team)
  );
}

export interface Trend {
  text: string;
  tone: 'up' | 'down' | 'flat' | 'none';
}

/** Variação da nota desde a auditoria anterior, pronta para exibir (▲ sobe, ▼ cai, — igual, "novo" sem histórico). */
export function formatTrend(delta: number | null | undefined): Trend {
  if (delta === null || delta === undefined) return { text: 'sem histórico', tone: 'none' };
  const rounded = Math.round(delta * 10) / 10;
  if (rounded > 0) return { text: `▲ +${rounded}`, tone: 'up' };
  if (rounded < 0) return { text: `▼ ${rounded}`, tone: 'down' };
  return { text: '— 0', tone: 'flat' };
}
