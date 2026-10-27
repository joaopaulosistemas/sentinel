import { useQueries } from '@tanstack/react-query';
import { api } from '../api/client';
import type { PendingAction, SystemSummaryResponse } from '../types';
import { useSystems } from './useSystems';

export interface PendingBySystem {
  system: SystemSummaryResponse;
  count: number;
  actions: PendingAction[];
}

/**
 * Pedidos do Claude (via MCP) que esperam decisão de uma pessoa, somados de todos os sistemas.
 * Compartilha a chave com o painel do sistema, então aprovar lá atualiza aqui.
 */
export function usePendingClaude() {
  const { data: systems } = useSystems();
  const list = systems ?? [];
  const results = useQueries({
    queries: list.map((s) => ({
      queryKey: ['pending-actions', s.id],
      queryFn: () => api.getPendingActions(s.id),
      refetchInterval: 60_000,
    })),
  });
  const bySystem: PendingBySystem[] = list
    .map((system, i) => {
      const data = results[i]?.data;
      const actions = Array.isArray(data) ? data.filter((a) => a.status === 'PENDING') : [];
      return { system, count: actions.length, actions };
    })
    .filter((r) => r.count > 0);
  const total = bySystem.reduce((acc, r) => acc + r.count, 0);
  return { total, bySystem };
}
