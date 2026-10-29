import { InlineNotification, SkeletonText } from '@carbon/react';
import { PageShell } from '../../components/PageShell';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { StartAuditModal } from '../../components/StartAuditModal';
import { DiffSummary } from '../../components/DiffSummary';
import { InsightsLimits, InsightsPanel } from '../../components/InsightsPanel';
import { PageSection, SectionIndex, type IndexItem } from '../../components/PageSection';
import { CheckIcon, CrossIcon, DashIcon, SpinnerIcon } from '../../components/StatusIcons';
import { SuppressionsList } from '../../components/SuppressionsList';
import { TrendChart } from '../../components/TrendChart';
import { useAuditRuns } from '../../hooks/useAuditRuns';
import { useInsights, useSuppressions } from '../../hooks/useLifecycle';
import { useSystem } from '../../hooks/useSystems';
import type { AuditRunResponse, Severity, Stack, SystemSummaryResponse } from '../../types';
import { analyzerCounts, PHASES, phaseIndex, progressPercent } from '../../utils/auditProgress';
import { TARGET_SCORE, scoreColor as bandColor } from '../../utils/scoreBand';
import { scoreColor } from '../../utils/systemAttention';
import { startLabel } from '../../utils/timeAgo';

const stackLabels: Record<Stack, string> = {
  JAVA: 'Java',
  JAVASCRIPT: 'JavaScript / TypeScript',
  DOTNET: '.NET',
  PYTHON: 'Python',
  COBOL: 'COBOL',
  MIXED: 'Misto',
};

const dimensionLabels: Array<{ key: keyof NonNullable<AuditRunResponse['riskScore']>; label: string }> = [
  { key: 'security', label: 'Segurança' },
  { key: 'quality', label: 'Qualidade' },
  { key: 'architecture', label: 'Arquitetura' },
  { key: 'dependencies', label: 'Dependências' },
  { key: 'observability', label: 'Observabilidade' },
  { key: 'performance', label: 'Desempenho' },
];

const severityOrder: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const severityColors: Record<Severity, string> = {
  CRITICAL: '#a2191f',
  HIGH: '#ff832b',
  MEDIUM: '#f1c21b',
  LOW: '#c6c6c6',
  INFO: '#a6c8ff',
};
const severityNames: Record<Severity, string> = {
  CRITICAL: 'críticos',
  HIGH: 'altos',
  MEDIUM: 'médios',
  LOW: 'baixos',
  INFO: 'informativos',
};

/** Etapa atual de uma auditoria em andamento, com a contagem de analisadores durante a análise. */
function stepLabel(run: AuditRunResponse): string {
  const base = PHASES[Math.min(phaseIndex(run), PHASES.length - 1)].label;
  if (run.progress?.phase === 'ANALISE') {
    const { done, total } = analyzerCounts(run.progress.analyzers);
    if (total > 0) return `${base} ${done}/${total}`;
  }
  return base;
}

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const cell = { padding: '12px 8px' } as const;

function truncated(message: string): string {
  return message.length > 60 ? `${message.slice(0, 60)}…` : message;
}

/** Estado da execução na tabela: andamento com barra, concluída, falhou ou cancelada. */
function RunStatus({ run, active, pct }: { run: AuditRunResponse; active: boolean; pct: number }) {
  if (active) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-link)', fontWeight: 500 }}>
          <SpinnerIcon size={14} color="#0043ce" />
          Em execução · {stepLabel(run)}
        </span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ flex: 1, height: 6, background: 'var(--s-border)' }}>
            <span style={{ display: 'block', height: 6, background: 'var(--s-primary)', width: `${pct}%` }} />
          </span>
          <span style={{ fontSize: 12, color: 'var(--s-text-3)', fontFamily: mono, width: 36 }}>{pct}%</span>
        </span>
      </div>
    );
  }
  if (run.status === 'COMPLETED') {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-success-strong)', fontWeight: 500 }}>
        <CheckIcon size={14} />
        Concluída
      </span>
    );
  }
  if (run.status === 'FAILED') {
    return (
      <span title={run.errorMessage ?? undefined} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-danger-text)', fontWeight: 500 }}>
        <CrossIcon size={14} />
        Falhou{run.errorMessage ? ` · ${truncated(run.errorMessage)}` : ''}
      </span>
    );
  }
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-text-3)', fontWeight: 500 }}>
      <DashIcon size={14} />
      Cancelada
    </span>
  );
}

