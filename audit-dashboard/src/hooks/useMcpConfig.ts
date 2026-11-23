import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IMcpConfigRequest, IMcpNewKey } from '../types';

/** Últimas chamadas que o Claude fez pelo MCP neste sistema. */
export function useMcpCalls(systemId: number) {
  return useQuery({ queryKey: ['mcp', systemId, 'calls'], queryFn: () => api.getMcpCalls(systemId), refetchOnWindowFocus: true });
}

interface IMcpKeyCallbacks {
  /** Chamado quando a chave nova foi gerada (a chave completa só existe agora). */
  onIssued: (fresh: IMcpNewKey) => void;
  /** Chamado quando a chave foi revogada. */
  onRevoked: () => void;
}

/** Configuração do MCP de um sistema: lê, grava e gera ou revoga a chave. */
export function useMcpConfig(systemId: number, { onIssued, onRevoked }: IMcpKeyCallbacks) {
  const queryClient = useQueryClient();
  const key = ['mcp', systemId];
  const config = useQuery({ queryKey: key, queryFn: () => api.getMcp(systemId) });
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: key });
  };
  const update = useMutation({
    mutationFn: (body: IMcpConfigRequest) => api.updateMcp(systemId, body),
    onSuccess: (data) => queryClient.setQueryData(key, data),
  });
  const issue = useMutation({
    mutationFn: () => api.newMcpKey(systemId),
    onSuccess: (data) => {
      onIssued(data);
      refresh();
    },
  });
  const revoke = useMutation({
    mutationFn: () => api.revokeMcpKey(systemId),
    onSuccess: () => {
      onRevoked();
      refresh();
    },
  });
  return { config, update, issue, revoke };
}
