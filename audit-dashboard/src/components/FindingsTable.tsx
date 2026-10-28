import { Button } from '@carbon/react';
import React, { useState } from 'react';
import type { FindingResponse, FindingStatus } from '../types';
import { originLabel } from '../utils/findingOrigin';
import { severityPill } from '../utils/findingsView';
import { RemediationBlock } from './RemediationBlock';
import { SuppressModal } from './SuppressModal';

/** De onde vem o achado, quando a lista mistura vários sistemas. */
export interface FindingOrigin {
  name: string;
  stack?: string | null;
  auditId?: number;
  onClick?: () => void;
}

interface FindingsTableProps {
  findings: FindingResponse[];
  /** Sistema ao qual a auditoria pertence; mostrado em cada linha para não haver dúvida de onde vem o achado. */
  systemName?: string;
  /** Auditoria dos achados, quando a lista é de uma só. */
  auditId?: number;
  /** Sistema de cada achado, quando a lista mistura vários sistemas. Tem prioridade sobre systemName. */
  systemOf?: (finding: FindingResponse) => FindingOrigin | undefined;
  /** Sugestão aberta do Claude sobre o achado, se houver; vira um selo na linha. */
  claudeNoteOf?: (finding: FindingResponse) => { title: string } | undefined;
}

const statusLabels: Record<FindingStatus, string> = {
  OPEN: 'Aberto',
  ACKNOWLEDGED: 'Reconhecido',
  FIXED: 'Resolvido',
  SUPPRESSED: 'Suprimido',
};

const statusColors: Record<FindingStatus, string> = {
  OPEN: 'var(--s-text-2)',
  ACKNOWLEDGED: 'var(--s-text-2)',
  FIXED: 'var(--s-success-text)',
  SUPPRESSED: 'var(--s-purple-text)',
};

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const th = { padding: '12px 8px' } as const;
const td = { padding: '10px 8px', verticalAlign: 'top' } as const;

const badgeBase = { fontSize: 11, borderRadius: 8, padding: '0 6px' } as const;

function Badge({ title, color, border, marginRight = 4, children }: { title: string; color: string; border: string; marginRight?: number; children: React.ReactNode }) {
  return (
    <span title={title} style={{ ...badgeBase, border: `1px solid ${border}`, color, marginRight }}>
      {children}
    </span>
  );
}

/** Selos do achado: sugestão do Claude, confiança e modo sombra. */
function FindingBadges({ f, claudeNote }: { f: FindingResponse; claudeNote: { title: string } | undefined }) {
  return (
    <>
      {claudeNote && (
        <Badge title={`Sugestão do Claude: ${claudeNote.title}. Veja na aba Revisão do Claude.`} color="var(--s-link)" border="var(--s-link)">
          Claude sugeriu
        </Badge>
      )}
      {f.confidence === 'LOW' && (
        <Badge title={f.confidenceReason ?? 'Baixa confiança'} color="var(--s-text-3)" border="var(--s-border-strong)">
          Baixa confiança
        </Badge>
      )}
      {f.confidence === 'HIGH' && f.confidenceReason && (
        <Badge title={f.confidenceReason} color="#198038" border="#198038">
          Confirmado
        </Badge>
      )}
      {f.shadow === true && (
        <Badge title="Regra em modo sombra: aparece, mas não conta no score nem derruba o pipeline" color="var(--s-text-3)" border="var(--s-border-strong)" marginRight={0}>
          Sombra
        </Badge>
      )}
    </>
  );
}

function SystemCell({ origin, name }: { origin: FindingOrigin | undefined; name: string }) {
  return (
    <td style={{ ...td, minWidth: 0 }}>
      {origin?.onClick ? (
        <button type="button" onClick={origin.onClick} style={{ background: 'none', border: 0, padding: 0, color: 'var(--s-link)', cursor: 'pointer', fontSize: 'inherit', fontWeight: 500, textAlign: 'left' }}>
          {name}
        </button>
      ) : (
        <span style={{ fontWeight: 500 }}>{name}</span>
      )}
      {origin?.stack && <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{origin.stack}</div>}
    </td>
  );
}

