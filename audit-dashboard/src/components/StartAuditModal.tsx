import { InlineNotification, Modal, TextInput } from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';

interface Props {
  system: { id: number; name: string; scheduledBranch?: string | null } | null;
  onClose: () => void;
}

/** Pede o branch, dispara a auditoria e leva para o acompanhamento. */
export function StartAuditModal({ system, onClose }: Props) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [branch, setBranch] = useState('main');

  useEffect(() => {
    if (system) setBranch(system.scheduledBranch || 'main');
  }, [system]);

  const mutation = useMutation({
    mutationFn: (vars: { systemId: number; branch: string }) =>
      api.triggerAudit({ ...vars, triggeredBy: 'dashboard-user' }),
    onSuccess: (run, vars) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      // Sem isto a lista de auditorias só mostrava a nova depois de um refresh.
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      mutation.reset();
      onClose();
      navigate(`/audits/${run.id}`);
      void vars;
    },
  });

  return (
    <Modal
      open={system !== null}
      modalHeading={`Nova auditoria — ${system?.name ?? ''}`}
      primaryButtonText="Iniciar e acompanhar"
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
