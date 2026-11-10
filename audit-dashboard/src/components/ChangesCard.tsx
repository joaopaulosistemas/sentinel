import { Link } from 'react-router-dom';
import type { IAuditRunResponse } from '../types';

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderRadius: 8, padding: '1.25rem 1.5rem' } as const;
const eyebrow = { fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'var(--s-text-3)' } as const;

function ChangeStat({ value, label, color }: { value: number; label: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 36, fontWeight: 300, lineHeight: 1, color }}>{value}</div>
      <div style={{ fontSize: 13, color: 'var(--s-text-3)', marginTop: 4 }}>{label}</div>
    </div>
  );
}

function persistingCount(run: IAuditRunResponse): number | null {
  if (run.totalFindings == null) return null;
  return Math.max(0, run.totalFindings - (run.newFindings ?? 0));
}

function ChangesBody({ run }: { run: IAuditRunResponse }) {
  const persisting = persistingCount(run);
  const novos = run.newFindings ?? 0;
  return (
    <>
      <div style={{ display: 'flex', gap: '12px 28px', flexWrap: 'wrap' }}>
        <ChangeStat value={novos} label="novos" color={novos ? 'var(--s-danger-text)' : 'var(--s-success-text)'} />
        <ChangeStat value={run.resolvedFindings ?? 0} label="corrigidos" color="var(--s-success-text)" />
        {persisting !== null && <ChangeStat value={persisting} label="continuam" />}
      </div>
      {novos > 0 && <Link to={`/findings?auditId=${run.id}&onlyNew=1`} style={{ fontSize: 13 }}>Ver só os novos</Link>}
    </>
  );
}

/** O que mudou desde a auditoria anterior da mesma branch. */
export function ChangesCard({ run }: { run: IAuditRunResponse }) {
  const hasBase = run.previousAuditId != null && run.newFindings != null && run.resolvedFindings != null;
  return (
    <section aria-label="O que mudou" style={{ ...box, display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={eyebrow}>O que mudou desde #{run.previousAuditId ?? 'a anterior'}</div>
      {hasBase ? (
        <ChangesBody run={run} />
      ) : (
        <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>Primeira auditoria concluída desta branch: ainda não há com o que comparar.</p>
      )}
    </section>
  );
}
