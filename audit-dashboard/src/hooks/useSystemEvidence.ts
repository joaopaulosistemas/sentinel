import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

/** Evidências que o servidor já tem do sistema e o envio de arquivos novos. */
export function useSystemEvidence(systemId: number, onDelivered: () => void) {
  const queryClient = useQueryClient();
  const server = useQuery({ queryKey: ['system-evidence', systemId], queryFn: () => api.getSystemEvidence(systemId) });
  const deliver = useMutation({
    mutationFn: async (files: File[]) => {
      for (const f of files) await api.putSystemEvidence(systemId, f);
      return files.length;
    },
    onSuccess: () => {
      onDelivered();
      void queryClient.invalidateQueries({ queryKey: ['system-evidence', systemId] });
    },
  });
  return { server, deliver };
}
