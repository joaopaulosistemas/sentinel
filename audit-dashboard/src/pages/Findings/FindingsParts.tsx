import { DataTableSkeleton, InlineNotification } from '@carbon/react';
import { Link } from 'react-router-dom';
import { FindingsTable, type FindingOrigin } from '../../components/FindingsTable';
import type { FindingResponse, FindingsOverview } from '../../types';
import { groupBySystem } from '../../utils/findingsView';
import { startLabel } from '../../utils/timeAgo';
import { stackShort } from '../Systems/systemForm';
import { edgeColor } from './findingsRows';

type OverviewItem = FindingsOverview['items'][number];

const mono = "'IBM Plex Mono', ui-monospace, monospace";

export const ABAS = [
  { id: 'achados', nome: 'Achados e nota' },
  { id: 'revisao', nome: 'Revisão do Claude' },
  { id: 'reauditoria', nome: 'Reauditoria' },
] as const;

export type AbaId = (typeof ABAS)[number]['id'];

export function TabBar({ aba, onSelect }: { aba: AbaId; onSelect: (id: AbaId) => void }) {
  return (
    <div role="tablist" aria-label="Seções de Informações de Auditoria" style={{ display: 'flex', borderBottom: '1px solid var(--s-border-subtle)' }}>
      {ABAS.map((a) => (
        <button
          key={a.id}
          type="button"
          role="tab"
          id={`aba-${a.id}`}
          aria-selected={a.id === aba}
          onClick={() => onSelect(a.id)}
          style={{
            background: 'none',
            border: 0,
            borderBottom: a.id === aba ? '3px solid var(--s-link)' : '3px solid transparent',
            padding: '10px 16px',
            fontSize: 14,
            fontWeight: a.id === aba ? 600 : 400,
            color: 'var(--s-text)',
            cursor: 'pointer',
          }}
        >
          {a.nome}
        </button>
      ))}
    </div>
  );
}

export function PageHeading({ auditMode, auditId, systemName }: { auditMode: boolean; auditId: number; systemName: string | undefined }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>
        Informações de Auditoria
        {auditMode && (
          <span style={{ fontSize: 16, color: 'var(--s-text-4)', marginLeft: 12 }}>
            — {systemName ? `${systemName} · ` : ''}Auditoria #{auditId}
          </span>
        )}
      </h1>
      <span style={{ color: 'var(--s-text-3)' }}>
        {auditMode
          ? 'Achados desta auditoria.'
          : 'Da última auditoria concluída de cada sistema. Escolha um sistema para focar, ou veja todos juntos e saiba de onde cada achado vem.'}
      </span>
    </div>
  );
}

export function SystemSummaryCards({ summary }: { summary: FindingsOverview['bySystem'] }) {
  return (
    <section aria-label="Resumo por sistema" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(260px, 100%), 1fr))', gap: 12 }}>
      {summary.map((s) => (
        <Link
          key={s.systemId}
          to={`/systems/${s.systemId}`}
          style={{ textDecoration: 'none', color: 'inherit', background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: `4px solid ${edgeColor(s.critical ?? 0, s.high ?? 0)}`, padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}
        >
          <span style={{ fontWeight: 600, color: 'var(--s-text)' }}>{s.systemName}</span>
          <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
            Auditoria #{s.auditId}{s.auditedAt ? ` · ${startLabel(s.auditedAt)}` : ''}
          </span>
          <span style={{ display: 'flex', gap: 14, fontSize: 13, color: 'var(--s-text)' }}>
            <span><strong style={{ fontFamily: mono, color: 'var(--s-crit-text)' }}>{s.critical ?? 0}</strong> críticos</span>
            <span><strong style={{ fontFamily: mono, color: 'var(--s-high-text)' }}>{s.high ?? 0}</strong> altos</span>
          </span>
        </Link>
      ))}
    </section>
  );
}

