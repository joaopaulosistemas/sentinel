import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

/** Pedidos do Claude que esperam aprovação (atualiza a cada 15 s) e a decisão de aprovar ou recusar. */
export function usePendingActions(systemId: number) {
  const queryClient = useQueryClient();
  const key = ['pending-actions', systemId];
  const list = useQuery({ queryKey: key, queryFn: () => api.getPendingActions(systemId), refetchInterval: 15_000 });
  const decide = useMutation({
    mutationFn: (v: { id: number; decision: 'approve' | 'reject' }) => api.decidePendingAction({ systemId, actionId: v.id, decision: v.decision }),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data);
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
    },
  });
  return { list, decide };
}
