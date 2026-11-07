import type { IAuditProgress, IMcpCall, IPendingAction } from '../types';

/** A API devolve listas; se vier outra coisa (erro ou resposta inesperada), trata como vazia. */
function asList<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

/** Chamadas do Claude (MCP) feitas desde o início da auditoria, da mais recente para a mais antiga. */
export function callsSince(calls: unknown, startedAt: string | null | undefined): IMcpCall[] {
  const from = startedAt ? Date.parse(startedAt) : Number.NaN;
  return asList<IMcpCall>(calls)
    .filter((c) => Number.isNaN(from) || Date.parse(c.at) >= from)
    .sort((a, b) => Date.parse(b.at) - Date.parse(a.at));
}

/** Chamadas do Claude como linhas do quadro de eventos da auditoria. */
export function mcpCallEvents(calls: unknown, startedAt: string | null | undefined): IAuditProgress['events'] {
  return callsSince(calls, startedAt).map((c) => ({
    at: c.at,
    message: c.ok
      ? `Claude (MCP) chamou ${c.tool}`
      : `Claude (MCP): ${c.tool} recusada${c.detail ? ` (${c.detail})` : ''}`,
  }));
}

/** Eventos da auditoria e do Claude juntos, em ordem de horário. */
export function mergeEvents(base: IAuditProgress['events'], extra: IAuditProgress['events']): IAuditProgress['events'] {
  if (extra.length === 0) return base;
  return [...base, ...extra].sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
}

/** Pedidos do Claude que esperam aprovação de uma pessoa. */
export function pendingCount(actions: unknown): number {
  return asList<IPendingAction>(actions).filter((a) => a.status === 'PENDING').length;
}
