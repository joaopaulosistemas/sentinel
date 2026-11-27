import { InlineNotification } from '@carbon/react';
import { useState } from 'react';
import { useDecide } from '../../hooks/useClaudeArea';
import { KIND_LABEL, type DecisionKind, type IDecision, type IDecisionButton } from '../../utils/claudeDecisions';
import { Pill, emptyStyle } from './ClaudeParts';

const btn = { height: 32, padding: '0 12px', border: '1px solid var(--s-link)', background: 'var(--s-surface)', color: 'var(--s-link)', cursor: 'pointer', fontSize: 13 } as const;
const btnPrimary = { ...btn, background: 'var(--s-primary)', color: '#fff' } as const;

type Filter = 'todas' | DecisionKind;

const FILTERS: Array<{ id: Filter; label: string }> = [
  { id: 'todas', label: 'Todas' },
  { id: 'pedido', label: 'Pedidos' },
  { id: 'regra', label: 'Regras' },
  { id: 'sugestao', label: 'Sugestões' },
];

function DecisionRow({ d, busy, onPress }: { d: IDecision; busy: boolean; onPress: (b: IDecisionButton) => void }) {
  return (
    <li aria-label={`${KIND_LABEL[d.kind]}: ${d.title}`} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', padding: '12px 0', borderTop: '1px solid var(--s-border)' }}>
      <div style={{ flex: 1, minWidth: 240 }}>
        <div style={{ fontWeight: 600 }}>{d.title}</div>
        <div style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
          {KIND_LABEL[d.kind]} · {d.systemName}
          {d.detail ? ` · ${d.detail}` : ''}
        </div>
      </div>
      <Pill tone={d.kind === 'pedido' ? 'warn' : 'idle'}>{KIND_LABEL[d.kind]}</Pill>
      <button type="button" style={btnPrimary} disabled={busy} onClick={() => onPress(d.primary)}>{d.primary.label}</button>
      {d.secondary && <button type="button" style={btn} disabled={busy} onClick={() => onPress(d.secondary as IDecisionButton)}>{d.secondary.label}</button>}
    </li>
  );
}

/** A caixa de decisões: tudo o que o Claude deixou esperando uma pessoa, de todos os sistemas, em um só lugar. */
export function ClaudeDecisions({ items, loading }: { items: IDecision[]; loading: boolean }) {
  const [filter, setFilter] = useState<Filter>('todas');
  const decide = useDecide();
  const shown = filter === 'todas' ? items : items.filter((d) => d.kind === filter);
  const count = (id: Filter) => (id === 'todas' ? items.length : items.filter((d) => d.kind === id).length);
  return (
    <section aria-label="Decisões do Claude" style={{ display: 'grid', gap: 12 }}>
      <p style={{ margin: 0, fontSize: 14, color: 'var(--s-text-3)' }}>
        Só o que precisa de uma pessoa: pedidos do Claude (nada acontece até você aprovar), regras no funil e sugestões abertas. Nada daqui entra na nota
        até uma regra ser promovida por você.
      </p>
      <div role="group" aria-label="Filtrar decisões" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {FILTERS.map((f) => (
          <button key={f.id} type="button" aria-pressed={filter === f.id} onClick={() => setFilter(f.id)} style={{ ...btn, background: filter === f.id ? 'var(--s-selected)' : 'var(--s-surface)' }}>
            {f.label} ({count(f.id)})
          </button>
        ))}
      </div>
      {decide.isError && <InlineNotification kind="error" title="Não foi possível" subtitle={(decide.error as Error).message} hideCloseButton />}
      {loading && items.length === 0 && <div style={emptyStyle}>Carregando…</div>}
      {!loading && shown.length === 0 && <div style={emptyStyle}>Nada esperando você. O Claude avisa aqui quando precisar de uma decisão.</div>}
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {shown.map((d) => (
          <DecisionRow key={d.key} d={d} busy={decide.isPending} onPress={(b) => decide.mutate(b.action)} />
        ))}
      </ul>
    </section>
  );
}
