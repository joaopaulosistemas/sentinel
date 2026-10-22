import {
  Button,
  InlineNotification,
  SkeletonText,
  Tag,
  Tile,
} from '@carbon/react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { StartAuditModal } from '../../components/StartAuditModal';
import { DiffSummary } from '../../components/DiffSummary';
import { InsightsPanel } from '../../components/InsightsPanel';
import { ProfileForm } from '../../components/ProfileForm';
import { ScheduleForm } from '../../components/ScheduleForm';
import { ScoreGauge } from '../../components/ScoreGauge';
import { SuppressionsList } from '../../components/SuppressionsList';
import { TrendChart } from '../../components/TrendChart';
import { useAuditRuns } from '../../hooks/useAuditRuns';
import { useSystem } from '../../hooks/useSystems';
import { useAuditStore } from '../../store/useAuditStore';
import type { AuditRunResponse, AuditStatus, Severity, Stack } from '../../types';

type TagType = 'warm-gray' | 'blue' | 'green' | 'red';

const stackLabels: Record<Stack, string> = {
  JAVA: 'Java',
  JAVASCRIPT: 'JavaScript',
  DOTNET: '.NET',
  PYTHON: 'Python',
  COBOL: 'COBOL',
  MIXED: 'Misto',
};

const auditStatusConfig: Record<AuditStatus, { label: string; type: TagType }> = {
  PENDING: { label: 'Pendente', type: 'warm-gray' },
  RUNNING: { label: 'Em execução', type: 'blue' },
  COMPLETED: { label: 'Concluída', type: 'green' },
  FAILED: { label: 'Falhou', type: 'red' },
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

export function SystemDetailPage() {
  const { systemId } = useParams<{ systemId: string }>();
  const navigate = useNavigate();
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);

  const id = Number(systemId);
  const [starting, setStarting] = useState(false);
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

  const recentRuns = auditRuns?.slice(0, 5) ?? [];

  return (
    <div style={{ padding: '2rem' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
        <Button kind="ghost" size="sm" onClick={() => navigate('/')}>
          ← Voltar
        </Button>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600, margin: 0 }}>{system.name}</h1>
        <Tag type="blue">{stackLabels[system.stack]}</Tag>
        <Tag type="warm-gray">{system.team}</Tag>
        <Button size="sm" style={{ marginLeft: 'auto' }} onClick={() => setStarting(true)}>
          Nova auditoria
        </Button>
      </div>
      <StartAuditModal system={starting ? system : null} onClose={() => setStarting(false)} />

      {/* Score section */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '200px 1fr',
          gap: '1.5rem',
          marginBottom: '2rem',
        }}
      >
        <Tile style={{ textAlign: 'center', padding: '1.5rem' }}>
          <p style={{ fontSize: '0.875rem', color: '#6f6f6f', marginBottom: '0.75rem' }}>
            Score Geral
          </p>
          <ScoreGauge score={latestCompleted?.riskScore?.overall ?? system.lastAuditScore} size="lg" />
        </Tile>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(6, 1fr)',
            gap: '0.75rem',
          }}
        >
          {dimensionLabels.map(({ key, label }) => (
            <Tile key={key} style={{ textAlign: 'center', padding: '1rem' }}>
              <p style={{ fontSize: '0.75rem', color: '#6f6f6f', marginBottom: '0.5rem' }}>
                {label}
              </p>
              <ScoreGauge score={latestCompleted?.riskScore?.[key] ?? null} size="sm" />
            </Tile>
          ))}
        </div>
      </div>

      {/* Findings summary from latest run */}
      {latestCompleted && (
        <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
          <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>
            Resumo de Findings — Última Auditoria Concluída
          </h3>
          <div style={{ display: 'flex', gap: '1.5rem' }}>
            {severityOrder.map((sev) => {
              const count = latestCompleted.findingsSummary[sev] ?? 0;
              return (
                <div key={sev} style={{ textAlign: 'center' }}>
                  <div
                    style={{
                      fontSize: '1.5rem',
                      fontWeight: 700,
                      color: count > 0 ? severityColors[sev] : '#6f6f6f',
                    }}
                  >
                    {count}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#6f6f6f' }}>{sev}</div>
                </div>
              );
            })}
          </div>
        </Tile>
      )}

      {latestCompleted && <DiffSummary auditId={latestCompleted.id} />}

      <InsightsPanel auditId={latestCompleted?.id} />

      <TrendChart systemId={id} />

      <ProfileForm system={system} />

      <ScheduleForm system={system} />

      <SuppressionsList systemId={id} />

      {/* Recent audit runs */}
      <Tile style={{ padding: '1.25rem' }}>
        <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>
          Últimas Auditorias
        </h3>
        {loadingRuns ? (
          <SkeletonText lineCount={5} />
        ) : recentRuns.length === 0 ? (
          <p style={{ color: '#6f6f6f' }}>Nenhuma auditoria encontrada.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {recentRuns.map((run) => {
              const cfg = auditStatusConfig[run.status];
              return (
                <div
                  key={run.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '1rem',
                    padding: '0.75rem',
                    background: '#f4f4f4',
                    borderRadius: '4px',
                  }}
                >
                  <Tag type={cfg.type} size="sm">
                    {cfg.label}
                  </Tag>
                  <span style={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                    {run.branch}
                  </span>
                  <span style={{ color: '#6f6f6f', fontSize: '0.8125rem' }}>
                    {new Date(run.startedAt).toLocaleString('pt-BR')}
                  </span>
                  {run.riskScore && (
                    <span style={{ marginLeft: 'auto', fontWeight: 600 }}>
                      Score: {run.riskScore.overall}
                    </span>
                  )}
                  {run.status === 'FAILED' && run.errorMessage && (
                    <span
                      title={run.errorMessage}
                      style={{
                        marginLeft: 'auto',
                        color: '#da1e28',
                        fontSize: '0.8125rem',
                        maxWidth: '50%',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {run.errorMessage}
                    </span>
                  )}
                  {(run.status === 'PENDING' || run.status === 'RUNNING') && (
                    <Button kind="ghost" size="sm" style={{ marginLeft: 'auto' }} onClick={() => navigate(`/audits/${run.id}`)}>
                      Acompanhar
                    </Button>
                  )}
                  {run.status === 'COMPLETED' && (
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => {
                        setSelectedAudit(run.id);
                        navigate(`/findings?auditId=${run.id}`);
                      }}
                    >
                      Ver Findings
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Tile>
    </div>
  );
}
