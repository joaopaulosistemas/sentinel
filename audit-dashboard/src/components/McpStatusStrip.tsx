import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { McpCall } from '../types';
import { timeAgo } from '../utils/timeAgo';

const mono = "'IBM Plex Mono', ui-monospace, monospace";

export interface McpStatusStripProps {
  systemId: number;
  /** null enquanto a configuração do sistema não chegou: nada é mostrado até saber. */
  enabled: boolean | null;
  /** Chamadas do Claude desde o início desta auditoria, da mais recente para a mais antiga. */
  calls: McpCall[];
  /** Pedidos do Claude esperando aprovação. */
  pending: number;
}

/**
 * Faixa "Claude (MCP)" da tela de execução da auditoria: diz se o Claude pode consultar este sistema e se já consultou
 * durante esta execução. É só informação: o motor de regras continua sendo a única fonte da nota.
 */
export function McpStatusStrip({ systemId, enabled, calls, pending }: McpStatusStripProps) {
  if (enabled === null) return null;

  const last = calls[0];
  const refused = calls.filter((c) => !c.ok).length;
  const dot = (color: string) => (
    <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: '50%', background: color, display: 'inline-block' }} />
  );

  let summary: ReactNode;
  if (!enabled) {
    summary = <>{dot('#8d8d8d')} <strong style={{ fontWeight: 600 }}>Claude (MCP)</strong> · desligado para este sistema</>;
  } else if (!last) {
    summary = <>{dot('#198038')} <strong style={{ fontWeight: 600 }}>Claude (MCP)</strong> · ligado, sem consultas nesta auditoria</>;
  } else {
    summary = (
      <>
        {dot('#198038')} <strong style={{ fontWeight: 600 }}>Claude (MCP)</strong> · ligado · {calls.length}{' '}
        {calls.length === 1 ? 'consulta' : 'consultas'} nesta auditoria
        {refused > 0 && <> ({refused} recusada{refused === 1 ? '' : 's'})</>}
        {' · '}última {timeAgo(last.at)} <span style={{ fontFamily: mono, fontSize: 12 }}>{last.tool}</span>
      </>
    );
  }

  return (
    <section
      aria-label="Claude (MCP)"
      style={{ background: '#fff', border: '1px solid #e0e0e0', padding: '10px 16px', display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px 16px', fontSize: 13 }}
    >
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>{summary}</span>
      {pending > 0 && (
        <Link to={`/findings?aba=reauditoria&systemId=${systemId}`}>
          {pending === 1 ? '1 pedido do Claude aguarda aprovação' : `${pending} pedidos do Claude aguardam aprovação`}
        </Link>
      )}
      <Link to={`/systems?edit=${systemId}&tab=mcp`} style={{ marginLeft: 'auto' }}>
        {enabled ? 'Ver chamadas' : 'Configurar'}
      </Link>
      <span style={{ flexBasis: '100%', fontSize: 12, color: '#525252' }}>
        O que o Claude fizer não muda a nota desta auditoria: ela vem apenas do motor de regras.
      </span>
    </section>
  );
}
