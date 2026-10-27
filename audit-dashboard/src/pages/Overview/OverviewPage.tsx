import { DataTableSkeleton, InlineNotification } from '@carbon/react';
import { PageShell } from '../../components/PageShell';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { StartAuditModal } from '../../components/StartAuditModal';
import { RecentAudits } from '../../components/RecentAudits';
import { usePendingClaude } from '../../hooks/usePendingClaude';
import { useRecentAudits } from '../../hooks/useAuditRuns';
import { useSystems } from '../../hooks/useSystems';
import type { AuditRunResponse, SystemSummaryResponse } from '../../types';
import { isActiveRun } from '../../utils/auditProgress';
import { STALE_DAYS, attentionRows, isStale, scoreColor } from '../../utils/systemAttention';

const names = (list: string[]) => (list.length <= 2 ? list.join(', ') : `${list.slice(0, 2).join(', ')} e mais ${list.length - 2}`);

/** Visão geral: quatro números, quem precisa de atenção e as últimas auditorias; daqui se inicia uma auditoria. */
export function OverviewPage() {
  const { data: systems, isLoading, error } = useSystems();
  const { data: recent } = useRecentAudits(20);
  const [pickedId, setPickedId] = useState('');
  const [started, setStarted] = useState<AuditRunResponse | null>(null);

  const list: SystemSummaryResponse[] = systems ?? [];
  const picked = list.find((s) => String(s.id) === pickedId) ?? list[0] ?? null;

  const pendingClaude = usePendingClaude();
  const [startTarget, setStartTarget] = useState<SystemSummaryResponse | null>(null);

  // O aviso some sozinho; a auditoria continua na lista de baixo.
  useEffect(() => {
    if (!started) return undefined;
    const t = setTimeout(() => setStarted(null), 10_000);
    return () => clearTimeout(t);
  }, [started]);

  if (isLoading) return <DataTableSkeleton columnCount={4} rowCount={5} />;

  if (error) {
    return (
      <InlineNotification
        kind="error"
        title="Erro ao carregar sistemas"
        subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
      />
    );
  }

  const total = list.length;
  const criticalSystems = list.filter((s) => s.openCritical > 0);
  const totalCritical = list.reduce((acc, s) => acc + s.openCritical, 0);
  const scores = list.map((s) => s.lastAuditScore).filter((s): s is number => s !== null);
  const avg = scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null;
  const deltas = list.map((s) => s.scoreDelta).filter((d): d is number => d != null);
  const avgDelta = deltas.length > 0 ? Math.round(deltas.reduce((a, b) => a + b, 0) / deltas.length) : null;
  const runningRuns = (recent ?? []).filter(isActiveRun);
  const now = Date.now();
  const staleSystems = list.filter((s) => isStale(s, now));
  const attention = attentionRows(list, now);

  const kpis = [
    {
      label: 'Nota média',
      value: avg ?? '-',
      color: scoreColor(avg),
      note: avgDelta !== null && avgDelta !== 0 ? `${avgDelta > 0 ? '+' : ''}${avgDelta} desde a auditoria anterior` : `${total} ${total === 1 ? 'sistema' : 'sistemas'}`,
    },
    {
      label: 'Achados críticos abertos',
      value: totalCritical,
      color: totalCritical > 0 ? 'var(--s-danger-text)' : 'var(--s-success-text)',
      note: totalCritical > 0 ? `em ${criticalSystems.length} ${criticalSystems.length === 1 ? 'sistema' : 'sistemas'}` : 'nenhum em aberto',
    },
    {
      label: 'Em execução agora',
      value: runningRuns.length,
      color: 'var(--s-link)',
      note: runningRuns.length > 0 ? names([...new Set(runningRuns.map((r) => r.systemName))]) : 'nenhuma auditoria',
    },
    {
      label: `Sem auditar há mais de ${STALE_DAYS} dias`,
      value: staleSystems.length,
      color: 'var(--s-text)',
      note: staleSystems.length > 0 ? names(staleSystems.map((s) => s.name)) : 'todos em dia',
    },
  ];

  return (
    <PageShell>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 16 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 400 }}>Visão geral</h1>
          <span style={{ color: 'var(--s-text-3)' }}>
            Situação {total === 1 ? 'do 1 sistema cadastrado' : `dos ${total} sistemas cadastrados`} · atualizada agora
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <label htmlFor="overview-system" style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Sistema</label>
          <select
            id="overview-system"
            value={picked ? String(picked.id) : ''}
            onChange={(e) => setPickedId(e.target.value)}
            disabled={list.length === 0}
            style={{ height: 40, padding: '0 12px', background: 'var(--s-surface)', border: '1px solid var(--s-border-strong)', fontSize: 14, minWidth: 220 }}
          >
            {list.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          <button
            type="button"
            disabled={!picked}
            onClick={() => picked && setStartTarget(picked)}
            style={{ height: 40, padding: '0 20px', background: !picked ? 'var(--s-border)' : 'var(--s-link)', color: '#fff', border: 0, fontSize: 14, cursor: !picked ? 'not-allowed' : 'pointer' }}
          >
            Nova auditoria
          </button>
        </div>
      </div>

      {started && (
        <div role="status" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: '4px solid var(--s-link)', padding: '12px 16px', display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <span>
            <strong style={{ fontWeight: 600 }}>Auditoria #{started.id} iniciada</strong> em {started.systemName}. Ela já aparece em "Últimas auditorias" abaixo.
          </span>
          <Link to={`/audits/${started.id}`} style={{ fontWeight: 500 }}>Acompanhar execução</Link>
        </div>
      )}

      {pendingClaude.total > 0 && (
        <div role="status" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: '4px solid var(--s-link)', padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 6 }}>
          <strong style={{ fontWeight: 600 }}>
            {pendingClaude.total === 1 ? '1 pedido do Claude espera' : `${pendingClaude.total} pedidos do Claude esperam`} sua decisão
          </strong>
          <span style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {pendingClaude.bySystem.map((r) => (
              <Link key={r.system.id} to={`/findings?aba=reauditoria&systemId=${r.system.id}`}>
                {r.system.name} ({r.count})
              </Link>
            ))}
          </span>
        </div>
      )}

      <section aria-label="Indicadores" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 1, background: 'var(--s-border)', border: '1px solid var(--s-border)' }}>
        {kpis.map((k) => (
          <div key={k.label} style={{ background: 'var(--s-surface)', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{k.label}</span>
            <span style={{ fontSize: 32, fontWeight: 300, color: k.color }}>{k.value}</span>
            <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{k.note}</span>
          </div>
        ))}
      </section>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))', gap: 24, alignItems: 'start' }}>
        <section aria-label="Precisam de atenção" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
          <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--s-border)' }}>
            <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Precisam de atenção</h2>
            <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>Do menor para o maior nota</span>
          </div>
          {attention.length === 0 ? (
            <p style={{ padding: '1rem', color: 'var(--s-text-3)' }}>Nenhum sistema cadastrado ainda.</p>
          ) : (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {attention.map(({ system, score, reason }) => (
                <li key={system.id} style={{ borderBottom: '1px solid var(--s-border)' }}>
                  <Link to={`/systems/${system.id}`} style={{ display: 'grid', gridTemplateColumns: '44px 1fr auto', gap: 12, alignItems: 'center', padding: '12px 16px', textDecoration: 'none', color: 'var(--s-text)' }}>
                    <span style={{ fontSize: 22, fontWeight: 300, color: scoreColor(score) }}>{score ?? '-'}</span>
                    <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                      <span style={{ fontWeight: 600 }}>{system.name}</span>
                      <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{reason}</span>
                    </span>
                    <span style={{ color: 'var(--s-link)', fontSize: 13 }}>Ver sistema</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <RecentAudits />
      </div>

      <StartAuditModal system={startTarget} onClose={() => setStartTarget(null)} onStarted={(run) => setStarted(run)} />
    </PageShell>
  );
}
