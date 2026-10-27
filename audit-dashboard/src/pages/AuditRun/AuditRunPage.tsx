import { Button, InlineNotification, Modal, SkeletonText } from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { StartAuditModal } from '../../components/StartAuditModal';
import { useAudit, useAuditRuns } from '../../hooks/useAuditRuns';
import { AnalyzerCard, EventLog, PartialResult, PhaseStrip } from '../../components/ExecutionPanels';
import { PageShell, type Crumb } from '../../components/PageShell';
import { McpStatusStrip } from '../../components/McpStatusStrip';
import { useMcpActivity } from '../../hooks/useMcpActivity';
import { callsSince, mcpCallEvents, mergeEvents, pendingCount } from '../../utils/mcpActivity';
import { CheckIcon, CrossIcon, DashIcon, SpinnerIcon } from '../../components/StatusIcons';
import { ResultSteps } from '../../components/ResultSteps';
import { useAuditStore } from '../../store/useAuditStore';
import type { AuditProgress } from '../../types';
import { timeAgo } from '../../utils/timeAgo';
import { reportUrl } from '../Reports/reportLinks';
import {
  PHASES,
  analyzerCounts,
  averageDurationSeconds,
  remainingSeconds,
  clock,
  elapsedSeconds,
  etaText,
  formatDuration,
  phaseIndex,
  phaseViews,
  progressPercent,
} from '../../utils/auditProgress';

