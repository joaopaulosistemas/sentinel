import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { McpConfig, SystemSummaryResponse } from '../types';
import { McpPanel, connectCommand } from './McpPanel';

const system = { id: 7, name: 'conta-corrente-api', stack: 'JAVA', team: '', lastAuditScore: 62, lastAuditDate: null, openCritical: 0, openHigh: 0 } as SystemSummaryResponse;

const tools = [
  { name: 'status_auditoria', description: 'Nota e situação', writes: false, enabled: true },
  { name: 'buscar_achados', description: 'Achados abertos', writes: false, enabled: true },
];

function config(over: Partial<McpConfig> = {}): McpConfig {
  return { enabled: false, hasKey: false, keyPrefix: null, keyCreatedAt: null, dataLevel: 'TITLES', tools, ...over };
}

let callList: unknown[] = [];

function setup(initial: McpConfig) {
  let current = initial;
  const calls: Array<{ url: string; method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      const method = init?.method ?? 'GET';
      calls.push({ url: u, method, body: init?.body as string | undefined });
      if (u.endsWith('/calls?limit=20')) return new Response(JSON.stringify(callList), { status: 200 });
      if (u.endsWith('/key') && method === 'POST') {
        current = { ...current, hasKey: true, keyPrefix: 'snt_AbCdEf' };
        return new Response(JSON.stringify({ key: 'snt_AbCdEfGHIJ-chave-completa', keyPrefix: 'snt_AbCdEf' }), { status: 200 });
      }
      if (u.endsWith('/key') && method === 'DELETE') {
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
      <McpPanel system={system} />
    </QueryClientProvider>,
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe('McpPanel', () => {
  it('mostra o endereço do sistema e começa em só números e títulos, com a consulta desligada', async () => {
    setup(config());
    expect(await screen.findByText(`${window.location.origin}/mcp/conta-corrente-api`)).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Só números e títulos/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Permitir consulta pelo Claude/ })).toBeDisabled();
    expect(screen.getByText('Nenhuma chave gerada.')).toBeInTheDocument();
  });

  it('gera a chave, mostra o texto completo uma vez e depois só o começo', async () => {
    setup(config());
    fireEvent.click(await screen.findByRole('button', { name: 'Gerar chave' }));
    const aviso = await screen.findByRole('status', { name: 'Chave nova' });
    expect(aviso).toHaveTextContent('snt_AbCdEfGHIJ-chave-completa');
    expect(aviso).toHaveTextContent('claude mcp add --transport http sentinel-conta-corrente-api');
    fireEvent.click(screen.getByRole('button', { name: 'Já copiei' }));
    expect(screen.queryByRole('status', { name: 'Chave nova' })).not.toBeInTheDocument();
    expect(await screen.findByText('snt_AbCdEf…')).toBeInTheDocument();
    expect(screen.queryByText(/chave-completa/)).not.toBeInTheDocument();
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

  it('monta o comando do Claude Code com a chave e o endereço', () => {
    expect(connectCommand('https://s.exemplo/mcp/api', 'api', 'snt_x')).toBe(
      'claude mcp add --transport http sentinel-api https://s.exemplo/mcp/api --header "Authorization: Bearer snt_x"',
    );
  });
});
