import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IClaudeReview, IMcpCall, IMcpConfig, IPendingAction, ISystemSummaryResponse } from '../types';
import { connectionState, type ConnectionState } from '../utils/mcpSetup';
import { buildDecisions, type DecisionAction, type IDecision } from '../utils/claudeDecisions';
import { usePendingClaude } from './usePendingClaude';
import { useSystems } from './useSystems';

/** De quanto em quanto tempo a área confere o estado dos sistemas (pouco, porque são vários de uma vez). */
const AREA_POLL_MS = 15_000;
const REVIEW_POLL_MS = 60_000;

export interface IClaudeSystemRow {
  system: ISystemSummaryResponse;
  cfg: IMcpConfig | undefined;
  calls: IMcpCall[];
  /** Nulo enquanto a configuração do sistema ainda não chegou. */
  state: ConnectionState | null;
}

/** Cada sistema com a configuração do MCP, as chamadas recentes e o estado da ligação. Mesmas chaves das telas de um sistema só. */
export function useClaudeSystems() {
  const { data: systems, isLoading: loadingSystems } = useSystems();
  const list = systems ?? [];
  const configs = useQueries({
    queries: list.map((s) => ({ queryKey: ['mcp', s.id], queryFn: () => api.getMcp(s.id), refetchInterval: AREA_POLL_MS })),
  });
  const calls = useQueries({
    queries: list.map((s) => ({ queryKey: ['mcp', s.id, 'calls'], queryFn: () => api.getMcpCalls(s.id), refetchInterval: AREA_POLL_MS })),
  });
  const rows: IClaudeSystemRow[] = list.map((system, i) => {
    const cfg = configs[i]?.data;
    const recent = Array.isArray(calls[i]?.data) ? (calls[i].data as IMcpCall[]) : [];
    return { system, cfg, calls: recent, state: cfg ? connectionState(cfg, recent) : null };
  });
  const loading = loadingSystems || configs.some((q) => q.isLoading);
  return { rows, loading };
}

/** Tudo que espera uma decisão de uma pessoa: pedidos do Claude, regras no funil e sugestões abertas. */
export function useDecisions() {
  const { data: systems } = useSystems();
  const list = systems ?? [];
  const pendingBySystem = usePendingClaude().bySystem;
  const reviews = useQueries({
    queries: list.map((s) => ({ queryKey: ['claude-review', s.id], queryFn: () => api.getClaudeReview(s.id), refetchInterval: REVIEW_POLL_MS })),
  });
  const pending: Array<{ system: ISystemSummaryResponse; actions: IPendingAction[] }> = pendingBySystem;
  const reviewList: Array<{ system: ISystemSummaryResponse; review: IClaudeReview | undefined }> = list.map((system, i) => ({
    system,
    review: reviews[i]?.data,
  }));
  const items: IDecision[] = buildDecisions({ pending, reviews: reviewList });
  return { items, total: items.length, loading: reviews.some((q) => q.isLoading) };
}

/** Executa a ação de uma decisão e atualiza o que ela mexe. */
export function useDecide() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (action: DecisionAction): Promise<void> => {
      if (action.type === 'pending') {
        await api.decidePendingAction({ systemId: action.systemId, actionId: action.actionId, decision: action.decision });
      } else {
        await api.reviewAction({ systemId: action.systemId, path: action.path, body: action.body });
      }
    },
    onSuccess: (_data, action) => {
      void queryClient.invalidateQueries({ queryKey: ['pending-actions', action.systemId] });
      void queryClient.invalidateQueries({ queryKey: ['claude-review', action.systemId] });
      void queryClient.invalidateQueries({ queryKey: ['review-metrics', action.systemId] });
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
    },
  });
}