/** "252 (4 novos, 9 resolvidos)"; sem comparação mostra só o total. */
function findingsText(run: AuditRunResponse): string {
  if (run.status === 'PENDING' || run.status === 'RUNNING') return 'calculando';
  if (run.status !== 'COMPLETED' || run.totalFindings === null || run.totalFindings === undefined) return '-';
  if (run.newFindings !== null && run.newFindings !== undefined && run.resolvedFindings !== null && run.resolvedFindings !== undefined) {
    return `${run.totalFindings} (${run.newFindings} ${run.newFindings === 1 ? 'novo' : 'novos'}, ${run.resolvedFindings} ${run.resolvedFindings === 1 ? 'resolvido' : 'resolvidos'})`;
  }
  return String(run.totalFindings);
}

const isActive = (run: AuditRunResponse) => run.status === 'PENDING' || run.status === 'RUNNING';

function StartedBanner({ started, onClose }: { started: AuditRunResponse; onClose: () => void }) {
  return (
    <div role="status" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: '4px solid var(--s-link)', padding: '12px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
      <span>
        <strong style={{ fontWeight: 600 }}>Auditoria #{started.id} iniciada</strong> na branch <span style={{ fontFamily: mono }}>{started.branch}</span>. Ela já aparece na lista abaixo e se atualiza sozinha.
      </span>
      <span style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <Link to={`/audits/${started.id}`} style={{ fontWeight: 500 }}>Acompanhar execução</Link>
        <button type="button" aria-label="Fechar aviso" onClick={onClose} style={{ background: 'transparent', border: 0, cursor: 'pointer', padding: 4 }}>
          <DashIcon size={14} color="var(--s-text-3)" />
        </button>
      </span>
    </div>
  );
}

function SystemHeader({ system, onEdit, onNewAudit }: { system: SystemSummaryResponse; onEdit: () => void; onNewAudit: () => void }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 16 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>{system.name}</h1>
        <span style={{ color: 'var(--s-text-3)' }}>
          {[stackLabels[system.stack], system.team, `perfil ${system.profile || 'default'}`].filter(Boolean).join(' · ')}
        </span>
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button type="button" onClick={onEdit} style={{ height: 40, padding: '0 16px', background: 'var(--s-surface)', color: 'var(--s-text)', border: '1px solid var(--s-border-strong)', fontSize: 14, cursor: 'pointer' }}>
          Editar sistema
        </button>
        <button type="button" onClick={onNewAudit} style={{ height: 40, padding: '0 20px', background: 'var(--s-primary)', color: '#fff', border: 0, fontSize: 14, cursor: 'pointer' }}>
          Nova auditoria
        </button>
      </div>
    </div>
  );
}

function RunRow({ run }: { run: AuditRunResponse }) {
  const active = isActive(run);
  const score = run.riskScore ? Math.round(run.riskScore.overall) : null;
  return (
    <tr style={{ borderBottom: '1px solid var(--s-border)', background: active ? 'var(--s-selected)' : 'transparent' }}>
      <td style={{ padding: '14px 16px 14px 0' }}>
        <Link to={`/audits/${run.id}`} style={{ fontWeight: 600, textDecoration: 'none' }}>#{run.id}</Link>
      </td>
      <td style={{ ...cell, padding: '14px 16px 14px 0', fontFamily: mono, fontSize: 13 }}>{run.branch}</td>
      <td style={{ ...cell, padding: '14px 16px 14px 0', minWidth: 240 }}>
        <RunStatus run={run} active={active} pct={progressPercent(run)} />
      </td>
      <td style={{ ...cell, padding: '14px 16px 14px 0', fontSize: 18, fontWeight: 600, color: scoreColor(score) }}>{score ?? '-'}</td>
      <td style={{ ...cell, padding: '14px 16px 14px 0', color: 'var(--s-text-2)' }}>{findingsText(run)}</td>
      <td style={{ padding: '14px 0', textAlign: 'right', color: 'var(--s-text-3)', fontSize: 13 }}>{startLabel(run.startedAt)}</td>
    </tr>
  );
}

function RunsBody({ loading, runs }: { loading: boolean; runs: AuditRunResponse[] }) {
  if (loading) return <SkeletonText lineCount={5} />;
  if (runs.length === 0) return <p style={{ color: 'var(--s-text-3)', margin: 0 }}>Nenhuma auditoria encontrada.</p>;
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 720 }}>
        <thead>
          <tr style={{ textAlign: 'left', fontSize: 12, fontWeight: 600, color: 'var(--s-text-3)', borderBottom: '1px solid var(--s-border)' }}>
            <th style={{ padding: '8px 16px 8px 0' }}>Auditoria</th>
            <th style={{ padding: '8px 16px 8px 0' }}>Branch</th>
            <th style={{ padding: '8px 16px 8px 0' }}>Situação</th>
            <th style={{ padding: '8px 16px 8px 0' }}>Nota</th>
            <th style={{ padding: '8px 16px 8px 0' }}>Achados</th>
            <th style={{ padding: '8px 0', textAlign: 'right' }}>Início</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <RunRow key={run.id} run={run} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RunsSection({ loading, runs, live }: { loading: boolean; runs: AuditRunResponse[]; live: boolean }) {
  return (
    <PageSection
      id="auditorias"
      title="Últimas auditorias"
      aside={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--s-text-3)' }}>
          <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: 4, background: live ? '#0f62fe' : '#198038' }} />
          {live ? 'Atualizando a cada 3 s enquanto houver auditoria em execução' : 'Atualizado agora'}
        </span>
      }
    >
      <RunsBody loading={loading} runs={runs} />
    </PageSection>
  );
}

