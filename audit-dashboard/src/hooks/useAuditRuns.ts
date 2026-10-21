import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AuditRunResponse } from '../types';

const POLL_MS = 3000;

const isActive = (run: AuditRunResponse) => run.status === 'PENDING' || run.status === 'RUNNING';

export function useAuditRuns(systemId: number) {
  return useQuery({
    queryKey: ['auditRuns', systemId],
    queryFn: () => api.getAuditRuns(systemId),
    enabled: systemId > 0,
    // Atualiza sozinho enquanto houver auditoria em andamento.
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
