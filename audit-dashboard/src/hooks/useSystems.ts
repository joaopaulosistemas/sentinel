import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { IScheduleRequest, ISystemRequest, ISystemSummaryResponse } from '../types';

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

/** Sem resposta do servidor, a origem "pasta local" fica desligada. */
async function fetchLocalSourceEnabled(): Promise<boolean> {
  try {
    return (await api.getLocalSourceEnabled()).enabled;
  } catch {
    return false;
  }
}

/** A origem "pasta local" só aparece quando o servidor a habilitou (SENTINEL_LOCAL_ROOT). */
export function useLocalSourceEnabled(): boolean {
  return (
    useQuery({
      queryKey: ['local-source-enabled'],
      queryFn: fetchLocalSourceEnabled,
      staleTime: 5 * 60_000,
    }).data === true
  );
}

/** Exclui o sistema e todas as suas auditorias (a API remove tudo junto); só depois da confirmação. */
export function useRemoveSystem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (target: ISystemSummaryResponse) => api.deleteSystem(target.id),
    onSuccess: (_data, target) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      void queryClient.invalidateQueries({ queryKey: ['audits'] });
      queryClient.removeQueries({ queryKey: ['systems', target.id] });
    },
  });
}

interface IPersistSystem {
  /** Id do sistema já cadastrado; ausente ao criar. */
  currentId?: number;
  body: ISystemRequest;
  /** Agendamento a gravar depois do cadastro; nulo quando não mudou. */
  schedule: IScheduleRequest | null;
}

/** Cria ou atualiza o sistema e, se o agendamento mudou, grava o agendamento em seguida. */
export async function persistSystem({ currentId, body, schedule }: IPersistSystem): Promise<ISystemSummaryResponse> {
  const system = currentId !== undefined ? await api.updateSystem(currentId, body) : await api.createSystem(body);
  return schedule ? api.updateSchedule(system.id, schedule) : system;
}

interface ISourceCheckInput {
  repoUrl: string;
  branch?: string;
  systemId?: number;
  gitToken: string;
  gitTokenHosts: string;
  localPath: string;
}

/** Confere o endereço (responde? qual a branch padrão?) e a pasta local, sem clonar. */
export function useSourceChecks(read: () => ISourceCheckInput) {
  const check = useMutation({
    mutationFn: () => {
      const v = read();
      return api.checkRepo({
        repoUrl: v.repoUrl,
        branch: v.branch,
        systemId: v.systemId,
        gitToken: v.gitToken,
        gitTokenHosts: v.gitTokenHosts,
      });
    },
  });
  const checkPath = useMutation({
    mutationFn: () => {
      const v = read();
      return api.checkPath(v.localPath, v.branch);
    },
  });
  return { check, checkPath };
}
