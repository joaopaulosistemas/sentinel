import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ClaudePage } from './ClaudePage';

const systems = [
  { id: 1, name: 'sentinel', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 },
  { id: 2, name: 'cobranca', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 },
];

const cfg = (over: object) => ({ enabled: true, hasKey: true, keyPrefix: 'snt_AbCd', keyCreatedAt: '2026-11-27T01:00:00', dataLevel: 'TITLES', tools: [], ...over });

const NOW = new Date().toISOString();
const call = (over: object) => ({ tool: 'status_auditoria', ok: true, durationMs: 5, detail: null, at: NOW, ...over });

interface IStub {
  cfgs?: Record<number, object>;
  calls?: Record<number, object[]>;
  pending?: Record<number, object[]>;
  reviews?: Record<number, object>;
}

function stub({ cfgs = {}, calls = {}, pending = {}, reviews = {} }: IStub = {}) {
  const posts: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      if (init?.method === 'POST') {
        posts.push(u);
        return new Response('[]', { status: 200 });
      }
      const id = Number(u.match(/\/(\d+)(?:\/|\?|$)/)?.[1] ?? 0);
      if (u.includes('/mcp-access/') && u.includes('/calls')) return new Response(JSON.stringify(calls[id] ?? []), { status: 200 });
      if (u.includes('/mcp-access/')) return new Response(JSON.stringify(cfgs[id] ?? cfg({ hasKey: false })), { status: 200 });
      if (u.includes('/pending-actions/')) return new Response(JSON.stringify(pending[id] ?? []), { status: 200 });
      if (u.includes('/claude-review/')) return new Response(JSON.stringify(reviews[id] ?? { openSuggestions: 0, suggestions: [], proposals: [] }), { status: 200 });
      return new Response(JSON.stringify(systems), { status: 200 });
    }),
  );
  return posts;
}

function open(search = '') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[`/claude${search}`]}>
        <ClaudePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('ClaudePage', () => {
  it('mostra as cinco abas e começa na Visão geral', async () => {
    stub();
    open();
    const tabs = screen.getByRole('tablist', { name: 'Seções do Claude (MCP)' });
    expect(within(tabs).getAllByRole('tab').map((t) => t.textContent)).toEqual(['Visão geral', 'Conectar', 'Decisões', 'Atividade', 'Permissões']);
    expect(screen.getByRole('tab', { name: 'Visão geral' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('Seus sistemas')).toBeInTheDocument();
  });

  it('a Visão geral conta conectados e recusas e leva cada sistema à aba certa', async () => {
    stub({
      cfgs: { 1: cfg({}), 2: cfg({}) },
      calls: { 1: [call({})], 2: [call({ tool: 'acesso', ok: false, detail: 'chave diferente da vigente' })] },
    });
    open();
    expect(await screen.findByText('1 de 2')).toBeInTheDocument();
    const lista = screen.getByRole('list');
    const [sentinel, cobranca] = within(lista).getAllByRole('listitem');
    expect(within(sentinel).getByText('Conectado')).toBeInTheDocument();
    expect(within(sentinel).getByRole('link', { name: 'Gerenciar' })).toHaveAttribute('href', '/claude?aba=permissoes&systemId=1');
    expect(within(cobranca).getByText('Recusando')).toBeInTheDocument();
    expect(within(cobranca).getByRole('link', { name: 'Corrigir' })).toHaveAttribute('href', '/claude?aba=conectar&systemId=2');
  });

  it('a Visão geral conta as decisões esperando e a aba Decisões mostra o número', async () => {
    stub({ pending: { 1: [{ id: 7, kind: 'START_AUDIT', summary: 'Auditar a main', status: 'PENDING', createdAt: NOW, expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null }] } });
    open();
    expect(await screen.findByLabelText('1 esperando')).toBeInTheDocument();
  });

  it('Decisões: aprovar um pedido manda a decisão ao sistema certo', async () => {
    const posts = stub({ pending: { 2: [{ id: 7, kind: 'START_AUDIT', summary: 'Auditar a main', status: 'PENDING', createdAt: NOW, expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null }] } });
    open('?aba=decisoes');
    const linha = await screen.findByRole('listitem', { name: 'Pedido: Auditar a main' });
    expect(within(linha).getByText(/cobranca/)).toBeInTheDocument();
    fireEvent.click(within(linha).getByRole('button', { name: 'Aprovar' }));
    await waitFor(() => expect(posts).toEqual(['/api/v1/pending-actions/2/7/approve']));
  });

  it('Decisões: filtra por tipo e mostra o estado vazio', async () => {
    stub({ reviews: { 1: { openSuggestions: 1, proposals: [], suggestions: [{ id: 9, title: 'Extrair a validação', detail: null, ruleRef: null, file: null, line: null, status: 'OPEN', createdAt: '' }] } } });
    open('?aba=decisoes');
    expect(await screen.findByRole('listitem', { name: 'Sugestão: Extrair a validação' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Pedidos (0)' }));
    expect(screen.getByText(/Nada esperando você/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pedidos (0)' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('Atividade: junta as chamadas de todos os sistemas e filtra as recusadas, com o que fazer', async () => {
    stub({
      cfgs: { 1: cfg({}), 2: cfg({}) },
      calls: {
        1: [call({ tool: 'status_auditoria', at: '2026-11-27T01:00:00' })],
        2: [call({ tool: 'acesso', ok: false, detail: 'chamada sem chave', at: '2026-11-27T01:05:00' })],
      },
    });
    open('?aba=atividade');
    const tabela = await screen.findByRole('table', { name: 'Chamadas do Claude em todos os sistemas' });
    await waitFor(() => expect(within(tabela).getAllByRole('row')).toHaveLength(3));
    const linhas = within(tabela).getAllByRole('row').slice(1);
    expect(linhas[0]).toHaveTextContent('cobranca');
    expect(linhas[1]).toHaveTextContent('sentinel');
    expect(within(linhas[0]).getByRole('link', { name: 'Abrir os passos' })).toHaveAttribute('href', '/claude?aba=conectar&systemId=2');

    fireEvent.click(screen.getByRole('checkbox', { name: 'Só recusadas' }));
    expect(within(tabela).getAllByRole('row')).toHaveLength(2);
    fireEvent.change(screen.getByLabelText('Sistema'), { target: { value: '1' } });
    expect(screen.getByText('Nenhuma chamada recusada ainda.')).toBeInTheDocument();
  });

  it('Conectar: escolhe o sistema pela URL e troca pelo seletor', async () => {
    stub();
    open('?aba=conectar&systemId=2');
    expect(await screen.findByLabelText('Sistema')).toHaveValue('2');
    fireEvent.change(screen.getByLabelText('Sistema'), { target: { value: '1' } });
    await waitFor(() => expect(screen.getByLabelText('Sistema')).toHaveValue('1'));
  });

  it('Permissões: mostra os ajustes do sistema e aponta para Conectar para trocar a chave', async () => {
    stub({ cfgs: { 1: cfg({}) } });
    open('?aba=permissoes&systemId=1');
    expect(await screen.findByRole('link', { name: 'aba Conectar' })).toHaveAttribute('href', '/claude?aba=conectar&systemId=1');
  });

  it('trocar de aba atualiza a seleção', async () => {
    stub();
    open();
    fireEvent.click(screen.getByRole('tab', { name: 'Atividade' }));
    expect(screen.getByRole('tab', { name: 'Atividade' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('Nenhuma chamada ainda.')).toBeInTheDocument();
  });
});
