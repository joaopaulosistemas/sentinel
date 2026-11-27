import { describe, expect, it } from 'vitest';
import type { IMcpCall, IMcpConfig } from '../types';
import { connectCommand, connectionState, describeState, isLocalAddress, refusalHelp, removeCommand, serverName } from './mcpSetup';

function cfg(over: Partial<IMcpConfig> = {}): IMcpConfig {
  return { enabled: true, hasKey: true, keyPrefix: 'snt_AbCd', keyCreatedAt: '2026-11-27T01:00:00', dataLevel: 'TITLES', tools: [], ...over };
}

function call(over: Partial<IMcpCall> = {}): IMcpCall {
  return { tool: 'status_auditoria', ok: true, durationMs: 5, detail: null, at: '2026-11-27T01:10:00', ...over };
}

describe('connectionState', () => {
  it('sem chave, fala que falta gerar a chave, mesmo com acesso marcado', () => {
    expect(connectionState(cfg({ hasKey: false, enabled: true }), [call()])).toEqual({ kind: 'no-key' });
  });

  it('com chave e acesso desligado, fala que está desligado', () => {
    expect(connectionState(cfg({ enabled: false }), [])).toEqual({ kind: 'off' });
  });

  it('ligado e sem nenhuma chamada, espera o Claude conectar', () => {
    expect(connectionState(cfg(), [])).toEqual({ kind: 'waiting' });
    expect(connectionState(cfg(), undefined)).toEqual({ kind: 'waiting' });
  });

  it('uma chamada que chegou a uma ferramenta quer dizer conectado, mesmo se a ferramenta recusou', () => {
    expect(connectionState(cfg(), [call()])).toEqual({ kind: 'connected', at: '2026-11-27T01:10:00' });
    expect(connectionState(cfg(), [call({ ok: false, detail: 'ferramenta desligada' })]).kind).toBe('connected');
  });

  it('a recusa mais recente na porta mostra o motivo', () => {
    const state = connectionState(cfg(), [call({ tool: 'acesso', ok: false, detail: 'chave diferente da vigente' })]);
    expect(state).toEqual({ kind: 'refused', reason: 'chave diferente da vigente', at: '2026-11-27T01:10:00' });
  });

  it('o que aconteceu por último é o que vale: recusa depois de conectar volta a mostrar o problema', () => {
    const calls = [call({ at: '2026-11-27T01:10:00' }), call({ tool: 'acesso', ok: false, detail: 'chamada sem chave', at: '2026-11-27T01:20:00' })];
    expect(connectionState(cfg(), calls).kind).toBe('refused');
    expect(connectionState(cfg(), [...calls].reverse()).kind).toBe('refused');
  });

  it('a conexão que deu certo vale mais que uma recusa antiga: some o "Recusando" e vira Conectado', () => {
    const calls = [
      call({ tool: 'acesso', ok: false, detail: 'chave diferente da vigente', at: '2026-11-27T01:40:00' }),
      call({ tool: 'conexão', ok: true, at: '2026-11-27T01:50:00' }),
    ];
    expect(connectionState(cfg(), calls)).toEqual({ kind: 'connected', at: '2026-11-27T01:50:00' });
  });

  it('uma recusa depois da conexão volta a mostrar o problema', () => {
    const calls = [
      call({ tool: 'conexão', ok: true, at: '2026-11-27T01:50:00' }),
      call({ tool: 'acesso', ok: false, detail: 'chave diferente da vigente', at: '2026-11-27T01:55:00' }),
    ];
    expect(connectionState(cfg(), calls).kind).toBe('refused');
  });

  it('chamadas de antes da chave atual não contam', () => {
    const antiga = call({ at: '2026-11-27T00:30:00' });
    expect(connectionState(cfg(), [antiga])).toEqual({ kind: 'waiting' });
  });

  it('recusa "acesso desligado" some depois que o acesso foi ligado', () => {
    const recusa = call({ tool: 'acesso', ok: false, detail: 'acesso desligado' });
    expect(connectionState(cfg({ enabled: true }), [recusa])).toEqual({ kind: 'waiting' });
  });
});

describe('refusalHelp', () => {
  it.each([
    ['nenhuma chave gerada', /passo 1/],
    ['chamada sem chave', /passo 3/],
    ['chave diferente da vigente', /nova chave/],
    ['acesso desligado', /passo 2/],
    ['outro motivo', /passos 1 a 3/],
  ])('"%s" diz o que fazer', (reason, fix) => {
    expect(refusalHelp(reason).fix).toMatch(fix);
    expect(refusalHelp(reason).what.length).toBeGreaterThan(10);
  });
});

describe('endereço e comando', () => {
  it('reconhece endereço que só vale neste computador', () => {
    expect(isLocalAddress('http://localhost:3000/mcp/1')).toBe(true);
    expect(isLocalAddress('http://127.0.0.1:3000/mcp/1')).toBe(true);
    expect(isLocalAddress('https://sentinel.exemplo.com/mcp/1')).toBe(false);
    expect(isLocalAddress('isto não é endereço')).toBe(false);
  });

  it('o nome do servidor não leva espaço nem acento e nunca fica vazio', () => {
    expect(serverName('Conta Corrente API', 7)).toBe('sentinel-conta-corrente-api');
    expect(serverName('Cobrança & Crédito', 3)).toBe('sentinel-cobranca-credito');
    expect(serverName('###', 9)).toBe('sentinel-sistema-9');
  });

  it('monta o comando para valer em qualquer pasta, e o de remover o registro antigo', () => {
    expect(connectCommand({ url: 'https://s.exemplo/mcp/7', name: 'sentinel-api', key: 'snt_x' })).toBe(
      'claude mcp add --scope user --transport http sentinel-api https://s.exemplo/mcp/7 --header "Authorization: Bearer snt_x"',
    );
    expect(removeCommand('sentinel-api')).toBe('claude mcp remove sentinel-api');
  });
});

describe('describeState', () => {
  const NOW = Date.parse('2026-11-27T01:20:00');

  it('diz em palavras cada estado e para onde ir', () => {
    expect(describeState({ kind: 'no-key' }, NOW)).toMatchObject({ tone: 'idle', label: 'Falta conectar', tab: 'conectar', action: 'Conectar' });
    expect(describeState({ kind: 'off' }, NOW)).toMatchObject({ tone: 'idle', label: 'Desligado', action: 'Ligar' });
    expect(describeState({ kind: 'waiting' }, NOW)).toMatchObject({ tone: 'warn', label: 'Esperando', tab: 'conectar' });
  });

  it('recusando leva a corrigir, com o que aconteceu em uma frase', () => {
    const view = describeState({ kind: 'refused', reason: 'chave diferente da vigente', at: '2026-11-27T01:10:00' }, NOW);
    expect(view).toMatchObject({ tone: 'bad', label: 'Recusando', tab: 'conectar', action: 'Corrigir' });
    expect(view.detail).toBe(refusalHelp('chave diferente da vigente').what);
  });

  it('conectado leva a gerenciar e diz quando foi a última chamada', () => {
    const view = describeState({ kind: 'connected', at: '2026-11-27T01:10:00' }, NOW);
    expect(view).toMatchObject({ tone: 'good', label: 'Conectado', tab: 'permissoes', action: 'Gerenciar' });
    expect(view.detail).toBe('Última chamada há 10 min.');
  });
});
