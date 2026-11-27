import type { ReactNode } from 'react';
import type { ISystemSummaryResponse } from '../../types';
import type { StateTone } from '../../utils/mcpSetup';

export const TONES: Record<StateTone, { bg: string; fg: string }> = {
  good: { bg: 'var(--s-good-bg)', fg: 'var(--s-success-text)' },
  bad: { bg: 'var(--s-bad-bg)', fg: 'var(--s-danger-text)' },
  warn: { bg: 'var(--s-warn-bg)', fg: 'var(--s-warn-text)' },
  idle: { bg: 'var(--s-layer)', fg: 'var(--s-text-3)' },
};

export const mono = "'IBM Plex Mono', ui-monospace, monospace";

export function Pill({ tone, children }: { tone: StateTone; children: ReactNode }) {
  return (
    <span style={{ display: 'inline-block', padding: '2px 10px', fontSize: 12, fontWeight: 600, background: TONES[tone].bg, color: TONES[tone].fg }}>
      {children}
    </span>
  );
}

export function Tile({ label, value, tone }: { label: string; value: string | number; tone?: StateTone }) {
  return (
    <div style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '10px 14px', display: 'grid', gap: 2 }}>
      <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{label}</span>
      <span style={{ fontSize: 24, color: tone ? TONES[tone].fg : 'var(--s-text)' }}>{value}</span>
    </div>
  );
}

/** Escolha do sistema que as abas Conectar e Permissões mostram. */
export function SystemPicker({ systems, value, onChange }: { systems: ISystemSummaryResponse[]; value: number | undefined; onChange: (id: number) => void }) {
  return (
    <label style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)', maxWidth: 320 }}>
      Sistema
      <select
        value={value ?? ''}
        onChange={(e) => onChange(Number(e.target.value))}
        style={{ height: 40, padding: '0 8px', border: 0, borderBottom: '2px solid var(--s-link)', background: 'var(--s-layer)', fontSize: 14 }}
      >
        {systems.map((s) => (
          <option key={s.id} value={s.id}>{s.name}</option>
        ))}
      </select>
    </label>
  );
}

export const emptyStyle = { fontSize: 14, color: 'var(--s-text-3)', padding: '16px 0' } as const;
