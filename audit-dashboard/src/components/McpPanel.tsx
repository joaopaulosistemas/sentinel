import { Checkbox, InlineNotification } from '@carbon/react';
import { useState } from 'react';
import { useMcpCalls, useMcpConfig } from '../hooks/useMcpConfig';
import { startLabel } from '../utils/timeAgo';
import type { IMcpConfig, IMcpConfigRequest, McpDataLevel, IMcpNewKey, ISystemSummaryResponse } from '../types';
import { serverName } from '../utils/mcpSetup';
import { McpSetupSteps } from './McpSetupSteps';
import { LoadError } from './EngineRulesParts';
import { DataTable } from './DataTable';

const mono = "'IBM Plex Mono', ui-monospace, monospace";

const LEVELS: Array<{ id: McpDataLevel; titulo: string; texto: string }> = [
  { id: 'TITLES', titulo: 'Só números e títulos (recomendado)', texto: 'O Claude vê nota, contagens e o título de cada problema. Nenhum arquivo, linha ou trecho de código.' },
  { id: 'FILE_LINE', titulo: 'Arquivo e linha', texto: 'Acrescenta onde está cada problema. O trecho de código continua sem sair.' },
  { id: 'SENSITIVE', titulo: 'Sensível: só números', texto: 'Contagens por regra e severidade, sem títulos nem locais. Para sistemas que não podem expor nada.' },
];

interface IToolToggle {
  name: string;
  on: boolean;
}

/** O registro guarda "erro | campo=valor"; chamadas que deram certo guardam só os parâmetros. */
function callParams(c: { ok: boolean; detail?: string | null }): string {
  if (!c.detail) return '';
  if (!c.ok) {
    const i = c.detail.indexOf(' | ');
    return i >= 0 ? c.detail.slice(i + 3) : '';
  }
  return c.detail;
}


type UpdateMutation = { isPending: boolean; mutate: (body: IMcpConfigRequest) => void };

function LevelsFieldset({ cfg, update }: { cfg: IMcpConfig; update: UpdateMutation }) {
  return (
    <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'grid', gap: 8 }}>
      <legend style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>O que o Claude pode ver</legend>
      {LEVELS.map((l) => (
        <label key={l.id} style={{ display: 'flex', gap: 10, alignItems: 'flex-start', cursor: 'pointer' }}>
          <input
            type="radio"
            name="mcp-level"
            checked={cfg.dataLevel === l.id}
            disabled={update.isPending}
            onChange={() => update.mutate({ dataLevel: l.id })}
            style={{ marginTop: 4 }}
          />
          <span>
            <span style={{ fontWeight: 500 }}>{l.titulo}</span>
            <br />
            <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{l.texto}</span>
          </span>
        </label>
      ))}
      <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--s-text-3)' }}>
        O trecho de código nunca sai, em nenhum nível. Quem usa o Claude Code já lê a pasta do projeto no próprio computador:
        este controle vale para o que o Sentinel entrega.
      </p>
    </fieldset>
  );
}

function ToolsFieldset({ cfg, update, onToggle }: { cfg: IMcpConfig; update: UpdateMutation; onToggle: (toggle: IToolToggle) => void }) {
  return (
    <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'grid', gap: 8 }}>
      <legend style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Ferramentas liberadas</legend>
      {cfg.tools.map((t) => (
        <Checkbox
          key={t.name}
          id={`mcp-tool-${t.name}`}
          labelText={`${t.name} · ${t.writes ? 'escreve' : 'leitura'}`}
          helperText={t.description}
          checked={t.enabled}
          disabled={update.isPending}
          onChange={(_, { checked }) => onToggle({ name: t.name, on: checked })}
        />
      ))}
    </fieldset>
  );
}

type CallRow = NonNullable<ReturnType<typeof useCalls>['data']>[number];
const useCalls = useMcpCalls;

function callResult(c: CallRow): string {
  if (c.ok) return 'ok';
  return c.detail?.split(' | ')[0] || 'recusada';
}

