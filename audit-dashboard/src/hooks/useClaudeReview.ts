import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

/** Quanto o Claude acerta: métricas das sugestões e das regras propostas para o sistema. */
export function useReviewMetrics(systemId: number) {
  return useQuery({ queryKey: ['review-metrics', systemId], queryFn: () => api.getReviewMetrics(systemId) });
}

/** Revisão do Claude de um sistema (sugestões e regras propostas) e as ações que a alteram. */
export function useClaudeReview(systemId: number | undefined) {
  const queryClient = useQueryClient();
  const key = ['claude-review', systemId];
  const review = useQuery({ queryKey: key, queryFn: () => api.getClaudeReview(systemId as number), enabled: systemId !== undefined });
  const mutation = useMutation({
    mutationFn: (v: { path: string; body?: unknown }) => api.reviewAction({ systemId: systemId as number, path: v.path, body: v.body }),
    onSuccess: (data) => queryClient.setQueryData(key, data),
  });
  return { review, mutation };
}
