import { InlineNotification } from '@carbon/react';
import { useState } from 'react';
import { ClaudeReviewPanel } from '../../components/ClaudeReviewPanel';
import { useDecide } from '../../hooks/useClaudeArea';
import type { ISystemSummaryResponse } from '../../types';
import { KIND_LABEL, isWaiting, type DecisionKind, type IDecision, type IDecisionButton } from '../../utils/claudeDecisions';
import { startLabel } from '../../utils/timeAgo';
import { Pill, emptyStyle, type StateTone } from './ClaudeParts';

const btn = { height: 32, padding: '0 12px', border: '1px solid var(--s-link)', background: 'var(--s-surface)', color: 'var(--s-link)', cursor: 'pointer', fontSize: 13 } as const;
const btnPrimary = { ...btn, background: 'var(--s-primary)', color: '#fff' } as const;

type Scope = 'esperando' | 'decididas' | 'todas';
type KindFilter = 'todas' | DecisionKind;

const SCOPES: Array<{ id: Scope; label: string }> = [
  { id: 'esperando', label: 'Esperando' },
  { id: 'decididas', label: 'Decididas' },
  { id: 'todas', label: 'Todas' },
];

const KINDS: Array<{ id: KindFilter; label: string }> = [
  { id: 'todas', label: 'Todos os tipos' },
  { id: 'pedido', label: 'Pedidos' },
  { id: 'regra', label: 'Regras' },
  { id: 'sugestao', label: 'Sugestões' },
];

const inScope = (d: IDecision, scope: Scope): boolean => scope === 'todas' || (scope === 'esperando' ? isWaiting(d) : !isWaiting(d));

function WaitingRow({ d, busy, onPress }: { d: IDecision; busy: boolean; onPress: (b: IDecisionButton) => void }) {
  return (
    <>
      <Pill tone={d.kind === 'pedido' ? 'warn' : 'idle'}>{KIND_LABEL[d.kind]}</Pill>
      {d.primary && <button type="button" style={btnPrimary} disabled={busy} onClick={() => onPress(d.primary as IDecisionButton)}>{d.primary.label}</button>}
      {d.secondary && <button type="button" style={btn} disabled={busy} onClick={() => onPress(d.secondary as IDecisionButton)}>{d.secondary.label}</button>}
    </>
  );
}

function DoneRow({ d, busy, onPress }: { d: IDecision; busy: boolean; onPress: (b: IDecisionButton) => void }) {
  const result = d.result;
  if (!result) return null;
  const tone: StateTone = result.tone;
  return (
    <div style={{ display: 'grid', gap: 4, justifyItems: 'end', textAlign: 'right' }}>
      <Pill tone={tone}>{result.label}</Pill>
      <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{result.at ? startLabel(result.at) : 'sem hora registrada'}</span>
      {result.undo ? (
        <button type="button" style={btn} disabled={busy} onClick={() => onPress(result.undo as IDecisionButton)}>{result.undo.label}</button>
      ) : (
        <span style={{ fontSize: 12, color: 'var(--s-text-3)', maxWidth: 260 }}>{result.undoBlocked}</span>
      )}
    </div>
  );
}

function DecisionRow({ d, busy, onPress }: { d: IDecision; busy: boolean; onPress: (b: IDecisionButton) => void }) {
  const done = !isWaiting(d);
  return (
    <li aria-label={`${KIND_LABEL[d.kind]}: ${d.title}`} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', padding: '12px 0', borderTop: '1px solid var(--s-border)' }}>
      <div style={{ flex: 1, minWidth: 240 }}>
        <div style={{ fontWeight: 600, color: done ? 'var(--s-text-3)' : undefined }}>{d.title}</div>
        <div style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
          {KIND_LABEL[d.kind]} · {d.systemName}
          {d.detail ? ` · ${d.detail}` : ''}
        </div>
      </div>
      {done ? <DoneRow d={d} busy={busy} onPress={onPress} /> : <WaitingRow d={d} busy={busy} onPress={onPress} />}
    </li>
  );
}

const EMPTY: Record<Scope, string> = {
  esperando: 'Nada esperando você. O Claude avisa aqui quando precisar de uma decisão. O que você já decidiu está em Decididas.',
  decididas: 'Nada decidido ainda. Quando você decidir algo, o item fica aqui, com o resultado.',
  todas: 'Nada por aqui ainda.',
};

