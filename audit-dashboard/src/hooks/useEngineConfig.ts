import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IEngineTuning } from '../types';

/** Ajuste do motor de um sistema: lê a configuração e grava as sobrescritas (a resposta já é a nova configuração). */
export function useEngineConfig(systemId: number) {
  const queryClient = useQueryClient();
  const key = ['engine', systemId];
  const query = useQuery({ queryKey: key, queryFn: () => api.getEngineConfig(systemId) });
  const save = useMutation({
    mutationFn: (body: IEngineTuning) => api.updateEngineConfig(systemId, body),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data);
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
    },
  });
  return { query, save };
}
