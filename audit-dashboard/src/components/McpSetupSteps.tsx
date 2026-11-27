import { Button, Checkbox } from '@carbon/react';
import { useState, type ReactNode } from 'react';
import type { IMcpCall, IMcpConfig, IMcpNewKey } from '../types';
import {
  EXAMPLE_QUESTION,
  OPERATING_SYSTEMS,
  connectCommand,
  connectionState,
  isLocalAddress,
  refusalHelp,
  removeCommand,
  type ConnectionState,
  type OperatingSystem,
} from '../utils/mcpSetup';
import { startLabel } from '../utils/timeAgo';

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const KEY_PLACEHOLDER = 'COLE_AQUI_A_CHAVE';
const CLAUDE_CODE_DOCS = 'https://docs.claude.com/en/docs/claude-code/overview';

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    /* sem permissão para a área de transferência: a pessoa seleciona o texto à mão */
  }
}

type Tone = 'good' | 'bad' | 'warn' | 'idle';

const TONES: Record<Tone, { bg: string; fg: string }> = {
  good: { bg: 'var(--s-good-bg)', fg: 'var(--s-success-text)' },
  bad: { bg: 'var(--s-bad-bg)', fg: 'var(--s-danger-text)' },
  warn: { bg: 'var(--s-warn-bg)', fg: 'var(--s-warn-text)' },
  idle: { bg: 'var(--s-layer)', fg: 'var(--s-text-3)' },
};

/** Texto e cor do selo do topo, que diz em uma linha se o Claude já está conectado. */
function badgeFor(state: ConnectionState): { tone: Tone; text: string } {
  switch (state.kind) {
    case 'no-key':
      return { tone: 'idle', text: 'Falta gerar a chave' };
    case 'off':
      return { tone: 'idle', text: 'Acesso desligado' };
    case 'waiting':
      return { tone: 'warn', text: 'Esperando o Claude conectar' };
    case 'refused':
      return { tone: 'bad', text: 'Recusando chamadas' };
    case 'connected':
      return { tone: 'good', text: `Conectado · última chamada ${startLabel(state.at)}` };
  }
}

export function StatusBadge({ state }: { state: ConnectionState }) {
  const { tone, text } = badgeFor(state);
  return (
    <div role="status" aria-label="Estado da conexão" style={{ display: 'inline-block', padding: '6px 12px', fontSize: 14, fontWeight: 600, background: TONES[tone].bg, color: TONES[tone].fg }}>
      {text}
    </div>
  );
}

function Step({ n, done, title, children }: { n: number; done: boolean; title: string; children: ReactNode }) {
  return (
    <li style={{ display: 'flex', gap: 12, alignItems: 'flex-start', padding: '14px 0', borderTop: '1px solid var(--s-border)' }}>
      <span
        aria-label={done ? `Passo ${n} feito` : `Passo ${n}`}
        style={{ flex: 'none', width: 24, height: 24, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 600, background: done ? TONES.good.bg : 'var(--s-layer)', color: done ? TONES.good.fg : 'var(--s-text-3)' }}
      >
        {done ? '✓' : n}
      </span>
      <div style={{ flex: 1, display: 'grid', gap: 8, minWidth: 0 }}>
        <div style={{ fontWeight: 600 }}>{title}</div>
        {children}
      </div>
    </li>
  );
}

const hint = { margin: 0, fontSize: 13, color: 'var(--s-text-3)' } as const;
const codeBox = { fontFamily: mono, fontSize: 12, background: 'var(--s-layer)', padding: '8px 10px', wordBreak: 'break-all' } as const;

interface IKeyStepProps {
  cfg: IMcpConfig;
  fresh: IMcpNewKey | null;
  confirmNew: boolean;
  issuing: boolean;
  revoking: boolean;
  onIssue: () => void;
  onRevoke: () => void;
  onConfirmNew: (on: boolean) => void;
  onDismissFresh: () => void;
}

