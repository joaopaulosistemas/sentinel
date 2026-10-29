import { Link } from 'react-router-dom';
import { useRecentAudits } from '../hooks/useAuditRuns';
import type { AuditRunResponse } from '../types';
import { analyzerCounts, isActiveRun, phaseIndex, PHASES } from '../utils/auditProgress';
import { scoreColor } from '../utils/systemAttention';
import { timeAgo } from '../utils/timeAgo';
import { SpinnerIcon } from './StatusIcons';

/** "Na fila", "Clonando", "Analisando 4/12", "Pontuando" — onde a auditoria em andamento está. */
export function stepText(run: AuditRunResponse): string {
  const i = phaseIndex(run);
  if (run.status === 'PENDING' || i === 0) return 'Na fila';
  if (i === 1) return 'Clonando';
  if (i === 2) {
    const { done, total } = analyzerCounts(run.progress?.analyzers ?? []);
    return total > 0 ? `Analisando ${done}/${total}` : 'Analisando';
  }
  return i === 3 ? 'Pontuando' : (PHASES[Math.min(i, PHASES.length - 1)]?.label ?? 'Em execução');
}

const cell = { padding: '12px 8px' } as const;

function RunStatus({ run }: { run: AuditRunResponse }) {
  if (isActiveRun(run)) {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-link)', fontWeight: 500 }}>
        <SpinnerIcon size={14} color="#0043ce" />
        {stepText(run)}
      </span>
    );
  }
  const finished: Record<string, { text: string; color: string }> = {
    COMPLETED: { text: 'Concluída', color: 'var(--s-success-strong)' },
    FAILED: { text: 'Falhou', color: 'var(--s-danger-text)' },
  };
  const { text, color } = finished[run.status] ?? { text: 'Cancelada', color: 'var(--s-text-3)' };
  return <span style={{ color, fontWeight: 500 }}>{text}</span>;
}

function RunRow({ run }: { run: AuditRunResponse }) {
  const score = run.riskScore ? Math.round(run.riskScore.overall) : null;
  return (
    <tr style={{ borderBottom: '1px solid var(--s-border)', background: isActiveRun(run) ? 'var(--s-selected)' : 'var(--s-surface)' }}>
      <td style={{ padding: '12px 16px' }}>
        <Link to={`/audits/${run.id}`} style={{ fontWeight: 500, textDecoration: 'none' }}>#{run.id}</Link>
        <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{timeAgo(run.startedAt)}</div>
      </td>
      <td style={{ ...cell, fontWeight: 500 }}>{run.systemName}</td>
      <td style={cell}><RunStatus run={run} /></td>
      <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 600, color: scoreColor(score) }}>{score ?? '-'}</td>
    </tr>
  );
}

function RunsTable({ rows }: { rows: AuditRunResponse[] }) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 560 }}>
        <thead>
          <tr style={{ background: 'var(--s-header-row)', textAlign: 'left', fontSize: 12, fontWeight: 600 }}>
            <th style={{ padding: '10px 16px' }}>Auditoria</th>
            <th style={{ padding: '10px 8px' }}>Sistema</th>
            <th style={{ padding: '10px 8px' }}>Situação</th>
            <th style={{ padding: '10px 16px', textAlign: 'right' }}>Nota</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((run) => <RunRow key={run.id} run={run} />)}
        </tbody>
      </table>
    </div>
  );
}

function RecentBody({ loading, rows }: { loading: boolean; rows: AuditRunResponse[] }) {
  if (loading) return <p style={{ padding: '1rem', color: 'var(--s-text-3)' }}>Carregando…</p>;
  if (rows.length === 0) return <p style={{ padding: '1rem', color: 'var(--s-text-3)' }}>Nenhuma auditoria ainda. Inicie a primeira em um sistema.</p>;
  return <RunsTable rows={rows} />;
}

/** Últimas auditorias de todos os sistemas, numa única consulta; atualiza sozinha enquanto houver alguma em andamento. */
export function RecentAudits() {
  // Mesma consulta da Visão geral (cache compartilhado); a lista mostra só as 8 mais recentes.
  const { data, isLoading } = useRecentAudits(20);
  const rows = (data ?? []).slice(0, 8);
  const live = (data ?? []).some(isActiveRun);

  return (
    <section aria-label="Últimas auditorias" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
      <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--s-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
        <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Últimas auditorias</h2>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--s-text-3)' }}>
          <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: 4, background: live ? '#0f62fe' : '#198038' }} />
          {live ? 'Atualizando sozinha a cada 3 s' : 'Atualizada agora'}
        </span>
      </div>
      <RecentBody loading={isLoading} rows={rows} />
    </section>
  );
}