const BASE_URL = import.meta.env.VITE_API_URL || '';

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
  const lastProgress = useRef<AuditProgress | null>(null);
  // Histórico do mesmo sistema: base da estimativa de tempo.
  const { data: history } = useAuditRuns(run?.systemId ?? 0);
  // O que o Claude (MCP) fez com este sistema desde que a auditoria começou. Só informação: não entra na nota.
  const mcp = useMcpActivity(run?.systemId, run !== undefined && !finished);

  const cancel = useMutation({
    mutationFn: () => api.cancelAudit(id),
    onSuccess: () => {
      setConfirmCancel(false);
      void queryClient.invalidateQueries({ queryKey: ['audit', id] });
      void queryClient.invalidateQueries({ queryKey: ['recentAudits'] });
    },
  });

  if (run?.progress) lastProgress.current = run.progress;

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
  // O servidor só devolve o andamento enquanto a auditoria roda; ao concluir, guarda-se o último visto.
  const progress = run.progress ?? lastProgress.current;
  const analyzers = progress?.analyzers ?? [];
  const mcpEnabled = typeof mcp.config.data?.enabled === 'boolean' ? mcp.config.data.enabled : null;
  const mcpCalls = mcpEnabled ? callsSince(mcp.calls, run.startedAt) : [];
  const events = mergeEvents(progress?.events ?? [], mcpEnabled ? mcpCallEvents(mcp.calls, run.startedAt) : []);
  const counts = analyzerCounts(analyzers);
  const seconds = elapsedSeconds(run);
  const failed = run.status === 'FAILED';
  const cancelled = run.status === 'CANCELLED';
  const done = run.status === 'COMPLETED';
  const runCrumbs: Crumb[] = [
    { label: 'Início', to: '/' },
    { label: 'Sistemas', to: '/systems' },
    { label: run.systemName, to: run.systemId !== undefined ? `/systems/${run.systemId}` : undefined },
    { label: `Auditoria #${run.id}` },
  ];
  const active = !done && !failed && !cancelled;
  const average = averageDurationSeconds((history ?? []).filter((r) => r.id !== run.id));
  const left = active ? remainingSeconds(seconds, average) : null;
  const phases = phaseViews({ status: run.status, progress }, counts);
  const stepText = done
    ? 'Concluída'
    : failed
      ? 'A auditoria falhou'
      : cancelled
        ? 'Auditoria cancelada'
        : index === 2 && counts.total > 0
          ? `Análise · ${counts.done} de ${counts.total} analisadores concluídos`
          : (PHASES[index]?.label ?? '');
  const barColor = failed ? '#da1e28' : cancelled ? '#8d8d8d' : done ? '#198038' : '#0f62fe';
  const started = run.startedAt ? new Date(run.startedAt) : null;
  const startedAt = started && !Number.isNaN(started.getTime())
    ? started.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    : null;
  const pill = active
    ? { bg: '#d0e2ff', fg: '#0043ce', label: 'Em execução', icon: <SpinnerIcon size={12} color="#0043ce" /> }
    : done
      ? { bg: '#a7f0ba', fg: '#044317', label: 'Concluída', icon: <CheckIcon size={12} color="#044317" /> }
      : failed
        ? { bg: '#ffd7d9', fg: '#750e13', label: 'Falhou', icon: <CrossIcon size={12} color="#750e13" /> }
        : { bg: 'var(--s-border)', fg: 'var(--s-text)', label: 'Cancelada', icon: <DashIcon size={12} color="var(--s-text)" /> };

  const mono = "'IBM Plex Mono', ui-monospace, monospace";
  const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)' } as const;
  const rerunModal = (
    <StartAuditModal
      system={rerun ? { id: run.systemId ?? 0, name: run.systemName, scheduledBranch: run.branch } : null}
      onClose={() => setRerun(false)}
    />
  );

  const openFindings = () => {
    setSelectedAudit(run.id);
    navigate(`/findings?auditId=${run.id}`);
  };


  if (done) {
    const sha = run.commitSha ? run.commitSha.slice(0, 7) : null;
    return (
      <PageShell crumbs={runCrumbs}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>{run.systemName} · resultado da auditoria #{run.id}</h1>
            <span style={{ color: 'var(--s-text-3)' }}>
              Branch <span style={{ fontFamily: mono }}>{run.branch}</span>
              {sha && <> · commit {sha}</>}
              {' · '}concluída {timeAgo(run.completedAt)}
              {seconds !== null && <> · {formatDuration(seconds)}</>}
            </span>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button kind="tertiary" href={reportUrl(BASE_URL, run.id, 'html')} target="_blank" rel="noreferrer">
              Baixar relatório
            </Button>
            {run.systemId !== undefined && <Button onClick={() => setRerun(true)}>Auditar de novo</Button>}
          </div>
        </div>
        <ResultSteps run={run} onOpenFindings={openFindings} />
        {rerunModal}
      </PageShell>
    );
  }

  return (
    <PageShell crumbs={runCrumbs}>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>{run.systemName}</h1>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: pill.bg, color: pill.fg, padding: '3px 10px', borderRadius: 12, fontSize: 12, fontWeight: 500 }}>
              {pill.icon}
              {pill.label}
            </span>
          </div>
          <div style={{ color: 'var(--s-text-3)', display: 'flex', gap: 20, flexWrap: 'wrap', fontSize: 13 }}>
            <span>Branch <span style={{ fontFamily: mono }}>{run.branch}</span></span>
            {run.triggeredBy && (
              <span>Iniciada por <strong style={{ fontWeight: 500, color: 'var(--s-text)' }}>{run.triggeredBy}</strong>{startedAt ? ` às ${startedAt}` : ''}</span>
            )}
            {run.profile && <span>Perfil <span style={{ fontFamily: mono }}>{run.profile}</span></span>}
            {run.commitSha && <span>Commit <span style={{ fontFamily: mono }}>{run.commitSha.slice(0, 7)}</span></span>}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {active && (
            <Button kind="danger--tertiary" onClick={() => setConfirmCancel(true)}>
              Cancelar auditoria
            </Button>
          )}
          {!active && run.systemId !== undefined && (
            <Button onClick={() => setRerun(true)}>
              Auditar de novo
            </Button>
          )}
        </div>
      </div>

      <section aria-label="Andamento" style={{ ...box, padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
        <PhaseStrip phases={phases} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8, fontSize: 13 }}>
            <span><strong style={{ fontWeight: 600 }}>{percent}%</strong> &nbsp;·&nbsp; {stepText}</span>
            <span style={{ color: 'var(--s-text-3)' }}>
              {seconds !== null && <>Decorrido <span style={{ fontFamily: mono, color: 'var(--s-text)' }}>{clock(seconds)}</span></>}
              {active && <> &nbsp;·&nbsp; <span title="Estimativa pela média das últimas auditorias deste sistema">{etaText(left)}</span></>}
              {done && <> &nbsp;·&nbsp; finalizada</>}
            </span>
          </div>
          <div role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-label="Progresso da auditoria" style={{ height: 8, background: 'var(--s-border)' }}>
            <div style={{ height: 8, background: barColor, width: `${percent}%`, transition: 'width 300ms ease' }} />
          </div>
        </div>
      </section>

      {active && run.systemId !== undefined && (
        <McpStatusStrip systemId={run.systemId} enabled={mcpEnabled} calls={mcpCalls} pending={pendingCount(mcp.pending)} />
      )}

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

      {active && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, alignItems: 'flex-start' }}>
          <section aria-label="Analisadores" style={{ flex: '999 1 640px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>
              Analisadores
              {counts.total > 0 && (
                <span style={{ fontWeight: 400, color: 'var(--s-text-3)' }}> · {counts.done} de {counts.total} concluídos{active ? ', rodando em paralelo' : ''}</span>
              )}
            </h2>
            {analyzers.length === 0 ? (
              <p style={{ color: 'var(--s-text-3)', margin: 0 }}>
                {active ? 'Os analisadores aparecem aqui assim que o código for baixado.' : 'O detalhe por analisador só fica disponível enquanto a auditoria roda.'}
              </p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(230px, 100%), 1fr))', gap: 12 }}>
                {analyzers.map((a) => <AnalyzerCard key={a.name} a={a} />)}
              </ul>
            )}
          </section>

          <aside aria-label="Resultado e eventos" style={{ flex: '1 1 340px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 20 }}>
            <PartialResult run={run} progress={progress} />
            <EventLog startedAt={run.startedAt} events={events} />
          </aside>
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
      {rerunModal}
    </PageShell>
  );
}
