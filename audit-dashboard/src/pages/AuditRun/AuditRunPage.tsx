import { Button, InlineNotification, Modal, ProgressBar, ProgressIndicator, ProgressStep, SkeletonText, Tag } from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { StartAuditModal } from '../../components/StartAuditModal';
import { useAudit, useAuditRuns } from '../../hooks/useAuditRuns';
import { JourneySteps } from '../../components/JourneySteps';
import { ResultSteps } from '../../components/ResultSteps';
import { useAuditStore } from '../../store/useAuditStore';
import type { AnalyzerProgressStatus } from '../../types';
import { auditStatusConfig } from '../../utils/auditStatus';
import { reportUrl } from '../Reports/reportLinks';
import {
  PHASES,
  analyzerCounts,
  averageDurationSeconds,
  remainingSeconds,
  analyzerStatusLabel,
  elapsedSeconds,
  formatDuration,
  phaseIndex,
  progressPercent,
} from '../../utils/auditProgress';

const BASE_URL = import.meta.env.VITE_API_URL || '';

type TagType = 'warm-gray' | 'blue' | 'green' | 'red' | 'gray' | 'outline';

const statusTag: Record<AnalyzerProgressStatus, TagType> = {
  ESPERANDO: 'warm-gray',
  EXECUTANDO: 'blue',
  OK: 'green',
  FALHOU: 'red',
  INDISPONIVEL: 'gray',
  NAO_SUPORTADO: 'gray',
  SUBSTITUIDO: 'gray',
  SEM_EVIDENCIA: 'gray',
};

