import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IAuditRunResponse } from '../types';

/** Confere a pasta local e a branch pedida, sem clonar. */
export const checkLocalPath = (path: string, branch?: string) => api.checkPath(path, branch);

/**
 * Dispara uma auditoria e atualiza as listas que a mostram. `onDone` recebe a auditoria criada e uma função que
 * limpa o estado da mutação; o que fazer depois (fechar, navegar, avisar) é de quem chama.
 */
export function useTriggerAudit(onDone: (run: IAuditRunResponse, reset: () => void) => void) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (vars: { systemId: number; branch: string }) => api.triggerAudit(vars),
    onSuccess: (run) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      // Sem isto a lista de auditorias só mostrava a nova depois de um refresh.
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
      onDone(run, () => mutation.reset());
    },
  });
  return mutation;
}