/** Monta, para cada achado da visão geral, o sistema de onde ele vem (nome, stack e atalho para abrir o sistema). */
export function originFinder(
  itemByFinding: Map<number, OverviewItem>,
  stackByName: Map<number, string | null | undefined>,
  open: (item: OverviewItem) => void,
) {
  return (f: FindingResponse): FindingOrigin | undefined => {
    const hit = itemByFinding.get(f.id);
    if (!hit) return undefined;
    const stack = stackByName.get(hit.systemId);
    return {
      name: hit.systemName,
      stack: stack ? (stackShort[stack as keyof typeof stackShort] ?? stack) : undefined,
      auditId: hit.auditId,
      onClick: () => open(hit),
    };
  };
}

type OriginOf = ReturnType<typeof originFinder> | undefined;
type NoteOf = (f: FindingResponse) => ReturnType<typeof import('../../utils/claudeHint').suggestionFor>;

interface ListBodyProps {
  loading: boolean;
  error: unknown;
  total: number;
  auditMode: boolean;
  auditId: number;
  auditSystemName: string | undefined;
  grouped: boolean;
  rows: FindingResponse[];
  items: OverviewItem[];
  systemOf: OriginOf;
  claudeNoteOf: NoteOf;
}

function Padded({ children }: { children: React.ReactNode }) {
  return <div style={{ padding: 16 }}>{children}</div>;
}

/** Corpo da lista: carregando, erro, vazio, agrupado por sistema ou tabela única. */
export function FindingsListBody(p: ListBodyProps) {
  if (p.loading) return <Padded><DataTableSkeleton columnCount={6} rowCount={8} /></Padded>;
  if (p.error) {
    return (
      <Padded>
        <InlineNotification kind="error" title="Erro ao carregar findings" subtitle={p.error instanceof Error ? p.error.message : 'Erro desconhecido'} />
      </Padded>
    );
  }
  if (p.total === 0) {
    return (
      <Padded>
        <InlineNotification
          kind="info"
          title="Nenhum achado com esses filtros"
          subtitle={p.auditMode ? 'Tente remover algum filtro.' : 'Pode ser que nenhum sistema tenha auditoria concluída ainda, ou que os filtros sejam restritivos.'}
          hideCloseButton
        />
      </Padded>
    );
  }
  if (p.grouped && !p.auditMode) {
    return (
      <div style={{ display: 'grid', gap: 24, padding: '16px 0 0' }}>
        {groupBySystem(p.items).map((g) => (
          <section key={g.systemId} aria-label={`Achados de ${g.systemName}`}>
            <h2 style={{ fontSize: 16, fontWeight: 600, margin: '0 16px 8px' }}>
              {g.systemName}
              <span style={{ fontWeight: 400, color: 'var(--s-text-3)', marginLeft: 8 }}>
                {g.findings.length} {g.findings.length === 1 ? 'achado nesta página' : 'achados nesta página'}
              </span>
            </h2>
            <FindingsTable findings={g.findings} systemOf={p.systemOf} claudeNoteOf={p.claudeNoteOf} />
          </section>
        ))}
      </div>
    );
  }
  return (
    <FindingsTable
      findings={p.rows}
      systemName={p.auditMode ? p.auditSystemName : undefined}
      auditId={p.auditMode ? p.auditId : undefined}
      systemOf={p.systemOf}
      claudeNoteOf={p.claudeNoteOf}
    />
  );
}

interface ResolvedProps {
  resolved: FindingResponse[];
  open: boolean;
  onToggle: () => void;
  systemName: string | undefined;
  auditId: number;
  claudeNoteOf: NoteOf;
}

export function ResolvedSection({ resolved, open, onToggle, systemName, auditId, claudeNoteOf }: ResolvedProps) {
  return (
    <section aria-label="Resolvidos">
      <button
        type="button"
        aria-expanded={open}
        onClick={onToggle}
        style={{ height: 36, padding: '0 14px', background: 'var(--s-surface)', border: '1px solid var(--s-link)', color: 'var(--s-link)', cursor: 'pointer', fontSize: 13 }}
      >
        {open ? 'Ocultar' : 'Mostrar'} {resolved.length} {resolved.length === 1 ? 'achado resolvido' : 'achados resolvidos'} desde a auditoria anterior
      </button>
      {open && (
        <div style={{ marginTop: 16, background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
          <FindingsTable findings={resolved} systemName={systemName} auditId={auditId} claudeNoteOf={claudeNoteOf} />
        </div>
      )}
    </section>
  );
}
