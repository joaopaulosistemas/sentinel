import { Button, Checkbox, InlineNotification } from '@carbon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { startLabel } from '../utils/timeAgo';
import type { McpConfigRequest, McpDataLevel, McpNewKey, SystemSummaryResponse } from '../types';

const mono = "'IBM Plex Mono', ui-monospace, monospace";

const LEVELS: Array<{ id: McpDataLevel; titulo: string; texto: string }> = [
  { id: 'TITLES', titulo: 'Só números e títulos (recomendado)', texto: 'O Claude vê nota, contagens e o título de cada problema. Nenhum arquivo, linha ou trecho de código.' },
  { id: 'FILE_LINE', titulo: 'Arquivo e linha', texto: 'Acrescenta onde está cada problema. O trecho de código continua sem sair.' },
  { id: 'SENSITIVE', titulo: 'Sensível: só números', texto: 'Contagens por regra e severidade, sem títulos nem locais. Para sistemas que não podem expor nada.' },
];

/** Comando que registra este sistema no Claude Code. A chave é a que acabou de ser gerada. */
export function connectCommand(url: string, systemName: string, key: string): string {
  return `claude mcp add --transport http sentinel-${systemName} ${url} --header "Authorization: Bearer ${key}"`;
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    /* sem permissão para a área de transferência: a pessoa seleciona o texto à mão */
  }
}

/**
 * Aba "Claude (MCP)" do cadastro de um sistema. Tudo aqui vale só para este sistema e salva na hora:
 * liga a consulta, gera a chave, escolhe quanto do resultado pode sair e quais ferramentas ficam liberadas.
 */
