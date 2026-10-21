import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

export function useSystems() {
  return useQuery({
    queryKey: ['systems'],
    queryFn: () => api.getSystems(),
  });
}

export function useSystem(id: number) {
  return useQuery({
    queryKey: ['systems', id],
    queryFn: () => api.getSystem(id),
    enabled: id > 0,
  });
}

export function useRuleQuality() {
  return useQuery({
    queryKey: ['ruleQuality'],
    queryFn: () => api.getRuleQuality(),
  });
}
