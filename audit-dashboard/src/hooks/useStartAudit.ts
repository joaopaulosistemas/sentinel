import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

/** Confere a pasta local e a branch pedida, sem clonar. */
export const checkLocalPath = (path: string, branch?: string) => api.checkPath(path, branch);

/**
 * Dispara uma auditoria e atualiza as listas que a mostram. O que fazer depois (fechar, navegar, avisar) é de quem
 * chama: `mutate(vars, { onSuccess })` ou `await mutateAsync(vars)` devolvem a auditoria criada.
 */
export function useTriggerAudit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { systemId: number; branch: string }) => api.triggerAudit(vars),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      // Sem isto a lista de auditorias só mostrava a nova depois de um refresh.
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
    },
  });
}