export function McpPanel({ system }: { system: SystemSummaryResponse }) {
  const queryClient = useQueryClient();
  const key = ['mcp', system.id];
  const config = useQuery({ queryKey: key, queryFn: () => api.getMcp(system.id) });
  const calls = useQuery({ queryKey: [...key, 'calls'], queryFn: () => api.getMcpCalls(system.id), refetchOnWindowFocus: true });
  // A chave completa só existe logo depois de gerada; ao fechar o painel ela some.
  const [fresh, setFresh] = useState<McpNewKey | null>(null);
  const [confirmNew, setConfirmNew] = useState(false);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
  };
  const update = useMutation({
    mutationFn: (body: McpConfigRequest) => api.updateMcp(system.id, body),
    onSuccess: (data) => queryClient.setQueryData(key, data),
  });
  const issue = useMutation({
    mutationFn: () => api.newMcpKey(system.id),
    onSuccess: (data) => {
      setFresh(data);
      setConfirmNew(false);
      refresh();
    },
  });
  const revoke = useMutation({
    mutationFn: () => api.revokeMcpKey(system.id),
    onSuccess: () => {
      setFresh(null);
      refresh();
    },
  });

  if (config.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (config.isError || !config.data) {
    return (
      <div style={{ padding: 24 }}>
        <InlineNotification kind="error" title="Não foi possível carregar" subtitle={config.error instanceof Error ? config.error.message : 'Erro desconhecido'} hideCloseButton />
      </div>
    );
  }

  const cfg = config.data;
  const url = `${window.location.origin}/mcp/${encodeURIComponent(system.name)}`;
  const enabledTools = cfg.tools.filter((t) => t.enabled).map((t) => t.name);
  const toggleTool = (name: string, on: boolean) =>
    update.mutate({ tools: on ? [...enabledTools, name] : enabledTools.filter((n) => n !== name) });
  const error = (update.error ?? issue.error ?? revoke.error) as Error | null;

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: '#525252', fontSize: 14 }}>
        O Claude consulta o resultado deste sistema por MCP. As ferramentas de leitura vêm ligadas; as que escrevem
        (registrar_sugestao e propor_regra) ficam desligadas até você liberar, e só gravam na aba Revisão do Claude.
        A nota continua sendo calculada apenas pelo motor de regras: o que o Claude sugerir não entra nela.
      </p>

      {error && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={error.message} hideCloseButton />}

      <Checkbox
        id="mcp-enabled"
        labelText="Permitir consulta pelo Claude (MCP)"
        helperText={cfg.hasKey ? 'Desligado, qualquer chamada com a chave deste sistema é recusada.' : 'Gere a chave abaixo para poder ligar.'}
        checked={cfg.enabled}
        disabled={!cfg.hasKey || update.isPending}
        onChange={(_, { checked }) => update.mutate({ enabled: checked })}
      />

      <section aria-label="Endereço e chave" style={{ display: 'grid', gap: 10 }}>
        <div>
          <div style={{ fontSize: 12, color: '#525252' }}>Endereço deste sistema</div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <code style={{ fontFamily: mono, fontSize: 13, background: '#f4f4f4', padding: '4px 8px', wordBreak: 'break-all' }}>{url}</code>
            <Button kind="ghost" size="sm" onClick={() => copy(url)}>Copiar</Button>
          </div>
        </div>

        <div>
          <div style={{ fontSize: 12, color: '#525252' }}>Chave</div>
          {cfg.hasKey ? (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              <code style={{ fontFamily: mono, fontSize: 13 }}>{cfg.keyPrefix}…</code>
              <span style={{ fontSize: 12, color: '#525252' }}>criada em {startLabel(cfg.keyCreatedAt)}</span>
            </div>
          ) : (
            <span style={{ fontSize: 13, color: '#525252' }}>Nenhuma chave gerada.</span>
          )}
          <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
            {!cfg.hasKey || confirmNew ? (
              <Button kind="primary" size="sm" disabled={issue.isPending} onClick={() => issue.mutate()}>
                {confirmNew ? 'Confirmar: gerar e invalidar a anterior' : 'Gerar chave'}
              </Button>
            ) : (
              <Button kind="tertiary" size="sm" onClick={() => setConfirmNew(true)}>Gerar nova chave</Button>
            )}
            {confirmNew && <Button kind="ghost" size="sm" onClick={() => setConfirmNew(false)}>Cancelar</Button>}
            {cfg.hasKey && !confirmNew && (
              <Button kind="danger--tertiary" size="sm" disabled={revoke.isPending} onClick={() => revoke.mutate()}>
                Revogar e desligar
              </Button>
            )}
          </div>
        </div>

        {fresh && (
          <div role="status" aria-label="Chave nova" style={{ background: '#fcf4d6', borderLeft: '3px solid #f1c21b', padding: '12px 16px', display: 'grid', gap: 8 }}>
            <strong>Copie a chave agora: ela não aparece de novo.</strong>
            <code style={{ fontFamily: mono, fontSize: 13, wordBreak: 'break-all' }}>{fresh.key}</code>
            <div style={{ fontSize: 12, color: '#525252' }}>Para registrar no Claude Code:</div>
            <code style={{ fontFamily: mono, fontSize: 12, wordBreak: 'break-all' }}>{connectCommand(url, system.name, fresh.key)}</code>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button kind="tertiary" size="sm" onClick={() => copy(fresh.key)}>Copiar chave</Button>
              <Button kind="tertiary" size="sm" onClick={() => copy(connectCommand(url, system.name, fresh.key))}>Copiar comando</Button>
              <Button kind="ghost" size="sm" onClick={() => setFresh(null)}>Já copiei</Button>
            </div>
          </div>
        )}
      </section>

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
              <span style={{ fontSize: 12, color: '#525252' }}>{l.texto}</span>
            </span>
          </label>
        ))}
        <p style={{ margin: '4px 0 0', fontSize: 12, color: '#525252' }}>
          O trecho de código nunca sai, em nenhum nível. Quem usa o Claude Code já lê a pasta do projeto no próprio computador:
          este controle vale para o que o Sentinel entrega.
        </p>
      </fieldset>

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
            onChange={(_, { checked }) => toggleTool(t.name, checked)}
          />
        ))}
      </fieldset>

      <section aria-label="Últimas chamadas" style={{ display: 'grid', gap: 6 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Últimas chamadas</h3>
        {calls.data && calls.data.length > 0 ? (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#e8e8e8' }}>
                  <th style={{ padding: '6px 8px' }}>Quando</th>
                  <th style={{ padding: '6px 8px' }}>Ferramenta</th>
                  <th style={{ padding: '6px 8px' }}>Resultado</th>
                  <th style={{ padding: '6px 8px' }}>Tempo</th>
                </tr>
              </thead>
              <tbody>
                {calls.data.map((c, i) => (
                  <tr key={`${c.at}-${i}`} style={{ borderTop: '1px solid #e0e0e0' }}>
                    <td style={{ padding: '6px 8px' }}>{startLabel(c.at)}</td>
                    <td style={{ padding: '6px 8px', fontFamily: mono }}>{c.tool}</td>
                    <td style={{ padding: '6px 8px', color: c.ok ? '#198038' : '#a2191f' }}>{c.ok ? 'ok' : (c.detail ?? 'recusada')}</td>
                    <td style={{ padding: '6px 8px' }}>{c.durationMs} ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <span style={{ fontSize: 13, color: '#525252' }}>Nenhuma chamada ainda.</span>
        )}
      </section>
    </div>
  );
}
