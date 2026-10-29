import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

export function useGuide() {
  return useQuery({
    queryKey: ['guide'],
    queryFn: () => api.getGuide(),
    staleTime: 5 * 60_000,
  });
}

/** CLAUDE.md da tecnologia, gerado pela API a partir das regras e das boas práticas. */
export function useGuideClaudeMd(stackId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['guide', 'claude-md', stackId],
    queryFn: () => api.getGuideClaudeMd(stackId),
    enabled: enabled && stackId !== '',
    staleTime: 5 * 60_000,
  });
}
