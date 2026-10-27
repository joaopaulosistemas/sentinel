import { InlineNotification, SkeletonText, Tile } from '@carbon/react';
import { Crumbs, PageShell } from '../../components/PageShell';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { StartAuditModal } from '../../components/StartAuditModal';
import { DiffSummary } from '../../components/DiffSummary';
import { InsightsPanel } from '../../components/InsightsPanel';
import { CheckIcon, CrossIcon, DashIcon, SpinnerIcon } from '../../components/StatusIcons';
import { ScoreGauge } from '../../components/ScoreGauge';
import { SuppressionsList } from '../../components/SuppressionsList';
import { TrendChart } from '../../components/TrendChart';
import { useAuditRuns } from '../../hooks/useAuditRuns';
import { useSystem } from '../../hooks/useSystems';
import type { AuditRunResponse, Severity, Stack } from '../../types';
import { analyzerCounts, PHASES, phaseIndex, progressPercent } from '../../utils/auditProgress';
import { scoreColor } from '../../utils/systemAttention';
import { startLabel } from '../../utils/timeAgo';
import { JourneySteps } from '../../components/JourneySteps';

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
  CRITICAL: '#da1e28',
  HIGH: '#ff832b',
  MEDIUM: '#f1c21b',
  LOW: '#24a148',
  INFO: '#0f62fe',
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

/** "252 (4 novos, 9 resolvidos)"; sem comparação mostra só o total. */
function findingsText(run: AuditRunResponse): string {
  if (run.status === 'PENDING' || run.status === 'RUNNING') return 'calculando';
  if (run.status !== 'COMPLETED' || run.totalFindings === null || run.totalFindings === undefined) return '-';
  if (run.newFindings !== null && run.newFindings !== undefined && run.resolvedFindings !== null && run.resolvedFindings !== undefined) {
    return `${run.totalFindings} (${run.newFindings} ${run.newFindings === 1 ? 'novo' : 'novos'}, ${run.resolvedFindings} ${run.resolvedFindings === 1 ? 'resolvido' : 'resolvidos'})`;
  }
  return String(run.totalFindings);
}

