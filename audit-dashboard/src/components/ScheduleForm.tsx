import { Button, Checkbox, InlineNotification, NumberInput, TextInput, Tile } from '@carbon/react';
import { useEffect, useState } from 'react';
import { useUpdateSchedule } from '../hooks/useLifecycle';
import type { SystemSummaryResponse } from '../types';

/** Auditoria automática do sistema: periódica e/ou a cada push (exige o webhook configurado no GitHub/GitLab). */
export function ScheduleForm({ system }: { system: SystemSummaryResponse | undefined }) {
  const update = useUpdateSchedule();
  const [hours, setHours] = useState(0);
  const [branch, setBranch] = useState('');
  const [onPush, setOnPush] = useState(false);

  useEffect(() => {
    if (!system) return;
    setHours(system.auditIntervalHours ?? 0);
    setBranch(system.scheduledBranch ?? '');
    setOnPush(!!system.auditOnPush);
  }, [system?.id, system?.auditIntervalHours, system?.scheduledBranch, system?.auditOnPush]);

  if (!system) return null;

  const branchOk = branch === '' || /^[A-Za-z0-9][A-Za-z0-9._/-]*$/.test(branch);

  return (
    <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.25rem' }}>Auditoria automática</h3>
      <p style={{ fontSize: '0.875rem', color: '#525252', marginBottom: '1rem' }}>
        0 horas desliga a auditoria periódica. Por push exige o webhook configurado no repositório.
      </p>
      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <NumberInput
          id="schedule-hours"
          label="A cada (horas)"
          min={0}
          max={720}
          step={1}
          value={hours}
          onChange={(_e: unknown, state: { value: string | number }) => setHours(Number(state.value) || 0)}
        />
        <TextInput
          id="schedule-branch"
          labelText="Branch (vazio = main)"
          value={branch}
          invalid={!branchOk}
          invalidText="Nome de branch inválido"
          onChange={(e) => setBranch(e.target.value)}
        />
        <Checkbox
          id="schedule-push"
          labelText="Auditar a cada push"
          checked={onPush}
          onChange={(_e, { checked }) => setOnPush(checked)}
        />
        <Button
          disabled={!branchOk || update.isPending}
          onClick={() =>
            update.mutate({ systemId: system.id, body: { auditIntervalHours: hours, branch: branch.trim(), auditOnPush: onPush } })
          }
        >
          Salvar
        </Button>
      </div>
      {update.isError && (
        <InlineNotification
          kind="error"
          title="Não foi possível salvar"
          subtitle={update.error instanceof Error ? update.error.message : 'Erro desconhecido'}
          style={{ marginTop: '0.75rem' }}
        />
      )}
      {update.isSuccess && (
        <InlineNotification kind="success" title="Salvo" subtitle="Configuração atualizada." hideCloseButton style={{ marginTop: '0.75rem' }} />
      )}
    </Tile>
  );
}
