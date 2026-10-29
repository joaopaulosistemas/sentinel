import { InlineNotification, Modal, TextInput } from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { AuditRunResponse } from '../types';

interface Props {
  system: { id: number; name: string; scheduledBranch?: string | null; sourceType?: 'GIT_URL' | 'LOCAL_PATH' | null; localPath?: string | null } | null;
  onClose: () => void;
  /** Se informado, a tela continua onde está (a auditoria aparece na lista); sem isto, vai para o acompanhamento. */
  onStarted?: (run: AuditRunResponse) => void;
}

type AuditTarget = NonNullable<Props['system']>;

/** Branch inicial: a agendada (ou main); em pasta local, cai na branch atual da pasta se "main" não existir lá. */
function useInitialBranch(system: Props['system'], setBranch: (b: string) => void): void {
  useEffect(() => {
    if (!system) return;
    setBranch(system.scheduledBranch || 'main');
    if (!isLocalWithPath(system)) return;
    // Pasta local: a branch padrão ("main") pode nem existir lá. Se não existir, usa a branch atual da pasta.
    let ativo = true;
    api
      .checkPath(system.localPath as string, system.scheduledBranch || 'main')
      .then((c) => {
        if (ativo && c.ok && c.branchFound === false && c.defaultBranch) setBranch(c.defaultBranch);
      })
      .catch(() => undefined);
    return () => {
      ativo = false;
    };
  }, [system, setBranch]);
}

function isLocalWithPath(system: AuditTarget): boolean {
  return system.sourceType === 'LOCAL_PATH' && !!system.localPath;
}

/** Pede o branch, dispara a auditoria e leva para o acompanhamento. */
export function StartAuditModal({ system, onClose, onStarted }: Props) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [branch, setBranch] = useState('main');

  useInitialBranch(system, setBranch);

  const mutation = useMutation({
    mutationFn: (vars: { systemId: number; branch: string }) =>
      api.triggerAudit(vars),
    onSuccess: (run, vars) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      // Sem isto a lista de auditorias só mostrava a nova depois de um refresh.
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
      mutation.reset();
      onClose();
      if (onStarted) onStarted(run);
      else navigate(`/audits/${run.id}`);
      void vars;
    },
  });

  return (
    <Modal
      open={system !== null}
      modalHeading={`Nova auditoria — ${system?.name ?? ''}`}
      primaryButtonText={onStarted ? "Iniciar auditoria" : "Iniciar e acompanhar"}
      secondaryButtonText="Cancelar"
      primaryButtonDisabled={mutation.isPending || branch.trim() === ''}
      onRequestClose={() => {
        mutation.reset();
        onClose();
      }}
      onRequestSubmit={() => {
        if (system) mutation.mutate({ systemId: system.id, branch: branch.trim() });
      }}
    >
      <TextInput
        id="branch-input"
        labelText="Branch"
        helperText="Depois de iniciar, você acompanha cada etapa na tela de execução."
        value={branch}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setBranch(e.target.value)}
      />
      {mutation.isError && (
        <InlineNotification
          kind="error"
          title="Erro ao iniciar auditoria"
          subtitle={mutation.error instanceof Error ? mutation.error.message : 'Erro desconhecido'}
          style={{ marginTop: '1rem' }}
          hideCloseButton
        />
      )}
    </Modal>
  );
}
