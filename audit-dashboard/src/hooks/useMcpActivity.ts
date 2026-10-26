import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

/**
 * O que o Claude (MCP) está fazendo com um sistema: se a consulta está ligada, as últimas chamadas e os pedidos
 * esperando aprovação. Só leitura: nada aqui muda a auditoria nem a nota. Enquanto a auditoria roda, atualiza sozinho.
 */
export function useMcpActivity(systemId: number | undefined, refresh: boolean) {
  const known = systemId !== undefined;
  const config = useQuery({
    queryKey: ['mcp', systemId],
    queryFn: () => api.getMcp(systemId as number),
    enabled: known,
    refetchInterval: refresh ? 15_000 : false,
  });
  const on = known && config.data?.enabled === true;
  const calls = useQuery({
    queryKey: ['mcpActivity', systemId, 'calls'],
    queryFn: () => api.getMcpCalls(systemId as number, 50),
    enabled: on,
    refetchInterval: refresh ? 5_000 : false,
  });
  const pending = useQuery({
    queryKey: ['mcpActivity', systemId, 'pending'],
    queryFn: () => api.getPendingActions(systemId as number),
    enabled: on,
    refetchInterval: refresh ? 15_000 : false,
  });
  return { config, calls: calls.data, pending: pending.data };
}
