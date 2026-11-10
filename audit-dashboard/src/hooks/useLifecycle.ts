import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IBatchSuppressRequest, ISuppressRequest } from '../types';

export function useTrend(systemId: number) {
  return useQuery({
    queryKey: ['trend', systemId],
    queryFn: () => api.getTrend(systemId),
    enabled: systemId > 0,
  });
}

export function useDiff(auditId: number | undefined) {
  return useQuery({
    queryKey: ['diff', auditId],
    queryFn: () => api.getDiff(auditId as number),
    enabled: !!auditId && auditId > 0,
  });
}

/** Consulta o remoto (lento): uma vez por auditoria, sem repetir em caso de erro. */
export function useFreshness(auditId: number | undefined) {
  return useQuery({
    queryKey: ['freshness', auditId],
    queryFn: () => api.getFreshness(auditId as number),
    enabled: !!auditId && auditId > 0,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function useSuppressions(systemId: number) {
  return useQuery({
    queryKey: ['suppressions', systemId],
    queryFn: () => api.getSuppressions(systemId),
    enabled: systemId > 0,
  });
}

/** Depois de suprimir/reabrir, score, contagens, listas e comparações mudam: invalida tudo que depende disso. */
function useInvalidateLifecycle() {
  const qc = useQueryClient();
  return () => {
    for (const key of ['findings', 'audit', 'auditRuns', 'systems', 'trend', 'diff', 'suppressions', 'ruleQuality']) {
      void qc.invalidateQueries({ queryKey: [key] });
    }
  };
}

export function useSuppressFinding() {
  const invalidate = useInvalidateLifecycle();
  return useMutation({
    mutationFn: ({ findingId, body }: { findingId: number; body: ISuppressRequest }) =>
      api.suppressFinding(findingId, body),
    onSuccess: invalidate,
  });
}

export function useSuppressFindings() {
  const invalidate = useInvalidateLifecycle();
  return useMutation({
    mutationFn: (body: IBatchSuppressRequest) => api.suppressFindings(body),
    onSuccess: invalidate,
  });
}

export function useRemoveSuppression() {
  const invalidate = useInvalidateLifecycle();
  return useMutation({
    mutationFn: ({ systemId, suppressionId }: { systemId: number; suppressionId: number }) =>
      api.removeSuppression(systemId, suppressionId),
    onSuccess: invalidate,
  });
}

export function useInsights(auditId: number | undefined) {
  return useQuery({
    queryKey: ['insights', auditId],
    queryFn: () => api.getInsights(auditId as number),
    enabled: auditId !== undefined,
    staleTime: 60_000,
  });
}

export function usePacks() {
  return useQuery({ queryKey: ['packs'], queryFn: () => api.getPacks(), staleTime: 60_000 });
}
