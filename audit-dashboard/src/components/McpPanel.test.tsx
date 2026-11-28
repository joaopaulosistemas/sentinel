import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { IMcpConfig, ISystemSummaryResponse } from '../types';
import { McpAdjustPanel, McpConnectPanel, McpPanel } from './McpPanel';

const system = { id: 7, name: 'conta-corrente-api', stack: 'JAVA', team: '', lastAuditScore: 62, lastAuditDate: null, openCritical: 0, openHigh: 0 } as ISystemSummaryResponse;

const tools = [
  { name: 'status_auditoria', description: 'Nota e situação', writes: false, enabled: true },
  { name: 'buscar_achados', description: 'Achados abertos', writes: false, enabled: true },
];

function config(over: Partial<IMcpConfig> = {}): IMcpConfig {
  return { enabled: false, hasKey: false, keyPrefix: null, keyCreatedAt: null, dataLevel: 'TITLES', tools, ...over };
}

let callList: unknown[] = [];

function setup(initial: IMcpConfig) {
  let current = initial;
  const calls: Array<{ url: string; method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const requestUrl = String(url);
      const method = init?.method ?? 'GET';
      calls.push({ url: requestUrl, method, body: init?.body as string | undefined });
      if (requestUrl.endsWith('/calls?limit=20')) return new Response(JSON.stringify(callList), { status: 200 });
      if (requestUrl.endsWith('/key') && method === 'POST') {
        current = { ...current, hasKey: true, keyPrefix: 'snt_AbCdEf' };
        return new Response(JSON.stringify({ key: 'snt_AbCdEfGHIJ-chave-completa', keyPrefix: 'snt_AbCdEf' }), { status: 200 });
      }
      if (requestUrl.endsWith('/key') && method === 'DELETE') {
        current = { ...current, hasKey: false, enabled: false, keyPrefix: null };
        return new Response(null, { status: 204 });
      }
      if (method === 'PUT') {
        const body = JSON.parse(String(init?.body));
        current = {
          ...current,
          ...(body.enabled !== undefined ? { enabled: body.enabled } : {}),
          ...(body.dataLevel ? { dataLevel: body.dataLevel } : {}),
          ...(body.tools ? { tools: current.tools.map((t) => ({ ...t, enabled: body.tools.includes(t.name) })) } : {}),
        };
        return new Response(JSON.stringify(current), { status: 200 });
      }
      return new Response(JSON.stringify(current), { status: 200 });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <McpConnectPanel system={system} />
        <McpAdjustPanel system={system} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe('McpConnectPanel e McpAdjustPanel (as abas Conectar e Permissões da área Claude)', () => {
  it('começa pedindo a chave: selo "Falta gerar a chave", passo 2 travado e sem comando ainda', async () => {
    setup(config());
    expect(await screen.findByRole('status', { name: 'Estado da conexão' })).toHaveTextContent('Falta gerar a chave');
    expect(screen.getByRole('radio', { name: /Só números e títulos/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Permitir consulta pelo Claude/ })).toBeDisabled();
    expect(screen.getByText(/Faça os passos 1 e 2 e o comando aparece aqui/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Copiar comando' })).not.toBeInTheDocument();
  });

  it('gera a chave, mostra o texto completo uma vez e depois só o começo', async () => {
    setup(config());
    fireEvent.click(await screen.findByRole('button', { name: 'Gerar chave' }));
    const aviso = await screen.findByRole('status', { name: 'Chave nova' });
    expect(aviso).toHaveTextContent('snt_AbCdEfGHIJ-chave-completa');
    fireEvent.click(screen.getByRole('button', { name: 'Já copiei' }));
    expect(screen.queryByRole('status', { name: 'Chave nova' })).not.toBeInTheDocument();
    expect(await screen.findByText(/snt_AbCdEf…/)).toBeInTheDocument();
    expect(screen.queryByText(/chave-completa/)).not.toBeInTheDocument();
  });

  it('com a chave recém-gerada e o acesso ligado, o passo 3 traz o comando pronto com o número do sistema', async () => {
    setup(config({ enabled: true }));
    fireEvent.click(await screen.findByRole('button', { name: 'Gerar chave' }));
    expect(await screen.findByText(/--scope user --transport http sentinel-conta-corrente-api/)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(`${window.location.origin}/mcp/7 --header`))).toBeInTheDocument();
    expect(screen.getByText(/Bearer snt_AbCdEfGHIJ-chave-completa/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copiar comando' })).toBeInTheDocument();
  });

  it('sem a chave inteira, o comando avisa onde colocá-la e como gerar outra', async () => {
    setup(config({ hasKey: true, enabled: true, keyPrefix: 'snt_AbCdEf' }));
    expect(await screen.findByText(/Bearer COLE_AQUI_A_CHAVE/)).toBeInTheDocument();
    expect(screen.getByText(/gere uma nova no passo 1/)).toBeInTheDocument();
  });

  it('diz onde abrir o terminal conforme o sistema operacional escolhido', async () => {
    setup(config({ hasKey: true, enabled: true, keyPrefix: 'snt_AbCdEf' }));
    expect(await screen.findByText(/Prompt de Comando/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: 'macOS' }));
    expect(screen.getByText(/Cmd \+ Espaço/)).toBeInTheDocument();
    expect(screen.queryByText(/Prompt de Comando/)).not.toBeInTheDocument();
  });

  it('avisa que localhost só funciona no computador do Sentinel', async () => {
    setup(config({ hasKey: true, enabled: true, keyPrefix: 'snt_AbCdEf' }));
    expect(await screen.findByText(/só funciona no computador onde o Sentinel está rodando/)).toBeInTheDocument();
  });

  it('ligado e sem chamadas, o selo espera e o passo 4 diz o que fazer no Claude Code', async () => {
    setup(config({ hasKey: true, enabled: true, keyPrefix: 'snt_AbCdEf', keyCreatedAt: '2026-11-27T01:00:00' }));
    expect(await screen.findByRole('status', { name: 'Estado da conexão' })).toHaveTextContent('Esperando o Claude conectar');
    expect(screen.getByText(/Esperando a primeira chamada/)).toBeInTheDocument();
  });

  it('com chamada do Claude depois da chave, o selo vira Conectado e oferece uma pergunta de exemplo', async () => {
    callList = [{ tool: 'status_auditoria', ok: true, durationMs: 9, detail: null, at: new Date().toISOString() }];
    setup(config({ hasKey: true, enabled: true, keyPrefix: 'snt_AbCdEf', keyCreatedAt: '2026-01-01T00:00:00' }));
    await waitFor(() => expect(screen.getByRole('status', { name: 'Estado da conexão' })).toHaveTextContent('Conectado'));
    expect(screen.getByText(/Use o sentinel para listar os achados críticos/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copiar pergunta' })).toBeInTheDocument();
    callList = [];
  });

  it('com recusa na porta, o selo diz Recusando e o passo 4 explica o motivo e o que fazer', async () => {
    callList = [{ tool: 'acesso', ok: false, durationMs: 0, detail: 'chave diferente da vigente', at: new Date().toISOString() }];
    setup(config({ hasKey: true, enabled: true, keyPrefix: 'snt_AbCdEf', keyCreatedAt: '2026-01-01T00:00:00' }));
    await waitFor(() => expect(screen.getByRole('status', { name: 'Estado da conexão' })).toHaveTextContent('Recusando chamadas'));
    expect(screen.getByText(/não é a atual/)).toBeInTheDocument();
    expect(screen.getByText(/Gere uma nova chave no passo 1/)).toBeInTheDocument();
    callList = [];
  });

  it('mostra os parâmetros de cada chamada, com o motivo separado quando foi recusada', async () => {
    callList = [
      { tool: 'achados', ok: true, durationMs: 12, detail: 'severity=CRITICAL, limit=5', at: new Date().toISOString() },
      { tool: 'iniciar_auditoria', ok: false, durationMs: 3, detail: 'ferramenta desligada | branch=main', at: new Date().toISOString() },
    ];
    setup(config({ hasKey: true, keyPrefix: 'snt_AbCdEf' }));
    expect(await screen.findByText('severity=CRITICAL, limit=5')).toBeInTheDocument();
    expect(screen.getByText('ferramenta desligada')).toBeInTheDocument();
    expect(screen.getByText('branch=main')).toBeInTheDocument();
    callList = [];
  });

  it('só liga a consulta depois de haver chave', async () => {
    const calls = setup(config({ hasKey: true, keyPrefix: 'snt_AbCdEf' }));
    const toggle = await screen.findByRole('checkbox', { name: /Permitir consulta pelo Claude/ });
    expect(toggle).toBeEnabled();
    fireEvent.click(toggle);
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.body === JSON.stringify({ enabled: true }))).toBe(true));
  });

  it('trocar o nível de dados salva na hora', async () => {
    const calls = setup(config({ hasKey: true, keyPrefix: 'snt_AbCdEf' }));
    fireEvent.click(await screen.findByRole('radio', { name: /Sensível/ }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.body === JSON.stringify({ dataLevel: 'SENSITIVE' }))).toBe(true));
    await waitFor(() => expect(screen.getByRole('radio', { name: /Sensível/ })).toBeChecked());
  });

  it('desligar uma ferramenta manda a lista das que continuam ligadas', async () => {
    const calls = setup(config({ hasKey: true, keyPrefix: 'snt_AbCdEf' }));
    fireEvent.click(await screen.findByRole('checkbox', { name: /buscar_achados/ }));
    await waitFor(() =>
      expect(calls.some((c) => c.method === 'PUT' && c.body === JSON.stringify({ tools: ['status_auditoria'] }))).toBe(true),
    );
  });

  it('gerar uma nova chave pede confirmação, porque a anterior deixa de valer', async () => {
    const calls = setup(config({ hasKey: true, keyPrefix: 'snt_AbCdEf' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Gerar nova chave' }));
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
    fireEvent.click(screen.getByRole('button', { name: /Confirmar: gerar e invalidar a anterior/ }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/key'))).toBe(true));
  });
});

describe('McpPanel (aba Claude (MCP) do cadastro do sistema)', () => {
  function renderShort(cfg: IMcpConfig, calls: unknown[] = []) {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(String(url).includes('/calls') ? calls : cfg), { status: 200 })));
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <McpPanel system={system} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
  }

  it('não repete os passos: mostra a situação e leva à área Claude (MCP)', async () => {
    renderShort(config());
    expect(await screen.findByText('Falta conectar')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Conectar ou corrigir' })).toHaveAttribute('href', '/claude?aba=conectar&systemId=7');
    expect(screen.getByRole('link', { name: 'Permissões' })).toHaveAttribute('href', '/claude?aba=permissoes&systemId=7');
    expect(screen.getByRole('link', { name: 'Atividade' })).toHaveAttribute('href', '/claude?aba=atividade');
    expect(screen.queryByLabelText('Permitir consulta pelo Claude (MCP)')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Gerar chave/ })).not.toBeInTheDocument();
  });

  it('com chamada do Claude depois da chave, diz Conectado', async () => {
    renderShort(config({ enabled: true, hasKey: true, keyPrefix: 'snt_AbCd', keyCreatedAt: '2026-11-27T01:00:00' }), [
      { tool: 'status_auditoria', ok: true, durationMs: 4, detail: null, at: '2026-11-27T01:10:00' },
    ]);
    expect(await screen.findByText('Conectado')).toBeInTheDocument();
  });
});