function KeyStep({ cfg, fresh, confirmNew, issuing, revoking, onIssue, onRevoke, onConfirmNew, onDismissFresh }: IKeyStepProps) {
  return (
    <Step n={1} done={cfg.hasKey} title="Gerar a chave">
      <p style={hint}>A chave é a senha que deixa o Claude Code consultar só este sistema. Ela aparece inteira uma única vez.</p>
      {cfg.hasKey && <span style={{ fontSize: 13 }}>Chave criada: <code style={{ fontFamily: mono }}>{cfg.keyPrefix}…</code> em {startLabel(cfg.keyCreatedAt)}</span>}
      {fresh && (
        <div role="status" aria-label="Chave nova" style={{ background: 'var(--s-warn-bg)', borderLeft: '3px solid var(--s-warn-text)', padding: '10px 14px', display: 'grid', gap: 8 }}>
          <strong>Copie a chave agora: ela não aparece de novo.</strong>
          <code style={{ fontFamily: mono, fontSize: 13, wordBreak: 'break-all' }}>{fresh.key}</code>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Button kind="tertiary" size="sm" onClick={() => void copy(fresh.key)}>Copiar chave</Button>
            <Button kind="ghost" size="sm" onClick={onDismissFresh}>Já copiei</Button>
          </div>
        </div>
      )}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {!cfg.hasKey || confirmNew ? (
          <Button kind="primary" size="sm" disabled={issuing} onClick={onIssue}>
            {confirmNew ? 'Confirmar: gerar e invalidar a anterior' : 'Gerar chave'}
          </Button>
        ) : (
          <Button kind="tertiary" size="sm" onClick={() => onConfirmNew(true)}>Gerar nova chave</Button>
        )}
        {confirmNew && <Button kind="ghost" size="sm" onClick={() => onConfirmNew(false)}>Cancelar</Button>}
        {cfg.hasKey && !confirmNew && (
          <Button kind="danger--tertiary" size="sm" disabled={revoking} onClick={onRevoke}>Revogar e desligar</Button>
        )}
      </div>
    </Step>
  );
}

function EnableStep({ cfg, updating, onToggle }: { cfg: IMcpConfig; updating: boolean; onToggle: (on: boolean) => void }) {
  return (
    <Step n={2} done={cfg.hasKey && cfg.enabled} title="Ligar o acesso">
      <Checkbox
        id="mcp-enabled"
        labelText="Permitir consulta pelo Claude (MCP)"
        helperText={cfg.hasKey ? 'Desligado, qualquer chamada com a chave deste sistema é recusada.' : 'Gere a chave no passo 1 para poder ligar.'}
        checked={cfg.enabled}
        disabled={!cfg.hasKey || updating}
        onChange={(_, { checked }) => onToggle(checked)}
      />
    </Step>
  );
}

interface ICommandStepProps {
  cfg: IMcpConfig;
  url: string;
  name: string;
  fresh: IMcpNewKey | null;
  connected: boolean;
}

function CommandStep({ cfg, url, name, fresh, connected }: ICommandStepProps) {
  const [os, setOs] = useState<OperatingSystem>('windows');
  const ready = cfg.hasKey && cfg.enabled;
  const command = connectCommand({ url, name, key: fresh?.key ?? KEY_PLACEHOLDER });
  const where = OPERATING_SYSTEMS.find((o) => o.id === os)?.where;
  return (
    <Step n={3} done={connected} title="Registrar no Claude Code">
      <p style={hint}>
        Você precisa ter o Claude Code instalado neste computador.{' '}
        <a href={CLAUDE_CODE_DOCS} target="_blank" rel="noreferrer">Como instalar</a>
      </p>
      {!ready ? (
        <p style={hint}>Faça os passos 1 e 2 e o comando aparece aqui.</p>
      ) : (
        <>
          <div role="radiogroup" aria-label="Seu sistema operacional" style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
            {OPERATING_SYSTEMS.map((o) => (
              <label key={o.id} style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 13, cursor: 'pointer' }}>
                <input type="radio" name="mcp-os" checked={os === o.id} onChange={() => setOs(o.id)} />
                {o.label}
              </label>
            ))}
          </div>
          <p style={{ margin: 0, fontSize: 13 }}>{where}</p>
          <code style={codeBox}>{command}</code>
          {!fresh && <p style={hint}>A chave inteira só aparece na hora de gerar. Se você não a tem mais, gere uma nova no passo 1.</p>}
          {isLocalAddress(url) && (
            <p style={{ ...hint, color: 'var(--s-warn-text)' }}>
              Este endereço (localhost) só funciona no computador onde o Sentinel está rodando. Em outro computador, abra o Sentinel pelo endereço do servidor.
            </p>
          )}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Button kind="primary" size="sm" onClick={() => void copy(command)}>Copiar comando</Button>
          </div>
          <details style={{ fontSize: 13 }}>
            <summary style={{ cursor: 'pointer' }}>Já registrou antes e deu erro?</summary>
            <p style={hint}>Tire o registro antigo e rode o comando de novo:</p>
            <code style={codeBox}>{removeCommand(name)}</code>
          </details>
        </>
      )}
    </Step>
  );
}

