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
}

const statusLabels: Record<FindingStatus, string> = {
  OPEN: 'Aberto',
  ACKNOWLEDGED: 'Reconhecido',
  FIXED: 'Resolvido',
  SUPPRESSED: 'Suprimido',
};

const statusColors: Record<FindingStatus, string> = {
  OPEN: '#393939',
  ACKNOWLEDGED: '#393939',
  FIXED: '#198038',
  SUPPRESSED: '#6929c4',
};

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const th = { padding: '12px 8px' } as const;
const td = { padding: '10px 8px', verticalAlign: 'top' } as const;

/** Lista de achados: severidade, sistema, achado, local, origem e situação; cada linha abre o detalhe. */
export function FindingsTable({ findings, systemName, auditId, systemOf }: FindingsTableProps) {
  const [toSuppress, setToSuppress] = useState<FindingResponse[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [open, setOpen] = useState<Set<number>>(new Set());
  const selectable = findings.filter((f) => f.status !== 'SUPPRESSED');
  const selectedFindings = selectable.filter((f) => selected.has(f.id));
  const allSelected = selectable.length > 0 && selectedFindings.length === selectable.length;

  const toggle = (id: number, on: boolean) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  const toggleOpen = (id: number) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <>
      {selectedFindings.length > 0 && (
        <div role="region" aria-label="Ações em lote" style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '0.5rem 1rem', background: 'var(--s-selected)', borderBottom: '1px solid var(--s-border)' }}>
          <strong>{selectedFindings.length} selecionado(s)</strong>
          <Button kind="primary" size="sm" onClick={() => setToSuppress(selectedFindings)}>
            Suprimir selecionados…
          </Button>
          <Button kind="ghost" size="sm" onClick={() => setSelected(new Set())}>
            Limpar seleção
          </Button>
        </div>
      )}
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
              const name = origin?.name ?? systemName ?? '—';
              const origem = origin?.auditId ?? auditId;
              const pill = severityPill[f.severity];
              const expanded = open.has(f.id);
              const situation = f.isNew === true && f.status === 'OPEN' ? 'Novo' : statusLabels[f.status];
              return (
                <React.Fragment key={f.id}>
                  <tr style={{ borderBottom: expanded ? 'none' : '1px solid var(--s-border)' }}>
                    <td style={{ ...td, paddingLeft: 16 }}>
                      {f.status !== 'SUPPRESSED' && (
                        <input
                          type="checkbox"
                          aria-label={`Selecionar ${f.title}`}
                          checked={selected.has(f.id)}
                          onChange={(e) => toggle(f.id, e.target.checked)}
                          style={{ width: 16, height: 16, accentColor: '#0f62fe' }}
                        />
                      )}
                    </td>
                    <td style={td}>
                      <span style={{ display: 'inline-block', background: pill.bg, color: pill.fg, padding: '2px 8px', borderRadius: 10, fontSize: 12, fontWeight: 500 }}>
                        {pill.label}
                      </span>
                    </td>
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
                    <td style={td}>
                      <button
                        type="button"
                        aria-expanded={expanded}
                        aria-label={`Expandir ${f.title}`}
                        onClick={() => toggleOpen(f.id)}
                        style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', textAlign: 'left', fontSize: 'inherit', fontWeight: 500, color: 'var(--s-text)' }}
                      >
                        {f.title}
                      </button>{' '}
                      {f.shadow === true && (
                        <span title="Regra em modo sombra: aparece, mas não conta no score nem derruba o pipeline" style={{ fontSize: 11, border: '1px solid var(--s-border-strong)', borderRadius: 8, padding: '0 6px', color: 'var(--s-text-3)' }}>
                          Sombra
                        </span>
                      )}
                      <div style={{ fontSize: 12, color: 'var(--s-text-3)', fontFamily: mono }}>
                        <span style={{ fontFamily: 'inherit', fontWeight: 600 }}>{originLabel(f.toolSource)}</span>
                        {f.ruleId ? ` · ${f.ruleId}` : ''}
                      </div>
                    </td>
                    <td style={{ ...td, fontFamily: mono, fontSize: 12, color: 'var(--s-text-2)', wordBreak: 'break-all' }}>
                      {f.filePath}
                      {f.lineNumber ? `:${f.lineNumber}` : ''}
                    </td>
                    <td style={{ ...td, fontSize: 13, color: 'var(--s-text-2)' }}>{origem ? `Auditoria #${origem}` : '—'}</td>
                    <td style={{ ...td, paddingRight: 16, textAlign: 'right' }}>
                      <span style={{ fontSize: 12, fontWeight: 500, color: situation === 'Novo' ? '#0043ce' : statusColors[f.status] }}>{situation}</span>
                    </td>
                  </tr>
                  {expanded && (
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
                            <Button kind="tertiary" size="sm" onClick={() => setToSuppress([f])}>
                              Falso positivo / aceitar risco…
                            </Button>
                          </div>
                        )}
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
      <SuppressModal
        findings={toSuppress}
        onClose={() => {
          setToSuppress([]);
          setSelected(new Set());
        }}
      />
    </>
  );
}
