import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Category, FindingStatus, Severity } from '../types';

interface FindingsFilter {
  severity?: Severity;
  category?: Category;
  status?: FindingStatus;
  includeSuppressed?: boolean;
  onlyNew?: boolean;
}

export function useFindings(auditId: number, filter?: FindingsFilter) {
  return useQuery({
    queryKey: ['findings', auditId, filter],
    queryFn: () => api.getFindings(auditId, filter),
    enabled: auditId > 0,
  });
}

export interface OverviewFilter extends Omit<FindingsFilter, 'severity'> {
  severity?: Severity[];
  systemId?: number;
  limit: number;
  offset: number;
}

/** Achados da última auditoria concluída de cada sistema, juntos. */
export function useFindingsOverview(filter: OverviewFilter, enabled = true) {
  return useQuery({
    queryKey: ['findingsOverview', filter],
    queryFn: () => api.getFindingsOverview(filter),
    enabled,
    placeholderData: (previous) => previous,
  });
}
