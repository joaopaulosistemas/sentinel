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
  return [...systems].sort((a, b) => {
    const aNever = a.lastAuditScore == null;
    const bNever = b.lastAuditScore == null;
    if (aNever !== bNever) return aNever ? -1 : 1;
    return (
      (b.openCritical ?? 0) - (a.openCritical ?? 0) ||
      (b.openHigh ?? 0) - (a.openHigh ?? 0) ||
      (a.lastAuditScore ?? 0) - (b.lastAuditScore ?? 0) ||
      a.name.localeCompare(b.name)
    );
  });
}

/** Resume por time. A média considera só sistemas já auditados; times piores aparecem primeiro. */
export function summarizeTeams(systems: SystemSummaryResponse[]): TeamSummary[] {
  const groups = new Map<string, SystemSummaryResponse[]>();
  for (const s of systems) {
    const key = s.team?.trim() || NO_TEAM;
    groups.set(key, [...(groups.get(key) ?? []), s]);
  }
  const rows: TeamSummary[] = [];
  for (const [team, list] of groups) {
    const scored = list.filter((s) => s.lastAuditScore != null);
    rows.push({
      team,
      systems: list.length,
      audited: scored.length,
      avgScore:
        scored.length > 0
          ? Math.round(scored.reduce((acc, s) => acc + (s.lastAuditScore as number), 0) / scored.length)
          : null,
      critical: list.reduce((acc, s) => acc + (s.openCritical ?? 0), 0),
      high: list.reduce((acc, s) => acc + (s.openHigh ?? 0), 0),
      worst: rankSystems(list)[0] ?? null,
    });
  }
  return rows.sort(
    (a, b) =>
      b.critical - a.critical ||
      b.high - a.high ||
      (a.avgScore ?? -1) - (b.avgScore ?? -1) ||
      a.team.localeCompare(b.team),
  );
}