function CheckBody({ state }: { state: ConnectionState }) {
  if (state.kind === 'connected') {
    return (
      <>
        <p style={{ margin: 0, color: 'var(--s-success-text)' }}>Conectado: o Claude consultou este sistema {startLabel(state.at)}.</p>
        <p style={hint}>Agora, no Claude Code, peça por exemplo:</p>
        <code style={codeBox}>{EXAMPLE_QUESTION}</code>
        <div><Button kind="tertiary" size="sm" onClick={() => void copy(EXAMPLE_QUESTION)}>Copiar pergunta</Button></div>
      </>
    );
  }
  if (state.kind === 'refused') {
    const help = refusalHelp(state.reason);
    return (
      <div style={{ background: 'var(--s-bad-bg)', borderLeft: '3px solid var(--s-danger-text)', padding: '10px 14px', display: 'grid', gap: 4 }}>
        <strong>{help.what}</strong>
        <span>{help.fix}</span>
        <span style={hint}>Última tentativa: {startLabel(state.at)}</span>
      </div>
    );
  }
  if (state.kind === 'waiting') {
    return (
      <p style={{ margin: 0 }}>
        Esperando a primeira chamada. Depois de rodar o comando, abra o Claude Code (digite <code style={{ fontFamily: mono }}>claude</code> no terminal) e
        digite <code style={{ fontFamily: mono }}>/mcp</code>. Esta tela confere sozinha a cada poucos segundos.
      </p>
    );
  }
  return <p style={hint}>Faça os passos anteriores para poder conferir.</p>;
}

function CheckStep({ state }: { state: ConnectionState }) {
  return (
    <Step n={4} done={state.kind === 'connected'} title="Conferir a conexão">
      <CheckBody state={state} />
    </Step>
  );
}

interface ISetupProps extends IKeyStepProps {
  url: string;
  name: string;
  calls: IMcpCall[] | undefined;
  updating: boolean;
  onToggleEnabled: (on: boolean) => void;
}

/** O assistente: o selo de estado no topo e quatro passos, cada um mostrando se já foi feito. */
export function McpSetupSteps(props: ISetupProps) {
  const state = connectionState(props.cfg, props.calls);
  return (
    <section aria-label="Conectar o Claude Code" style={{ display: 'grid', gap: 12 }}>
      <StatusBadge state={state} />
      <p style={{ margin: 0, fontSize: 14, color: 'var(--s-text-3)' }}>
        O MCP deixa o Claude Code consultar o resultado deste sistema. Siga os quatro passos na ordem: cada um mostra se já foi feito.
      </p>
      <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        <KeyStep {...props} />
        <EnableStep cfg={props.cfg} updating={props.updating} onToggle={props.onToggleEnabled} />
        <CommandStep cfg={props.cfg} url={props.url} name={props.name} fresh={props.fresh} connected={state.kind === 'connected'} />
        <CheckStep state={state} />
      </ol>
    </section>
  );
}