function scoreCaption(shown: number | null): string {
  if (shown === null) return 'Sem auditoria concluída';
  const missing = Math.max(0, TARGET_SCORE - shown);
  if (missing === 0) return `Dentro da meta de ${TARGET_SCORE}`;
  return `Meta ${TARGET_SCORE} · faltam ${missing} ${missing === 1 ? 'ponto' : 'pontos'}`;
}

function ScoreHeadline({ shown }: { shown: number | null }) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ fontSize: 88, fontWeight: 300, lineHeight: 1, color: bandColor(shown) }}>{shown ?? '—'}</span>
        {shown !== null && <span style={{ color: 'var(--s-text-3)' }}>de 100</span>}
      </div>
      <div style={{ color: 'var(--s-text-2)', marginTop: 4 }}>{scoreCaption(shown)}</div>
    </div>
  );
}

function DimensionBar({ label, n }: { label: string; n: number | null }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
        <span>{label}</span>
        <strong style={{ color: bandColor(n) }}>{n ?? '—'}</strong>
      </div>
      <div style={{ position: 'relative', height: 8, borderRadius: 4, background: 'var(--s-border)', marginTop: 4 }}>
        <span style={{ position: 'absolute', inset: '0 auto 0 0', width: `${Math.max(0, Math.min(100, n ?? 0))}%`, borderRadius: 4, background: bandColor(n) }} />
        <span aria-hidden="true" title={`Meta ${TARGET_SCORE}`} style={{ position: 'absolute', left: `${TARGET_SCORE}%`, top: -3, bottom: -3, width: 2, background: 'var(--s-text)' }} />
      </div>
    </div>
  );
}

function DimensionBars({ riskScore }: { riskScore: AuditRunResponse['riskScore'] | undefined }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(220px, 100%), 1fr))', gap: '14px 40px' }}>
      {dimensionLabels.map(({ key, label }) => {
        const value = riskScore?.[key];
        return <DimensionBar key={key} label={label} n={typeof value === 'number' ? Math.round(value) : null} />;
      })}
    </div>
  );
}

/** Nota geral, as seis dimensões (com a meta marcada) e os achados por gravidade da última auditoria concluída. */
function ScoreOverview({ latest, fallbackScore }: { latest: AuditRunResponse | undefined; fallbackScore: number | null }) {
  const overall = latest?.riskScore?.overall ?? fallbackScore;
  const shown = overall === null || overall === undefined ? null : Math.round(overall);
  const total = severityOrder.reduce((acc, sev) => acc + (latest?.findingsSummary[sev] ?? 0), 0);
  return (
    <PageSection
      id="visao"
      first
      title="Visão geral"
      aside={latest && <span style={{ fontSize: 14, color: 'var(--s-text-3)' }}>última concluída: #{latest.id}, {startLabel(latest.startedAt)}</span>}
    >
      <div aria-label="Última auditoria concluída" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(280px, 100%), 1fr))', gap: '24px 48px', alignItems: 'center' }}>
        <ScoreHeadline shown={shown} />
        <DimensionBars riskScore={latest?.riskScore} />
      </div>
      {latest && <SeverityBar run={latest} total={total} />}
    </PageSection>
  );
}

/** Achados da última auditoria concluída, divididos por gravidade. */
function SeverityBar({ run, total }: { run: AuditRunResponse; total: number }) {
  return (
    <div aria-label="Resumo de achados por gravidade" style={{ marginTop: 28 }}>
      <div style={{ fontSize: 13, color: 'var(--s-text-2)', marginBottom: 6 }}>{total} {total === 1 ? 'achado' : 'achados'} por gravidade</div>
      {total > 0 && (
        <div style={{ display: 'flex', height: 12, borderRadius: 6, overflow: 'hidden', background: 'var(--s-border)' }}>
          {severityOrder.map((sev) => {
            const count = run.findingsSummary[sev] ?? 0;
            return count > 0 ? <span key={sev} style={{ display: 'block', width: `${(count / total) * 100}%`, background: severityColors[sev] }} /> : null;
          })}
        </div>
      )}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 28px', marginTop: 12 }}>
        {severityOrder.map((sev) => (
          <span key={sev} style={{ fontSize: 13 }}>
            <span aria-hidden="true" style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: severityColors[sev], marginRight: 6 }} />
            <strong style={{ fontWeight: 600 }}>{run.findingsSummary[sev] ?? 0}</strong> {severityNames[sev]}
          </span>
        ))}
      </div>
    </div>
  );
}

