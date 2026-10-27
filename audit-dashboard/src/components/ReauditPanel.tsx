import { InlineNotification } from '@carbon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuditRuns } from '../hooks/useAuditRuns';
import { useDiff } from '../hooks/useLifecycle';
import type { PendingAction, SystemSummaryResponse } from '../types';
import { startLabel } from '../utils/timeAgo';
import { StartAuditModal } from './StartAuditModal';

const btn = { height: 32, padding: '0 12px', border: '1px solid var(--s-link)', background: 'var(--s-surface)', color: 'var(--s-link)', cursor: 'pointer', fontSize: 13 } as const;
const btnPrimary = { ...btn, background: 'var(--s-primary)', color: '#fff' } as const;
const btnDanger = { ...btn, border: '1px solid #da1e28', color: 'var(--s-danger-text)' } as const;

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Esperando aprovação',
  APPROVED: 'Aprovado',
  REJECTED: 'Recusado',
  EXPIRED: 'Venceu sem decisão',
};

/** Texto da variação de nota: "62 → 71 (+9)". */
export function scoreChange(before: number | null, after: number | null): string {
  if (after === null) return 'sem nota';
  if (before === null) return `${Math.round(after)}`;
  const delta = Math.round(after) - Math.round(before);
  return `${Math.round(before)} → ${Math.round(after)} (${delta > 0 ? '+' : ''}${delta})`;
}

function Requests({ systemId }: { systemId: number }) {
  const queryClient = useQueryClient();
  const key = ['pending-actions', systemId];
  const list = useQuery({ queryKey: key, queryFn: () => api.getPendingActions(systemId), refetchInterval: 15_000 });
  const decide = useMutation({
    mutationFn: (v: { id: number; decision: 'approve' | 'reject' }) => api.decidePendingAction(systemId, v.id, v.decision),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data);
      void queryClient.invalidateQueries({ queryKey: ['auditRuns'] });
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
    },
  });
  const items: PendingAction[] = list.data ?? [];
  const waiting = items.filter((a) => a.status === 'PENDING').length;

  return (
    <section aria-label="Pedidos do Claude" style={{ display: 'grid', gap: 10 }}>
      <h2 style={{ margin: 0, fontSize: 18, fontWeight: 400 }}>Pedidos do Claude ({waiting} {waiting === 1 ? 'esperando' : 'esperando'})</h2>
      <p style={{ margin: 0, fontSize: 13, color: 'var(--s-text-3)' }}>
        O Claude só pede; nada acontece até você aprovar aqui. Cada pedido vale por 1 hora.
      </p>
      {decide.isError && <InlineNotification kind="error" title="Não foi possível" subtitle={(decide.error as Error).message} hideCloseButton />}
      {items.length === 0 ? (
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Nenhum pedido. Libere iniciar_auditoria ou suprimir_achado na aba Claude (MCP) do sistema.</span>
      ) : (
        items.map((a) => (
          <article key={a.id} aria-label={`Pedido ${a.id}`} style={{ border: '1px solid var(--s-border)', background: a.status === 'PENDING' ? 'var(--s-surface)' : 'var(--s-layer)', padding: 14, display: 'grid', gap: 8 }}>
            <div style={{ fontSize: 14 }}>{a.summary}</div>
            <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
              {STATUS_LABEL[a.status]} · pedido {startLabel(a.createdAt)}
              {a.resultNote ? ` · ${a.resultNote}` : ''}
              {a.auditRunId ? <> · <Link to={`/audits/${a.auditRunId}`}>acompanhar</Link></> : null}
            </div>
            {a.status === 'PENDING' && (
              <div style={{ display: 'flex', gap: 8 }}>
                <button type="button" style={btnPrimary} disabled={decide.isPending} onClick={() => decide.mutate({ id: a.id, decision: 'approve' })}>Aprovar</button>
                <button type="button" style={btnDanger} disabled={decide.isPending} onClick={() => decide.mutate({ id: a.id, decision: 'reject' })}>Recusar</button>
              </div>
            )}
          </article>
        ))
      )}
    </section>
  );
}

function BeforeAfter({ systemId }: { systemId: number }) {
  const runs = useAuditRuns(systemId);
  const completed = (runs.data ?? []).filter((r) => r.status === 'COMPLETED');
  const latest = completed[0];
  const diff = useDiff(latest?.id);
  const d = diff.data;

  return (
    <section aria-label="Antes e depois" style={{ display: 'grid', gap: 8 }}>
      <h2 style={{ margin: 0, fontSize: 18, fontWeight: 400 }}>Antes e depois</h2>
      {!latest ? (
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Ainda não há auditoria concluída.</span>
      ) : !d || !d.hasBaseline ? (
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>A última auditoria (#{latest.id}) é a primeira: ainda não há com o que comparar.</span>
      ) : (
        <div style={{ border: '1px solid var(--s-border)', background: 'var(--s-surface)', padding: 14, display: 'grid', gap: 6 }}>
          <div>
            Auditoria <Link to={`/audits/${latest.id}`}>#{latest.id}</Link> contra #{d.baseAuditId}: nota <strong>{scoreChange(d.baseOverall, d.overall)}</strong>
          </div>
          <div style={{ fontSize: 13, color: 'var(--s-text-2)' }}>
            {d.resolvedFindings.length} resolvidos · {d.newFindings.length} novos · {d.persistingCount} continuam
          </div>
          {d.resolvedFindings.slice(0, 5).map((f) => (
            <div key={`r${f.id}`} style={{ fontSize: 12, color: 'var(--s-success-text)' }}>resolvido: {f.title}</div>
          ))}
          {d.newFindings.slice(0, 5).map((f) => (
            <div key={`n${f.id}`} style={{ fontSize: 12, color: 'var(--s-danger-text)' }}>novo: {f.title}</div>
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * Aba "Reauditoria": pedidos do Claude esperando aprovação, reauditar agora e o antes e depois da última auditoria.
 */
export function ReauditPanel({ systems, initialSystemId }: { systems: SystemSummaryResponse[]; initialSystemId?: number }) {
  const [systemId, setSystemId] = useState<number | undefined>(initialSystemId ?? systems[0]?.id);
  const [starting, setStarting] = useState(false);
  const system = systems.find((s) => s.id === systemId) ?? null;

  return (
    <section aria-label="Reauditoria" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <label style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)', minWidth: 260 }}>
          Sistema
          <select value={systemId ?? ''} onChange={(e) => setSystemId(e.target.value === '' ? undefined : Number(e.target.value))} style={{ height: 40, padding: '0 8px', border: 0, borderBottom: '2px solid var(--s-link)', background: 'var(--s-layer)', fontSize: 14 }}>
            {systems.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </label>
        <button type="button" style={btnPrimary} disabled={!system} onClick={() => setStarting(true)}>Reauditar agora</button>
      </div>
      {systemId !== undefined && (
        <>
          <Requests systemId={systemId} />
          <BeforeAfter systemId={systemId} />
        </>
      )}
      <StartAuditModal system={starting ? system : null} onClose={() => setStarting(false)} />
    </section>
  );
}