function CallsSection({ calls }: { calls: CallRow[] | undefined }) {
  return (
    <section aria-label="Últimas chamadas" style={{ display: 'grid', gap: 6 }}>
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Últimas chamadas</h3>
      {calls && calls.length > 0 ? (
        <DataTable label="Consultas recentes do Claude via MCP" fontSize={12} columns={[{ label: 'Quando' }, { label: 'Ferramenta' }, { label: 'Resultado' }, { label: 'Parâmetros' }, { label: 'Tempo' }]}>
          {calls.map((c) => (
                <tr key={`${c.at}-${c.tool}`} style={{ borderTop: '1px solid var(--s-border)' }}>
                  <td style={{ padding: '6px 8px' }}>{startLabel(c.at)}</td>
                  <td style={{ padding: '6px 8px', fontFamily: mono }}>{c.tool}</td>
                  <td style={{ padding: '6px 8px', color: c.ok ? 'var(--s-success-text)' : 'var(--s-danger-text)' }}>{callResult(c)}</td>
                  <td style={{ padding: '6px 8px', fontFamily: mono, wordBreak: 'break-word' }}>{callParams(c)}</td>
                  <td style={{ padding: '6px 8px' }}>{c.durationMs} ms</td>
                </tr>
              ))}
        </DataTable>
      ) : (
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Nenhuma chamada ainda.</span>
      )}
    </section>
  );
}

/**
 * Aba "Claude (MCP)" do cadastro de um sistema. Tudo aqui vale só para este sistema e salva na hora:
 * liga a consulta, gera a chave, escolhe quanto do resultado pode sair e quais ferramentas ficam liberadas.
 */
export function McpPanel({ system }: { system: ISystemSummaryResponse }) {
  const calls = useCalls(system.id);
  // A chave completa só existe logo depois de gerada; ao fechar o painel ela some.
  const [fresh, setFresh] = useState<IMcpNewKey | null>(null);
  const [confirmNew, setConfirmNew] = useState(false);
  const { config, update, issue, revoke } = useMcpConfig(system.id, {
    onIssued: (data) => {
      setFresh(data);
      setConfirmNew(false);
    },
    onRevoked: () => setFresh(null),
  });

  if (config.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (config.isError || !config.data) return <LoadError error={config.error} />;

  const cfg = config.data;
  // O número do sistema vale sempre; o nome falha se tiver acento, espaço ou se outro sistema tiver o mesmo.
  const url = `${window.location.origin}/mcp/${system.id}`;
  const enabledTools = cfg.tools.filter((t) => t.enabled).map((t) => t.name);
  const toggleTool = ({ name, on }: IToolToggle) =>
    update.mutate({ tools: on ? [...enabledTools, name] : enabledTools.filter((n) => n !== name) });
  const error = (update.error ?? issue.error ?? revoke.error) as Error | null;

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      {error && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={error.message} hideCloseButton />}

      <McpSetupSteps
        cfg={cfg}
        url={url}
        name={serverName(system.name, system.id)}
        calls={calls.data}
        fresh={fresh}
        confirmNew={confirmNew}
        issuing={issue.isPending}
        revoking={revoke.isPending}
        updating={update.isPending}
        onIssue={() => issue.mutate()}
        onRevoke={() => revoke.mutate()}
        onConfirmNew={setConfirmNew}
        onDismissFresh={() => setFresh(null)}
        onToggleEnabled={(on) => update.mutate({ enabled: on })}
      />

      <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Ajustes</h3>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 13 }}>
        As ferramentas de leitura vêm ligadas; as que escrevem (registrar_sugestao e propor_regra) ficam desligadas até você
        liberar, e só gravam na aba Revisão do Claude. A nota continua sendo calculada apenas pelo motor de regras: o que o
        Claude sugerir não entra nela.
      </p>
      <LevelsFieldset cfg={cfg} update={update} />
      <ToolsFieldset cfg={cfg} update={update} onToggle={toggleTool} />
      <CallsSection calls={calls.data} />
    </div>
  );
}