export function SystemDetailPage() {
  const { systemId } = useParams<{ systemId: string }>();
  const navigate = useNavigate();

  const id = Number(systemId);
  const [starting, setStarting] = useState(false);
  const [started, setStarted] = useState<AuditRunResponse | null>(null);
  const { data: system, isLoading: loadingSystem, error: systemError } = useSystem(id);
  const { data: auditRuns, isLoading: loadingRuns } = useAuditRuns(id);

  if (loadingSystem) return (
    <div style={{ padding: '2rem' }}>
      <SkeletonText paragraph lineCount={8} />
    </div>
  );

  if (systemError || !system) {
    return (
      <InlineNotification
        kind="error"
        title="Sistema não encontrado"
        subtitle={systemError instanceof Error ? systemError.message : 'ID inválido'}
        style={{ margin: '2rem' }}
      />
    );
  }

  // Use the most recent completed audit for scores
  const latestCompleted = auditRuns
    ?.filter((r) => r.status === 'COMPLETED')
    .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())[0];

  const recentRuns = auditRuns?.slice(0, 8) ?? [];
  const live = (auditRuns ?? []).some((r) => r.status === 'PENDING' || r.status === 'RUNNING');

  return (
    <PageShell
      trail={
        <>
          <JourneySteps current={1} />
          <Crumbs>
            <Link to="/systems">Sistemas</Link> &nbsp;/&nbsp; {system.name}
          </Crumbs>
        </>
      }
    >
      {started && (
        <div role="status" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: '4px solid var(--s-link)', padding: '12px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <span>
            <strong style={{ fontWeight: 600 }}>Auditoria #{started.id} iniciada</strong> na branch <span style={{ fontFamily: mono }}>{started.branch}</span>. Ela já aparece na lista abaixo e se atualiza sozinha.
          </span>
          <span style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
            <Link to={`/audits/${started.id}`} style={{ fontWeight: 500 }}>Acompanhar execução</Link>
            <button type="button" aria-label="Fechar aviso" onClick={() => setStarted(null)} style={{ background: 'transparent', border: 0, cursor: 'pointer', padding: 4 }}>
              <DashIcon size={14} color="var(--s-text-3)" />
            </button>
          </span>
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 16 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 400 }}>{system.name}</h1>
          <span style={{ color: 'var(--s-text-3)' }}>
            {[stackLabels[system.stack], system.team, `perfil ${system.profile || 'default'}`].filter(Boolean).join(' · ')}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" onClick={() => navigate(`/systems?edit=${system.id}`)} style={{ height: 40, padding: '0 16px', background: 'var(--s-surface)', color: 'var(--s-text)', border: '1px solid var(--s-border-strong)', fontSize: 14, cursor: 'pointer' }}>
            Editar sistema
          </button>
          <button type="button" onClick={() => setStarting(true)} style={{ height: 40, padding: '0 20px', background: 'var(--s-primary)', color: '#fff', border: 0, fontSize: 14, cursor: 'pointer' }}>
            Nova auditoria
          </button>
        </div>
      </div>
      <StartAuditModal system={starting ? system : null} onClose={() => setStarting(false)} onStarted={setStarted} />

      <section aria-label="Últimas auditorias" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--s-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Últimas auditorias</h2>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--s-text-3)' }}>
            <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: 4, background: live ? '#0f62fe' : '#198038' }} />
            {live ? 'Atualizando a cada 3 s enquanto houver auditoria em execução' : 'Atualizado agora'}
          </span>
        </div>
        {loadingRuns ? (
          <div style={{ padding: 16 }}><SkeletonText lineCount={5} /></div>
        ) : recentRuns.length === 0 ? (
          <p style={{ padding: 16, color: 'var(--s-text-3)', margin: 0 }}>Nenhuma auditoria encontrada.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 860 }}>
              <thead>
                <tr style={{ background: 'var(--s-header-row)', textAlign: 'left', fontSize: 12, fontWeight: 600 }}>
                  <th style={{ padding: '12px 16px' }}>Auditoria</th>
                  <th style={{ padding: '12px 8px' }}>Branch</th>
                  <th style={{ padding: '12px 8px' }}>Situação</th>
                  <th style={{ padding: '12px 8px' }}>Nota</th>
                  <th style={{ padding: '12px 8px' }}>Achados</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right' }}>Início</th>
                </tr>
              </thead>
              <tbody>
                {recentRuns.map((run) => {
                  const active = run.status === 'PENDING' || run.status === 'RUNNING';
                  const score = run.riskScore ? Math.round(run.riskScore.overall) : null;
                  const pct = progressPercent(run);
                  return (
                    <tr key={run.id} style={{ borderBottom: '1px solid var(--s-border)', background: active ? 'var(--s-selected)' : 'var(--s-surface)' }}>
                      <td style={{ padding: '12px 16px' }}>
                        <Link to={`/audits/${run.id}`} style={{ fontWeight: 500, textDecoration: 'none' }}>#{run.id}</Link>
                      </td>
                      <td style={{ ...cell, fontFamily: mono, fontSize: 13 }}>{run.branch}</td>
                      <td style={{ ...cell, minWidth: 240 }}>
                        {active ? (
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
                        ) : run.status === 'COMPLETED' ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-success-strong)', fontWeight: 500 }}>
                            <CheckIcon size={14} />
                            Concluída
                          </span>
                        ) : run.status === 'FAILED' ? (
                          <span title={run.errorMessage ?? undefined} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-danger-text)', fontWeight: 500 }}>
                            <CrossIcon size={14} />
                            Falhou{run.errorMessage ? ` · ${run.errorMessage.length > 60 ? `${run.errorMessage.slice(0, 60)}…` : run.errorMessage}` : ''}
                          </span>
                        ) : (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-text-3)', fontWeight: 500 }}>
                            <DashIcon size={14} />
                            Cancelada
                          </span>
                        )}
                      </td>
                      <td style={{ ...cell, fontWeight: 600, color: scoreColor(score) }}>{score ?? '-'}</td>
                      <td style={{ ...cell, color: 'var(--s-text-2)' }}>{findingsText(run)}</td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', color: 'var(--s-text-3)', fontSize: 13 }}>{startLabel(run.startedAt)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Detalhe da última auditoria concluída */}
      <div aria-label="Última auditoria concluída" style={{ display: 'grid', gridTemplateColumns: '200px 1fr', gap: '1.5rem' }}>
        <Tile style={{ textAlign: 'center', padding: '1.5rem' }}>
          <p style={{ fontSize: '0.875rem', color: 'var(--s-text-4)', marginBottom: '0.75rem' }}>Score Geral</p>
          <ScoreGauge score={latestCompleted?.riskScore?.overall ?? system.lastAuditScore} size="lg" />
        </Tile>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))', gap: '0.75rem' }}>
          {dimensionLabels.map(({ key, label }) => (
            <Tile key={key} style={{ textAlign: 'center', padding: '1rem' }}>
              <p style={{ fontSize: '0.75rem', color: 'var(--s-text-4)', marginBottom: '0.5rem' }}>{label}</p>
              <ScoreGauge score={latestCompleted?.riskScore?.[key] ?? null} size="sm" />
            </Tile>
          ))}
        </div>
      </div>

      {latestCompleted && (
        <Tile style={{ padding: '1.25rem' }}>
          <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Resumo de Findings — Última Auditoria Concluída</h3>
          <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
            {severityOrder.map((sev) => {
              const count = latestCompleted.findingsSummary[sev] ?? 0;
              return (
                <div key={sev} style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: '1.5rem', fontWeight: 700, color: count > 0 ? severityColors[sev] : 'var(--s-text-4)' }}>{count}</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--s-text-4)' }}>{sev}</div>
                </div>
              );
            })}
          </div>
        </Tile>
      )}

      {latestCompleted && <DiffSummary auditId={latestCompleted.id} />}

      <InsightsPanel auditId={latestCompleted?.id} />

      <TrendChart systemId={id} />

      <SuppressionsList systemId={id} />
    </PageShell>
  );
}
