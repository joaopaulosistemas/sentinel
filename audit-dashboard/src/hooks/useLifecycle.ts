import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { ScheduleRequest, SuppressRequest } from '../types';

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
      qc.invalidateQueries({ queryKey: [key] });
    }
  };
}

export function useSuppressFinding() {
  const invalidate = useInvalidateLifecycle();
  return useMutation({
    mutationFn: ({ findingId, body }: { findingId: number; body: SuppressRequest }) =>
      api.suppressFinding(findingId, body),
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

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ systemId, profile }: { systemId: number; profile: string }) => api.updateProfile(systemId, profile),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['systems'] });
      qc.invalidateQueries({ queryKey: ['system'] });
    },
  });
}

export function useUpdateSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ systemId, body }: { systemId: number; body: ScheduleRequest }) =>
      api.updateSchedule(systemId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['systems'] });
      qc.invalidateQueries({ queryKey: ['system'] });
    },
  });
}