/** Detalhe aberto da linha: descrição, trecho, identificador, correção e a saída "falso positivo". */
function FindingDetail({ f, onSuppress }: { f: FindingResponse; onSuppress: () => void }) {
  return (
    <tr style={{ borderBottom: '1px solid var(--s-border)', background: 'var(--s-layer)' }}>
      <td colSpan={7} style={{ padding: '1rem 1rem 1rem 3.5rem' }}>
        <p>
          <strong>Descrição:</strong> {f.description}
        </p>
        {f.codeSnippet && (
          <pre style={{ background: '#161616', color: '#f4f4f4', padding: '1rem', overflow: 'auto', fontSize: '0.8125rem', marginTop: '0.5rem' }}>
            <code>{f.codeSnippet}</code>
          </pre>
        )}
        {f.cveId && (
          <p style={{ marginTop: '0.5rem' }}>
            <strong>Identificador:</strong> <code>{f.cveId}</code>
          </p>
        )}
        {f.remediation && <RemediationBlock remediation={f.remediation} />}
        {f.aiRecommendation && (
          <div style={{ marginTop: '0.5rem', padding: '0.75rem', background: '#e8f5e9', borderLeft: '4px solid #24a148' }}>
            <strong>Recomendação da IA:</strong>
            <p style={{ marginTop: '0.25rem' }}>{f.aiRecommendation}</p>
          </div>
        )}
        {f.status !== 'SUPPRESSED' && (
          <div style={{ marginTop: '0.75rem' }}>
            <Button kind="tertiary" size="sm" onClick={onSuppress}>
              Falso positivo / aceitar risco…
            </Button>
          </div>
        )}
      </td>
    </tr>
  );
}

interface RowProps {
  f: FindingResponse;
  origin: FindingOrigin | undefined;
  systemName: string;
  auditId: number | undefined;
  claudeNote: { title: string } | undefined;
  expanded: boolean;
  selected: boolean;
  onSelect: (on: boolean) => void;
  onToggleOpen: () => void;
  onSuppress: () => void;
}

function FindingRow({ f, origin, systemName, auditId, claudeNote, expanded, selected, onSelect, onToggleOpen, onSuppress }: RowProps) {
  const pill = severityPill[f.severity];
  const situation = f.isNew === true && f.status === 'OPEN' ? 'Novo' : statusLabels[f.status];
  return (
    <>
      <tr style={{ borderBottom: expanded ? 'none' : '1px solid var(--s-border)' }}>
        <td style={{ ...td, paddingLeft: 16 }}>
          {f.status !== 'SUPPRESSED' && (
            <input
              type="checkbox"
              aria-label={`Selecionar ${f.title}`}
              checked={selected}
              onChange={(e) => onSelect(e.target.checked)}
              style={{ width: 16, height: 16, accentColor: '#0f62fe' }}
            />
          )}
        </td>
        <td style={td}>
          <span style={{ display: 'inline-block', background: pill.bg, color: pill.fg, padding: '2px 8px', borderRadius: 10, fontSize: 12, fontWeight: 500 }}>
            {pill.label}
          </span>
        </td>
        <SystemCell origin={origin} name={systemName} />
        <td style={td}>
          <button
            type="button"
            aria-expanded={expanded}
            aria-label={`Expandir ${f.title}`}
            onClick={onToggleOpen}
            style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', textAlign: 'left', fontSize: 'inherit', fontWeight: 500, color: 'var(--s-text)' }}
          >
            {f.title}
          </button>{' '}
          <FindingBadges f={f} claudeNote={claudeNote} />
          <div style={{ fontSize: 12, color: 'var(--s-text-3)', fontFamily: mono }}>
            <span style={{ fontFamily: 'inherit', fontWeight: 600 }}>{originLabel(f.toolSource)}</span>
            {f.ruleId ? ` · ${f.ruleId}` : ''}
          </div>
        </td>
        <td style={{ ...td, fontFamily: mono, fontSize: 12, color: 'var(--s-text-2)', wordBreak: 'break-all' }}>
          {f.filePath}
          {f.lineNumber ? `:${f.lineNumber}` : ''}
        </td>
        <td style={{ ...td, fontSize: 13, color: 'var(--s-text-2)' }}>{auditId ? `Auditoria #${auditId}` : '—'}</td>
        <td style={{ ...td, paddingRight: 16, textAlign: 'right' }}>
          <span style={{ fontSize: 12, fontWeight: 500, color: situation === 'Novo' ? 'var(--s-link)' : statusColors[f.status] }}>{situation}</span>
        </td>
      </tr>
      {expanded && <FindingDetail f={f} onSuppress={onSuppress} />}
    </>
  );
}