/** O aviso lido por leitores de tela e visto por todos: o item sumiu de Esperando, e ele diz para onde foi. */
function noticeFor(d: IDecision, button: IDecisionButton): string {
  const undid = d.result?.undo === button;
  return undid
    ? `${button.label}: ${d.title}. O item voltou para o que está esperando você.`
    : `${button.label}: ${d.title}. O item continua em Decididas, com o resultado.`;
}

function Filters({ scope, kind, count, onScope, onKind }: { scope: Scope; kind: KindFilter; count: (s: Scope) => number; onScope: (s: Scope) => void; onKind: (k: KindFilter) => void }) {
  return (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
      <div role="group" aria-label="Mostrar decisões" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {SCOPES.map((s) => (
          <button key={s.id} type="button" aria-pressed={scope === s.id} onClick={() => onScope(s.id)} style={{ ...btn, background: scope === s.id ? 'var(--s-selected)' : 'var(--s-surface)' }}>
            {s.label} ({count(s.id)})
          </button>
        ))}
      </div>
      <label style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, color: 'var(--s-text-3)' }}>
        Tipo
        <select value={kind} onChange={(e) => onKind(e.target.value as KindFilter)} style={{ height: 32, padding: '0 8px', border: 0, borderBottom: '2px solid var(--s-link)', background: 'var(--s-layer)', fontSize: 13 }}>
          {KINDS.map((k) => (
            <option key={k.id} value={k.id}>{k.label}</option>
          ))}
        </select>
      </label>
    </div>
  );
}

interface IProps {
  items: IDecision[];
  loading: boolean;
  systems: ISystemSummaryResponse[];
  initialSystemId?: number;
}

/**
 * A caixa de decisões: tudo o que o Claude deixou esperando uma pessoa, de todos os sistemas, e o que já foi decidido.
 * Decidir não faz o item sumir: ele vai para Decididas, com o resultado, e dá para desfazer quando o Sentinel permite.
 * Embaixo fica a revisão completa de um sistema (funil, evidências e métricas).
 */
export function ClaudeDecisions({ items, loading, systems, initialSystemId }: IProps) {
  const [scope, setScope] = useState<Scope>('esperando');
  const [kind, setKind] = useState<KindFilter>('todas');
  const [notice, setNotice] = useState('');
  const decide = useDecide();
  const ofKind = items.filter((d) => kind === 'todas' || d.kind === kind);
  const shown = ofKind.filter((d) => inScope(d, scope));
  const press = (d: IDecision) => (b: IDecisionButton) => {
    setNotice('');
    decide.mutate(b.action, { onSuccess: () => setNotice(noticeFor(d, b)) });
  };
  return (
    <section aria-label="Decisões do Claude" style={{ display: 'grid', gap: 12 }}>
      <p style={{ margin: 0, fontSize: 14, color: 'var(--s-text-3)' }}>
        Pedidos do Claude (nada acontece até você aprovar), regras no funil e sugestões abertas. O que você decide continua aqui, em Decididas, e dá para
        desfazer quando o Sentinel permite. Nada daqui entra na nota até uma regra ser promovida por você.
      </p>
      <Filters scope={scope} kind={kind} count={(s) => ofKind.filter((d) => inScope(d, s)).length} onScope={setScope} onKind={setKind} />
      <div role="status" aria-live="polite" style={{ fontSize: 13, color: 'var(--s-text-2)', minHeight: 18 }}>{notice}</div>
      {decide.isError && <InlineNotification kind="error" title="Não foi possível" subtitle={(decide.error as Error).message} hideCloseButton />}
      {loading && items.length === 0 && <div style={emptyStyle}>Carregando…</div>}
      {!loading && shown.length === 0 && <div style={emptyStyle}>{EMPTY[scope]}</div>}
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {shown.map((d) => (
          <DecisionRow key={d.key} d={d} busy={decide.isPending} onPress={press(d)} />
        ))}
      </ul>
      <h2 style={{ margin: '16px 0 0', paddingTop: 16, borderTop: '1px solid var(--s-border-strong)', fontSize: 20, fontWeight: 400 }}>Revisão completa</h2>
      {systems.length > 0 && <ClaudeReviewPanel key={initialSystemId ?? 'primeiro'} systems={systems} initialSystemId={initialSystemId} />}
    </section>
  );
}
