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

interface Kpi {
  label: string;
  value: number | string;
  color: string;
  note: string;
}

const plural = (n: number, one: string, many: string) => (n === 1 ? one : many);

function averageDelta(list: SystemSummaryResponse[]): number | null {
  const deltas = list.map((s) => s.scoreDelta).filter((d): d is number => d != null);
  return deltas.length > 0 ? Math.round(deltas.reduce((a, b) => a + b, 0) / deltas.length) : null;
}

function averageScore(list: SystemSummaryResponse[]): number | null {
  const scores = list.map((s) => s.lastAuditScore).filter((s): s is number => s !== null);
  return scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null;
}

function averageNote(avgDelta: number | null, total: number): string {
  if (avgDelta !== null && avgDelta !== 0) return `${avgDelta > 0 ? '+' : ''}${avgDelta} desde a auditoria anterior`;
  return `${total} ${plural(total, 'sistema', 'sistemas')}`;
}

function criticalKpi(list: SystemSummaryResponse[]): Kpi {
  const criticalSystems = list.filter((s) => s.openCritical > 0);
  const totalCritical = list.reduce((acc, s) => acc + s.openCritical, 0);
  return {
    label: 'Achados críticos abertos',
    value: totalCritical,
    color: totalCritical > 0 ? 'var(--s-danger-text)' : 'var(--s-success-text)',
    note: totalCritical > 0 ? `em ${criticalSystems.length} ${plural(criticalSystems.length, 'sistema', 'sistemas')}` : 'nenhum em aberto',
  };
}

function buildKpis(list: SystemSummaryResponse[], recent: AuditRunResponse[], now: number): Kpi[] {
  const avg = averageScore(list);
  const runningRuns = recent.filter(isActiveRun);
  const staleSystems = list.filter((s) => isStale(s, now));
  return [
    { label: 'Nota média', value: avg ?? '-', color: scoreColor(avg), note: averageNote(averageDelta(list), list.length) },
    criticalKpi(list),
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
}

const noticeBox = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: '4px solid var(--s-link)', padding: '12px 16px' } as const;

function OverviewHeader({ list, picked, onPick, onStart }: {
  list: SystemSummaryResponse[];
  picked: SystemSummaryResponse | null;
  onPick: (id: string) => void;
  onStart: () => void;
}) {
  const total = list.length;
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 16 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>Visão geral</h1>
        <span style={{ color: 'var(--s-text-3)' }}>
          Situação {total === 1 ? 'do 1 sistema cadastrado' : `dos ${total} sistemas cadastrados`} · atualizada agora
        </span>
      </div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <label htmlFor="overview-system" style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Sistema</label>
        <select
          id="overview-system"
          value={picked ? String(picked.id) : ''}
          onChange={(e) => onPick(e.target.value)}
          disabled={total === 0}
          style={{ height: 40, padding: '0 12px', background: 'var(--s-surface)', border: '1px solid var(--s-border-strong)', fontSize: 14, minWidth: 220 }}
        >
          {list.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
        <button
          type="button"
          disabled={!picked}
          onClick={onStart}
          style={{ height: 40, padding: '0 20px', background: !picked ? 'var(--s-border)' : 'var(--s-link)', color: '#fff', border: 0, fontSize: 14, cursor: !picked ? 'not-allowed' : 'pointer' }}
        >
          Nova auditoria
        </button>
      </div>
    </div>
  );
}