function BatchBar({ count, onSuppress, onClear }: { count: number; onSuppress: () => void; onClear: () => void }) {
  if (count === 0) return null;
  return (
    <div role="region" aria-label="Ações em lote" style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '0.5rem 1rem', background: 'var(--s-selected)', borderBottom: '1px solid var(--s-border)' }}>
      <strong>{count} selecionado(s)</strong>
      <Button kind="primary" size="sm" onClick={onSuppress}>
        Suprimir selecionados…
      </Button>
      <Button kind="ghost" size="sm" onClick={onClear}>
        Limpar seleção
      </Button>
    </div>
  );
}

function toggledIn(set: Set<number>, id: number, on: boolean): Set<number> {
  const next = new Set(set);
  if (on) next.add(id);
  else next.delete(id);
  return next;
}

/** Lista de achados: severidade, sistema, achado, local, origem e situação; cada linha abre o detalhe. */
export function FindingsTable({ findings, systemName, auditId, systemOf, claudeNoteOf }: FindingsTableProps) {
  const [toSuppress, setToSuppress] = useState<FindingResponse[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [open, setOpen] = useState<Set<number>>(new Set());
  const selectable = findings.filter((f) => f.status !== 'SUPPRESSED');
  const selectedFindings = selectable.filter((f) => selected.has(f.id));
  const allSelected = selectable.length > 0 && selectedFindings.length === selectable.length;

  return (
    <>
      <BatchBar count={selectedFindings.length} onSuppress={() => setToSuppress(selectedFindings)} onClear={() => setSelected(new Set())} />
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 980 }}>
          <thead>
            <tr style={{ background: 'var(--s-header-row)', textAlign: 'left', fontSize: 12, fontWeight: 600 }}>
              <th style={{ ...th, width: 40, paddingLeft: 16 }}>
                <input
                  type="checkbox"
                  aria-label="Selecionar todos os findings desta lista"
                  checked={allSelected}
                  disabled={selectable.length === 0}
                  ref={(el) => {
                    if (el) el.indeterminate = selectedFindings.length > 0 && !allSelected;
                  }}
                  onChange={(e) => setSelected(e.target.checked ? new Set(selectable.map((f) => f.id)) : new Set())}
                  style={{ width: 16, height: 16, accentColor: '#0f62fe' }}
                />
              </th>
              <th style={{ ...th, width: 96 }}>Severidade</th>
              <th style={th}>Sistema</th>
              <th style={th}>Achado</th>
              <th style={th}>Local</th>
              <th style={th}>Origem</th>
              <th style={{ ...th, paddingRight: 16, textAlign: 'right' }}>Situação</th>
            </tr>
          </thead>
          <tbody>
            {findings.map((f) => {
              const origin = systemOf?.(f);
              return (
                <FindingRow
                  key={f.id}
                  f={f}
                  origin={origin}
                  systemName={origin?.name ?? systemName ?? '—'}
                  auditId={origin?.auditId ?? auditId}
                  claudeNote={claudeNoteOf?.(f)}
                  expanded={open.has(f.id)}
                  selected={selected.has(f.id)}
                  onSelect={(on) => setSelected(toggledIn(selected, f.id, on))}
                  onToggleOpen={() => setOpen(toggledIn(open, f.id, !open.has(f.id)))}
                  onSuppress={() => setToSuppress([f])}
                />
              );
            })}
          </tbody>
        </table>
      </div>
      <SuppressModal
        findings={toSuppress}
        candidates={findings}
        onClose={() => {
          setToSuppress([]);
          setSelected(new Set());
        }}
      />
    </>
  );
}
