import { Link } from 'react-router-dom';
import type { IClaudeSystemRow } from '../../hooks/useClaudeArea';
import { describeState } from '../../utils/mcpSetup';
import { Pill, Tile, emptyStyle } from './ClaudeParts';

/** Primeira aba da área: quantos sistemas já estão conectados, o que espera uma decisão e a situação de cada sistema. */
export function ClaudeOverview({ rows, decisions, loading }: { rows: IClaudeSystemRow[]; decisions: number; loading: boolean }) {
  const states = rows.map((r) => (r.state ? describeState(r.state) : null));
  const connected = states.filter((s) => s?.tone === 'good').length;
  const refusing = states.filter((s) => s?.tone === 'bad').length;
  return (
    <section aria-label="Visão geral do Claude (MCP)" style={{ display: 'grid', gap: 16 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 12 }}>
        <Tile label="Sistemas conectados" value={`${connected} de ${rows.length}`} tone={connected > 0 ? 'good' : undefined} />
        <Tile label="Decisões esperando" value={decisions} tone={decisions > 0 ? 'warn' : undefined} />
        <Tile label="Recusando chamadas" value={refusing} tone={refusing > 0 ? 'bad' : undefined} />
      </div>
      <h2 style={{ margin: 0, fontSize: 18, fontWeight: 400 }}>Seus sistemas</h2>
      {rows.length === 0 && !loading && <div style={emptyStyle}>Nenhum sistema cadastrado. Cadastre um em Sistemas para conectar o Claude.</div>}
      {loading && rows.length === 0 && <div style={emptyStyle}>Carregando…</div>}
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {rows.map((r, i) => {
          const view = states[i];
          return (
            <li key={r.system.id} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', padding: '12px 0', borderTop: '1px solid var(--s-border)' }}>
              <div style={{ flex: 1, minWidth: 200 }}>
                <div style={{ fontWeight: 600 }}>{r.system.name}</div>
                <div style={{ fontSize: 13, color: 'var(--s-text-3)' }}>{view ? view.detail : 'Carregando…'}</div>
              </div>
              {view && <Pill tone={view.tone}>{view.label}</Pill>}
              {view && <Link to={`/claude?aba=${view.tab}&systemId=${r.system.id}`}>{view.action}</Link>}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
