import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { IClaudeSystemRow } from '../../hooks/useClaudeArea';
import type { IMcpCall } from '../../types';
import { HANDSHAKE_TOOL, REFUSAL_TOOL, refusalHelp } from '../../utils/mcpSetup';
import { startLabel } from '../../utils/timeAgo';
import { DataTable } from '../../components/DataTable';
import { emptyStyle, mono } from './ClaudeParts';

interface IActivityRow {
  key: string;
  systemId: number;
  systemName: string;
  call: IMcpCall;
}

/** As chamadas de todos os sistemas em uma lista só, da mais recente para a mais antiga. */
function activityRows(rows: IClaudeSystemRow[]): IActivityRow[] {
  return rows
    .flatMap((r) => r.calls.map((call, i) => ({ key: `${r.system.id}-${call.at}-${call.tool}-${i}`, systemId: r.system.id, systemName: r.system.name, call })))
    .sort((a, b) => Date.parse(b.call.at) - Date.parse(a.call.at));
}

function resultText(call: IMcpCall): string {
  if (call.tool === HANDSHAKE_TOOL) return 'conectou';
  if (call.ok) return `ok · ${call.durationMs} ms`;
  return call.detail?.split(' | ')[0] || 'recusada';
}

function WhatToDo({ row }: { row: IActivityRow }) {
  if (row.call.tool !== REFUSAL_TOOL) return null;
  const help = refusalHelp(row.call.detail ?? '');
  return (
    <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
      {help.fix} <Link to={`/claude?aba=conectar&systemId=${row.systemId}`}>Abrir os passos</Link>
    </div>
  );
}

/** Registro de tudo que o Claude fez ou tentou fazer, com o motivo de cada recusa e o que corrigir. */
export function ClaudeActivity({ rows }: { rows: IClaudeSystemRow[] }) {
  const [systemId, setSystemId] = useState('');
  const [onlyRefused, setOnlyRefused] = useState(false);
  const all = activityRows(rows);
  const shown = all.filter((r) => (systemId === '' || String(r.systemId) === systemId) && (!onlyRefused || !r.call.ok));
  return (
    <section aria-label="Atividade do Claude" style={{ display: 'grid', gap: 12 }}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
        <label style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)' }}>
          Sistema
          <select value={systemId} onChange={(e) => setSystemId(e.target.value)} style={{ height: 36, padding: '0 8px', border: 0, borderBottom: '2px solid var(--s-link)', background: 'var(--s-layer)', fontSize: 14 }}>
            <option value="">Todos os sistemas</option>
            {rows.map((r) => (
              <option key={r.system.id} value={r.system.id}>{r.system.name}</option>
            ))}
          </select>
        </label>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 14 }}>
          <input type="checkbox" checked={onlyRefused} onChange={(e) => setOnlyRefused(e.target.checked)} />
          Só recusadas
        </label>
      </div>
      {shown.length === 0 ? (
        <div style={emptyStyle}>Nenhuma chamada{onlyRefused ? ' recusada' : ''} ainda.</div>
      ) : (
        <DataTable label="Chamadas do Claude em todos os sistemas" fontSize={13} columns={[{ label: 'Quando' }, { label: 'Sistema' }, { label: 'O que' }, { label: 'Resultado' }]}>
          {shown.map((r) => (
            <tr key={r.key} style={{ borderTop: '1px solid var(--s-border)' }}>
              <td style={{ padding: '6px 8px' }}>{startLabel(r.call.at)}</td>
              <td style={{ padding: '6px 8px' }}>{r.systemName}</td>
              <td style={{ padding: '6px 8px', fontFamily: mono }}>{r.call.tool}</td>
              <td style={{ padding: '6px 8px', color: r.call.ok ? 'var(--s-success-text)' : 'var(--s-danger-text)' }}>
                {resultText(r.call)}
                <WhatToDo row={r} />
              </td>
            </tr>
          ))}
        </DataTable>
      )}
    </section>
  );
}
