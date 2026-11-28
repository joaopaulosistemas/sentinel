import type { IMcpCall, IMcpConfig } from '../types';
import { startLabel } from './timeAgo';

/** Nome com que a API registra, na coluna "Ferramenta", uma chamada recusada antes de chegar a uma ferramenta. */
export const REFUSAL_TOOL = 'acesso';

/** Nome com que a API registra o Claude Code conectando (o aperto de mão do protocolo, antes de qualquer consulta). */
export const HANDSHAKE_TOOL = 'conexão';

/** Onde a pessoa abre o terminal para colar o comando, conforme o sistema dela. */
export type OperatingSystem = 'windows' | 'mac' | 'linux';

export const OPERATING_SYSTEMS: Array<{ id: OperatingSystem; label: string; where: string }> = [
  { id: 'windows', label: 'Windows', where: 'Abra o menu Iniciar, digite "Prompt de Comando" (ou "PowerShell") e abra. Cole o comando lá e aperte Enter.' },
  { id: 'mac', label: 'macOS', where: 'Aperte Cmd + Espaço, digite "Terminal" e abra. Cole o comando lá e aperte Enter.' },
  { id: 'linux', label: 'Linux', where: 'Abra o terminal do seu sistema. Cole o comando lá e aperte Enter.' },
];

/** Em que ponto está a ligação do Claude com este sistema, olhando a configuração e o registro de chamadas. */
export type ConnectionState =
  | { kind: 'no-key' }
  | { kind: 'off' }
  | { kind: 'waiting' }
  | { kind: 'refused'; reason: string; at: string }
  | { kind: 'connected'; at: string };

function time(iso: string): number {
  const instante = Date.parse(iso);
  return Number.isNaN(instante) ? 0 : instante;
}

/** Uma recusa que a configuração de hoje já resolveu não deve mais aparecer como problema. */
function staleRefusal(cfg: IMcpConfig, reason: string | null | undefined): boolean {
  return (cfg.enabled && reason === 'acesso desligado') || (cfg.hasKey && reason === 'nenhuma chave gerada');
}

/**
 * Estado da ligação. Vale o que aconteceu por último depois da chave atual: uma chamada que chegou a uma ferramenta
 * (deu certo ou foi recusada por ela) quer dizer que o Claude conectou; uma recusa na porta quer dizer que ele
 * tentou e a chave, o interruptor ou o endereço não bateram.
 */
export function connectionState(cfg: IMcpConfig, calls: IMcpCall[] | undefined): ConnectionState {
  if (!cfg.hasKey) return { kind: 'no-key' };
  if (!cfg.enabled) return { kind: 'off' };
  const since = cfg.keyCreatedAt ? time(cfg.keyCreatedAt) : 0;
  const recent = (calls ?? []).filter((c) => time(c.at) >= since).sort((a, b) => time(b.at) - time(a.at));
  for (const call of recent) {
    if (call.tool !== REFUSAL_TOOL) return { kind: 'connected', at: call.at };
    if (!staleRefusal(cfg, call.detail)) return { kind: 'refused', reason: call.detail ?? 'recusada', at: call.at };
  }
  return { kind: 'waiting' };
}

interface IRefusalHelp {
  /** O que aconteceu, em uma frase. */
  what: string;
  /** O que fazer agora. */
  fix: string;
}

/** Explica o motivo guardado pela API para uma recusa e diz o que a pessoa faz. */
export function refusalHelp(reason: string): IRefusalHelp {
  switch (reason) {
    case 'nenhuma chave gerada':
      return { what: 'Este sistema ainda não tem chave.', fix: 'Gere a chave no passo 1.' };
    case 'chamada sem chave':
      return { what: 'O Claude Code chamou sem enviar a chave.', fix: 'Rode o comando do passo 3 de novo, com a chave inteira.' };
    case 'chave diferente da vigente':
      return {
        what: 'A chave que o Claude Code está usando não é a atual (talvez seja uma chave antiga).',
        fix: 'Gere uma nova chave no passo 1 e rode o comando do passo 3 de novo.',
      };
    case 'acesso desligado':
      return { what: 'A chave está certa, mas o acesso está desligado.', fix: 'Ligue o acesso no passo 2.' };
    default:
      return { what: `Chamada recusada: ${reason}.`, fix: 'Confira os passos 1 a 3 e rode o comando de novo.' };
  }
}

/** Endereço que só funciona no computador onde o Sentinel roda. */
export function isLocalAddress(url: string): boolean {
  try {
    const host = new URL(url).hostname;
    return host === 'localhost' || host === '127.0.0.1' || host === '[::1]' || host === '::1';
  } catch {
    return false;
  }
}

/** Nome do servidor no Claude Code: sem espaços nem acentos, que o comando não aceita bem. */
export function serverName(systemName: string, systemId: number): string {
  const slug = systemName
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return `sentinel-${slug || `sistema-${systemId}`}`;
}

/** Comando que registra este sistema no Claude Code, para valer em qualquer pasta. */
export function connectCommand({ url, name, key }: { url: string; name: string; key: string }): string {
  return `claude mcp add --scope user --transport http ${name} ${url} --header "Authorization: Bearer ${key}"`;
}

/** Comando que tira um registro antigo, para quem já tinha rodado o comando antes. */
export function removeCommand(name: string): string {
  return `claude mcp remove ${name}`;
}

/** Pergunta pronta para a primeira conversa com o Claude Code, depois de conectar. */
export const EXAMPLE_QUESTION = 'Use o sentinel para listar os achados críticos do último relatório.';

export type StateTone = 'good' | 'bad' | 'warn' | 'idle';

export interface IStateView {
  tone: StateTone;
  /** Palavra curta do selo: "Conectado", "Recusando"... */
  label: string;
  /** Uma frase que diz o que está acontecendo com este sistema. */
  detail: string;
  /** Aba da área Claude (MCP) onde a pessoa resolve ou gerencia. */
  tab: 'conectar' | 'permissoes';
  /** Texto do botão que leva até lá. */
  action: string;
}

/** O estado da ligação em palavras, para as listas que mostram vários sistemas de uma vez. */
export function describeState(state: ConnectionState, now: number = Date.now()): IStateView {
  switch (state.kind) {
    case 'no-key':
      return { tone: 'idle', label: 'Falta conectar', detail: 'Sem chave: o Claude ainda não pode consultar este sistema.', tab: 'conectar', action: 'Conectar' };
    case 'off':
      return { tone: 'idle', label: 'Desligado', detail: 'A chave existe, mas o acesso está desligado.', tab: 'conectar', action: 'Ligar' };
    case 'waiting':
      return { tone: 'warn', label: 'Esperando', detail: 'Tudo pronto: esperando o Claude Code conectar.', tab: 'conectar', action: 'Ver passos' };
    case 'refused':
      return { tone: 'bad', label: 'Recusando', detail: refusalHelp(state.reason).what, tab: 'conectar', action: 'Corrigir' };
    case 'connected':
      return { tone: 'good', label: 'Conectado', detail: `Última chamada ${startLabel(state.at, now)}.`, tab: 'permissoes', action: 'Gerenciar' };
  }
}
