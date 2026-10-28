import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

const KEY = ['settings'];

export function useSettings(refetchInterval: number | false = false) {
  return useQuery({ queryKey: KEY, queryFn: () => api.getSettings(), refetchInterval, retry: false });
}

export function useSaveSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (values: Record<string, string | null>) => api.saveSettings(values),
    onSuccess: (data) => qc.setQueryData(KEY, data),
  });
}

export function useResetSetting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: string) => api.resetSetting(key),
    onSuccess: (data) => qc.setQueryData(KEY, data),
  });
}

export function useRestartApi() {
  return useMutation({ mutationFn: () => api.restartApi() });
}