function newestCompleted(runs: AuditRunResponse[] | undefined): AuditRunResponse | undefined {
  return runs?.filter((r) => r.status === 'COMPLETED').sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())[0];
}

function buildIndex(runCount: number, hasCompleted: boolean, debtCount?: number, suppressedCount = 0): IndexItem[] {
  const hasInsights = debtCount !== undefined;
  return [
    { id: 'visao', label: 'Visão geral' },
    { id: 'auditorias', label: 'Auditorias', badge: String(runCount) },
    ...(hasCompleted ? [{ id: 'mudou', label: 'O que mudou' }, { id: 'parecer', label: 'Parecer' }] : []),
    ...(hasInsights ? [{ id: 'plano', label: 'O que corrigir', badge: String(debtCount) }] : []),
    { id: 'tendencia', label: 'Evolução da nota' },
    ...(hasInsights ? [{ id: 'limites', label: 'Limites e mapa' }] : []),
    ...(suppressedCount > 0 ? [{ id: 'suprimidos', label: 'Suprimidos', badge: String(suppressedCount) }] : []),
  ];
}

function SystemNotFound({ error }: { error: unknown }) {
  return (
    <InlineNotification
      kind="error"
      title="Sistema não encontrado"
      subtitle={error instanceof Error ? error.message : 'ID inválido'}
      style={{ margin: '2rem' }}
    />
  );
}

interface DetailSectionsProps {
  systemId: number;
  latest: AuditRunResponse | undefined;
  fallbackScore: number | null;
  loadingRuns: boolean;
  runs: AuditRunResponse[];
  live: boolean;
}

function DetailSections({ systemId, latest, fallbackScore, loadingRuns, runs, live }: DetailSectionsProps) {
  return (
    <div className="sentinel-secoes">
      <ScoreOverview latest={latest} fallbackScore={fallbackScore} />
      <RunsSection loading={loadingRuns} runs={runs} live={live} />
      {latest && <DiffSummary auditId={latest.id} />}
      <InsightsPanel auditId={latest?.id} />
      <TrendChart systemId={systemId} />
      <InsightsLimits auditId={latest?.id} />
      <SuppressionsList systemId={systemId} />
    </div>
  );
}

export function SystemDetailPage() {
  const { systemId } = useParams<{ systemId: string }>();
  const navigate = useNavigate();

  const id = Number(systemId);
  const [starting, setStarting] = useState(false);
  const [started, setStarted] = useState<AuditRunResponse | null>(null);
  const { data: system, isLoading: loadingSystem, error: systemError } = useSystem(id);
  const { data: auditRuns, isLoading: loadingRuns } = useAuditRuns(id);
  // A auditoria concluída mais recente dá as notas e o parecer.
  const latestCompleted = newestCompleted(auditRuns);
  const insights = useInsights(latestCompleted?.id);
  const suppressions = useSuppressions(id);

  if (loadingSystem) return (
    <div style={{ padding: '2rem' }}>
      <SkeletonText paragraph lineCount={8} />
    </div>
  );
  if (systemError || !system) return <SystemNotFound error={systemError} />;

  const runs = auditRuns?.slice(0, 8) ?? [];
  const index = buildIndex(runs.length, !!latestCompleted, insights.data?.debtTable.length, suppressions.data?.length);

  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Sistemas', to: '/systems' }, { label: system.name }]}>
      {started && <StartedBanner started={started} onClose={() => setStarted(null)} />}
      <SystemHeader system={system} onEdit={() => navigate(`/systems?edit=${system.id}`)} onNewAudit={() => setStarting(true)} />
      <StartAuditModal system={starting ? system : null} onClose={() => setStarting(false)} onStarted={setStarted} />
      <div className="sentinel-com-indice">
        <SectionIndex items={index} />
        <DetailSections
          systemId={id}
          latest={latestCompleted}
          fallbackScore={system.lastAuditScore ?? null}
          loadingRuns={loadingRuns}
          runs={runs}
          live={auditRuns?.some(isActive) === true}
        />
      </div>
    </PageShell>
  );
}