/** Painel de acompanhamento: o que a auditoria está fazendo agora e, ao terminar, o resumo do resultado. */
export function AuditRunPage() {
  const { auditId } = useParams<{ auditId: string }>();
  const navigate = useNavigate();
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);
  const id = Number(auditId);
  const { data: run, isLoading, error } = useAudit(id);
  const queryClient = useQueryClient();
  const finished = run?.status === 'COMPLETED' || run?.status === 'FAILED' || run?.status === 'CANCELLED';
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [rerun, setRerun] = useState(false);
  // Histórico do mesmo sistema: base da estimativa de tempo.
  const { data: history } = useAuditRuns(run?.systemId ?? 0);

  const cancel = useMutation({
    mutationFn: () => api.cancelAudit(id),
    onSuccess: () => {
      setConfirmCancel(false);
      void queryClient.invalidateQueries({ queryKey: ['audit', id] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
    },
  });

  // Ao terminar, as listas que mostram nota e situação precisam do dado novo.
  useEffect(() => {
    if (finished) {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
    }
  }, [finished, queryClient]);

  if (isLoading) {
    return (
      <div style={{ padding: '2rem' }}>
        <SkeletonText paragraph lineCount={6} />
      </div>
    );
  }
  if (error || !run) {
    return (
      <InlineNotification
        kind="error"
        title="Auditoria não encontrada"
        subtitle={error instanceof Error ? error.message : 'ID inválido'}
        style={{ margin: '2rem' }}
      />
    );
  }

  const index = phaseIndex(run);
  const percent = progressPercent(run);
  const analyzers = run.progress?.analyzers ?? [];
  const counts = analyzerCounts(analyzers);
  const seconds = elapsedSeconds(run);
  const failed = run.status === 'FAILED';
  const cancelled = run.status === 'CANCELLED';
  const done = run.status === 'COMPLETED';
  const active = !done && !failed && !cancelled;
  const average = averageDurationSeconds((history ?? []).filter((r) => r.id !== run.id));
  const left = active ? remainingSeconds(seconds, average) : null;
  const events = [...(run.progress?.events ?? [])].reverse();
  const currentHint = done
    ? 'Auditoria concluída'
    : failed
      ? 'A auditoria não terminou'
      : cancelled
        ? 'Auditoria cancelada'
        : (PHASES[index]?.hint ?? '');

  const openFindings = () => {
    setSelectedAudit(run.id);
    navigate(`/findings?auditId=${run.id}`);
  };

  return (
    <div style={{ padding: '2rem', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div>
        <JourneySteps current={done ? 3 : 2} />
        <h1 style={{ fontSize: '1.75rem', fontWeight: 400, margin: '0.25rem 0' }}>
          {run.systemName} · Auditoria #{run.id}
        </h1>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <Tag type={auditStatusConfig[run.status].type}>{auditStatusConfig[run.status].label}</Tag>
          <span style={{ color: '#525252' }}>branch {run.branch}</span>
          {seconds !== null && <span style={{ color: '#525252' }}>· {formatDuration(seconds)}</span>}
          {left !== null && (
            <span style={{ color: '#525252' }} title="Estimativa pela média das últimas auditorias deste sistema">
              · faltam cerca de {formatDuration(Math.round(left / 5) * 5 || 5)}
            </span>
          )}
          <span style={{ marginLeft: 'auto', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            {active && (
              <Button kind="danger--tertiary" size="sm" onClick={() => setConfirmCancel(true)}>
                Cancelar auditoria
              </Button>
            )}
            {done && (
              <Button
                kind="tertiary"
                size="sm"
                href={reportUrl(BASE_URL, run.id, 'html')}
                target="_blank"
                rel="noreferrer"
              >
                Baixar relatório
              </Button>
            )}
            {!active && run.systemId !== undefined && (
              <Button size="sm" onClick={() => setRerun(true)}>
                Auditar de novo
              </Button>
            )}
          </span>
        </div>
      </div>

      <ProgressIndicator currentIndex={Math.min(index, PHASES.length - 1)} spaceEqually aria-label="Etapas da auditoria">
        {PHASES.map((p, i) => (
          <ProgressStep
            key={p.id}
            label={p.label}
            complete={done || i < index}
            current={!done && !failed && i === index}
            invalid={failed && i === index}
          />
        ))}
      </ProgressIndicator>

      <ProgressBar
        label={currentHint}
        helperText={
          done || failed
            ? undefined
            : counts.total > 0
              ? `${counts.done} de ${counts.total} analisadores concluídos`
              : undefined
        }
        value={percent}
        max={100}
        status={failed || cancelled ? 'error' : done ? 'finished' : 'active'}
      />

      {failed && (
        <InlineNotification
          kind="error"
          title="A auditoria falhou"
          subtitle={run.errorMessage ?? 'Sem detalhes. Veja os registros da API.'}
          hideCloseButton
        />
      )}

      {cancelled && (
        <InlineNotification
          kind="warning"
          title="Auditoria cancelada"
          subtitle="Nada foi gravado: a nota do sistema continua a da última auditoria concluída."
          hideCloseButton
        />
      )}

      {done && <ResultSteps run={run} onOpenFindings={openFindings} />}

      {active && (
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 3fr) minmax(0, 2fr)', gap: '1.5rem' }}>
          <section aria-label="Analisadores">
            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Analisadores</h2>
            {analyzers.length === 0 ? (
              <p style={{ color: '#525252' }}>Os analisadores aparecem aqui assim que o código for baixado.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: '0.5rem' }}>
                {analyzers.map((a) => (
                  <li
                    key={a.name}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      gap: '1rem',
                      padding: '0.75rem 1rem',
                      background: '#fff',
                      border: '1px solid #e0e0e0',
                    }}
                  >
                    <span style={{ display: 'flex', flexDirection: 'column' }}>
                      <strong style={{ fontWeight: 500 }}>{a.name}</strong>
                      {a.note && <span style={{ fontSize: '0.75rem', color: '#525252' }}>{a.note}</span>}
                    </span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      {a.status === 'OK' && (
                        <span style={{ fontSize: '0.75rem', color: '#525252' }}>
                          {a.findings} {a.findings === 1 ? 'achado' : 'achados'}
                        </span>
                      )}
                      <Tag type={statusTag[a.status]} size="sm">
                        {analyzerStatusLabel[a.status]}
                      </Tag>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-label="Registro de eventos">
            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>O que já aconteceu</h2>
            {events.length === 0 ? (
              <p style={{ color: '#525252' }}>Aguardando o início.</p>
            ) : (
              <ol style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: '0.375rem' }}>
                {events.map((e, i) => (
                  <li key={`${e.at}-${i}`} style={{ fontSize: '0.8125rem' }}>
                    <span style={{ fontFamily: 'monospace', color: '#6f6f6f', marginRight: '0.5rem' }}>
                      {e.at.slice(11, 19)}
                    </span>
                    {e.message}
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      )}

      <Modal
        danger
        open={confirmCancel}
        modalHeading="Cancelar esta auditoria?"
        primaryButtonText="Sim, cancelar"
        secondaryButtonText="Continuar auditando"
        primaryButtonDisabled={cancel.isPending}
        onRequestClose={() => setConfirmCancel(false)}
        onRequestSubmit={() => cancel.mutate()}
      >
        <p>O que já foi analisado será descartado e a nota do sistema não muda. Você pode iniciar outra auditoria depois.</p>
        {cancel.isError && (
          <InlineNotification
            kind="error"
            title="Não foi possível cancelar"
            subtitle={cancel.error instanceof Error ? cancel.error.message : 'Erro desconhecido'}
            hideCloseButton
            style={{ marginTop: '1rem' }}
          />
        )}
      </Modal>
      <StartAuditModal
        system={rerun ? { id: run.systemId ?? 0, name: run.systemName, scheduledBranch: run.branch } : null}
        onClose={() => setRerun(false)}
      />
    </div>
  );
}
