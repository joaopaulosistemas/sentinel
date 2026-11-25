import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IAuditRunResponse, IsoProfile } from '../types';

const POLL_MS = 3000;

const isActive = (run: IAuditRunResponse) => run.status === 'PENDING' || run.status === 'RUNNING';

export function useAuditRuns(systemId: number) {
  return useQuery({
    queryKey: ['auditRuns', systemId],
    queryFn: () => api.getAuditRuns(systemId),
    enabled: systemId > 0,
    // Atualiza sozinho enquanto houver auditoria em andamento.
    refetchInterval: (query) => (query.state.data?.some(isActive) ? POLL_MS : false),
  });
}

/** Últimas auditorias de todos os sistemas; atualiza sozinha enquanto houver alguma em andamento. */
export function useRecentAudits(limit = 8) {
  return useQuery({
    queryKey: ['recentAudits', limit],
    queryFn: () => api.getRecentAudits(limit),
    refetchInterval: (query) => (query.state.data?.some(isActive) ? POLL_MS : false),
  });
}

export function useAudit(id: number) {
  return useQuery({
    queryKey: ['audit', id],
    queryFn: () => api.getAudit(id),
    enabled: id > 0,
    refetchInterval: (query) => (query.state.data && isActive(query.state.data) ? POLL_MS : false),
  });
}

interface IScoreExplanationInput {
  auditId: number;
  enabled: boolean;
  without: string[];
}

export function useScoreExplanation({ auditId, enabled, without }: IScoreExplanationInput) {
  return useQuery({
    queryKey: ['scoreExplanation', auditId, without],
    queryFn: () => api.getScoreExplanation(auditId, without),
    enabled: enabled && auditId > 0,
    placeholderData: (previous) => previous,
  });
}

export function useIsoQuality({ auditId, perfil, enabled }: { auditId: number; perfil: IsoProfile; enabled: boolean }) {
  return useQuery({
    queryKey: ['isoQuality', auditId, perfil],
    queryFn: () => api.getIsoQuality(auditId, perfil),
    enabled: enabled && auditId > 0,
    placeholderData: (previous) => previous,
  });
}

export function useIsoTrend({ systemId, enabled }: { systemId: number | undefined; enabled: boolean }) {
  return useQuery({
    queryKey: ['isoTrend', systemId],
    queryFn: () => api.getIsoTrend(systemId ?? 0),
    enabled: enabled && systemId !== undefined && systemId > 0,
  });
}

interface IFixPlanInput {
  auditId: number;
  enabled: boolean;
}

export function useFixPlan({ auditId, enabled }: IFixPlanInput) {
  return useQuery({
    queryKey: ['fixPlan', auditId],
    queryFn: () => api.getFixPlan(auditId),
    enabled: enabled && auditId > 0,
  });
}

/** Cancela a auditoria e atualiza as listas que a mostram. */
export function useCancelAudit(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.cancelAudit(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['audit', id] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
    },
  });
}