function StartedNotice({ run }: { run: AuditRunResponse }) {
  return (
    <div role="status" style={{ ...noticeBox, display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
      <span>
        <strong style={{ fontWeight: 600 }}>Auditoria #{run.id} iniciada</strong> em {run.systemName}. Ela já aparece em &quot;Últimas auditorias&quot; abaixo.
      </span>
      <Link to={`/audits/${run.id}`} style={{ fontWeight: 500 }}>Acompanhar execução</Link>
    </div>
  );
}

function PendingClaudeNotice({ pending }: { pending: ReturnType<typeof usePendingClaude> }) {
  if (pending.total === 0) return null;
  return (
    <div role="status" style={{ ...noticeBox, display: 'flex', flexDirection: 'column', gap: 6 }}>
      <strong style={{ fontWeight: 600 }}>
        {pending.total === 1 ? '1 pedido do Claude espera' : `${pending.total} pedidos do Claude esperam`} sua decisão
      </strong>
      <span style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        {pending.bySystem.map((r) => (
          <Link key={r.system.id} to={`/findings?aba=reauditoria&systemId=${r.system.id}`}>
            {r.system.name} ({r.count})
          </Link>
        ))}
      </span>
    </div>
  );
}

function KpiGrid({ kpis }: { kpis: Kpi[] }) {
  return (
    <section aria-label="Indicadores" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(220px, 100%), 1fr))', gap: 1, background: 'var(--s-border)', border: '1px solid var(--s-border)' }}>
      {kpis.map((k) => (
        <div key={k.label} style={{ background: 'var(--s-surface)', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{k.label}</span>
          <span style={{ fontSize: 32, fontWeight: 300, color: k.color }}>{k.value}</span>
          <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{k.note}</span>
        </div>
      ))}
    </section>
  );
}

function AttentionList({ rows }: { rows: ReturnType<typeof attentionRows> }) {
  return (
    <section aria-label="Precisam de atenção" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
      <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--s-border)' }}>
        <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Precisam de atenção</h2>
        <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>Do menor para o maior nota</span>
      </div>
      {rows.length === 0 ? (
        <p style={{ padding: '1rem', color: 'var(--s-text-3)' }}>Nenhum sistema cadastrado ainda.</p>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {rows.map(({ system, score, reason }) => (
            <li key={system.id} className="sentinel-linha-hover" style={{ borderBottom: '1px solid var(--s-border)' }}>
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
  );
}

/** O aviso de auditoria iniciada some sozinho; a auditoria continua na lista de baixo. */
function useStartedNotice() {
  const [started, setStarted] = useState<AuditRunResponse | null>(null);
  useEffect(() => {
    if (!started) return undefined;
    const t = setTimeout(() => setStarted(null), 10_000);
    return () => clearTimeout(t);
  }, [started]);
  return [started, setStarted] as const;
}

function OverviewError({ error }: { error: unknown }) {
  return (
    <InlineNotification
      kind="error"
      title="Erro ao carregar sistemas"
      subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
    />
  );
}

/** Visão geral: quatro números, quem precisa de atenção e as últimas auditorias; daqui se inicia uma auditoria. */
export function OverviewPage() {
  const { data: systems, isLoading, error } = useSystems();
  const { data: recent } = useRecentAudits(20);
  const [pickedId, setPickedId] = useState('');
  const [started, setStarted] = useStartedNotice();
  const pendingClaude = usePendingClaude();
  const [startTarget, setStartTarget] = useState<SystemSummaryResponse | null>(null);

  const list: SystemSummaryResponse[] = systems ?? [];
  const picked = list.find((s) => String(s.id) === pickedId) ?? list[0] ?? null;

  if (isLoading) return <DataTableSkeleton columnCount={4} rowCount={5} />;
  if (error) return <OverviewError error={error} />;

  const now = Date.now();
  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Visão geral' }]}>
      <OverviewHeader list={list} picked={picked} onPick={setPickedId} onStart={() => picked && setStartTarget(picked)} />
      {started && <StartedNotice run={started} />}
      <PendingClaudeNotice pending={pendingClaude} />
      <KpiGrid kpis={buildKpis(list, recent ?? [], now)} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(380px, 100%), 1fr))', gap: 24, alignItems: 'start' }}>
        <AttentionList rows={attentionRows(list, now)} />
        <RecentAudits />
      </div>
      <StartAuditModal system={startTarget} onClose={() => setStartTarget(null)} onStarted={(run) => setStarted(run)} />
    </PageShell>
  );
}
