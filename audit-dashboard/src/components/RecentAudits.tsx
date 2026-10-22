import { Tag, Tile } from '@carbon/react';
import { Link } from 'react-router-dom';
import { useRecentAudits } from '../hooks/useAuditRuns';
import type { AuditRunResponse } from '../types';
import { analyzerCounts, isActiveRun, phaseIndex, PHASES } from '../utils/auditProgress';
import { auditStatusConfig } from '../utils/auditStatus';

function stepText(run: AuditRunResponse): string {
  const i = phaseIndex(run);
  const base = PHASES[Math.min(i, PHASES.length - 1)].label;
  if (run.progress && run.progress.phase === 'ANALISE') {
    const { done, total } = analyzerCounts(run.progress.analyzers);
    if (total > 0) return `${base} ${done}/${total}`;
  }
  return base;
}

/** Últimas auditorias de todos os sistemas, numa única consulta; atualiza sozinha enquanto houver alguma em andamento. */
export function RecentAudits() {
  // Mesma consulta da Visão geral (cache compartilhado); a lista mostra só as 8 mais recentes.
  const { data, isLoading } = useRecentAudits(20);
  const rows = (data ?? []).slice(0, 8);
  const live = (data ?? []).some(isActiveRun);

  return (
    <Tile style={{ padding: 0, marginBottom: '2rem' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '1rem 1rem 0.75rem',
          borderBottom: '1px solid #e0e0e0',
        }}
      >
        <h2 style={{ fontSize: '1rem', fontWeight: 600, margin: 0 }}>Últimas auditorias</h2>
        <span style={{ fontSize: '0.75rem', color: '#525252' }}>
          {live ? 'Atualizando sozinha a cada 3 s' : 'Atualizada agora'}
        </span>
      </div>
      {isLoading ? (
        <p style={{ padding: '1rem', color: '#525252' }}>Carregando…</p>
      ) : rows.length === 0 ? (
        <p style={{ padding: '1rem', color: '#525252' }}>Nenhuma auditoria ainda. Inicie a primeira em um sistema.</p>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {rows.map((run) => {
            const cfg = auditStatusConfig[run.status];
            return (
              <li
                key={run.id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '90px minmax(0, 2fr) 150px minmax(0, 1fr) 70px',
                  gap: '1rem',
                  alignItems: 'center',
                  padding: '0.75rem 1rem',
                  borderBottom: '1px solid #e0e0e0',
                  background: isActiveRun(run) ? '#edf5ff' : undefined,
                }}
              >
                <Link to={`/audits/${run.id}`}>#{run.id}</Link>
                <span style={{ fontWeight: 500 }}>{run.systemName}</span>
                <span style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <Tag type={cfg.type} size="sm">
                    {cfg.label}
                  </Tag>
                </span>
                <span style={{ color: '#525252', fontSize: '0.8125rem' }}>
                  {isActiveRun(run) ? stepText(run) : new Date(run.startedAt).toLocaleString('pt-BR')}
                </span>
                <span style={{ textAlign: 'right', fontWeight: 600 }}>{run.riskScore?.overall ?? '—'}</span>
              </li>
            );
          })}
        </ul>
      )}
    </Tile>
  );
}
