import { Link, useSearchParams } from 'react-router-dom';
import { McpAdjustPanel, McpConnectPanel } from '../../components/McpPanel';
import { PageShell } from '../../components/PageShell';
import { useClaudeSystems, useDecisions } from '../../hooks/useClaudeArea';
import { useSystems } from '../../hooks/useSystems';
import { ClaudeActivity } from './ClaudeActivity';
import { ClaudeDecisions } from './ClaudeDecisions';
import { ClaudeOverview } from './ClaudeOverview';
import { SystemPicker, emptyStyle } from './ClaudeParts';

const ABAS = [
  { id: 'visao', nome: 'Visão geral' },
  { id: 'conectar', nome: 'Conectar' },
  { id: 'decisoes', nome: 'Decisões' },
  { id: 'atividade', nome: 'Atividade' },
  { id: 'permissoes', nome: 'Permissões' },
] as const;

type AbaId = (typeof ABAS)[number]['id'];

const isAba = (v: string | null): v is AbaId => ABAS.some((a) => a.id === v);

function TabBar({ aba, decisions, onSelect }: { aba: AbaId; decisions: number; onSelect: (id: AbaId) => void }) {
  return (
    <div role="tablist" aria-label="Seções do Claude (MCP)" style={{ display: 'flex', borderBottom: '1px solid var(--s-border-subtle)', flexWrap: 'wrap' }}>
      {ABAS.map((a) => (
        <button
          key={a.id}
          type="button"
          role="tab"
          id={`claude-aba-${a.id}`}
          aria-selected={a.id === aba}
          onClick={() => onSelect(a.id)}
          style={{ background: 'none', border: 0, borderBottom: a.id === aba ? '3px solid var(--s-link)' : '3px solid transparent', padding: '10px 16px', fontSize: 14, fontWeight: a.id === aba ? 600 : 400, color: 'var(--s-text)', cursor: 'pointer' }}
        >
          {a.nome}
          {a.id === 'decisoes' && decisions > 0 && <span aria-label={`${decisions} esperando`}> ({decisions})</span>}
        </button>
      ))}
    </div>
  );
}

/**
 * Área Claude (MCP): tudo sobre o Claude Code consultando o Sentinel em um lugar só, na ordem em que se usa:
 * ver a situação, conectar um sistema, decidir o que o Claude deixou esperando, acompanhar a atividade e ajustar as permissões.
 */
export function ClaudePage() {
  const [params, setParams] = useSearchParams();
  const aba: AbaId = isAba(params.get('aba')) ? (params.get('aba') as AbaId) : 'visao';
  const { data: systems } = useSystems();
  const { rows, loading } = useClaudeSystems();
  const decisions = useDecisions();
  const requested = Number(params.get('systemId') ?? '0');
  const system = (systems ?? []).find((s) => s.id === requested) ?? (systems ?? [])[0];

  const select = (next: AbaId) => {
    const nextParams = new URLSearchParams(params);
    nextParams.set('aba', next);
    setParams(nextParams);
  };
  const pickSystem = (id: number) => {
    const nextParams = new URLSearchParams(params);
    nextParams.set('systemId', String(id));
    setParams(nextParams);
  };

  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Claude (MCP)' }]}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>Claude (MCP)</h1>
        <span style={{ color: 'var(--s-text-3)' }}>
          Deixe o Claude Code consultar suas auditorias, sugerir melhorias e pedir ações, sempre com a sua aprovação. A nota continua sendo calculada só
          pelo motor de regras.
        </span>
      </div>
      <TabBar aba={aba} decisions={decisions.waiting} onSelect={select} />
      <div role="tabpanel" aria-labelledby={`claude-aba-${aba}`} style={{ display: 'grid', gap: 16 }}>
        {aba === 'visao' && <ClaudeOverview rows={rows} decisions={decisions.waiting} loading={loading} />}
        {aba === 'decisoes' && <ClaudeDecisions items={decisions.items} loading={decisions.loading} systems={systems ?? []} initialSystemId={system?.id} />}
        {aba === 'atividade' && <ClaudeActivity rows={rows} />}
        {(aba === 'conectar' || aba === 'permissoes') &&
          (system ? (
            <>
              <SystemPicker systems={systems ?? []} value={system.id} onChange={pickSystem} />
              {aba === 'conectar' ? <McpConnectPanel key={system.id} system={system} /> : <McpAdjustPanel key={system.id} system={system} />}
              {aba === 'permissoes' && (
                <p style={{ margin: 0, fontSize: 13, color: 'var(--s-text-3)' }}>
                  Gerar uma chave nova ou revogar a atual: <Link to={`/claude?aba=conectar&systemId=${system.id}`}>aba Conectar</Link>.
                </p>
              )}
            </>
          ) : (
            <div style={emptyStyle}>Nenhum sistema cadastrado. Cadastre um em Sistemas para conectar o Claude.</div>
          ))}
      </div>
    </PageShell>
  );
}
