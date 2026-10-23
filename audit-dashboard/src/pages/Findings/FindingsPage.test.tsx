import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FindingsPage } from './FindingsPage';

const finding = (id: number, title: string) => ({
  id, category: 'SECURITY', severity: 'CRITICAL', title, description: 'd', filePath: 'A.java', lineNumber: 3,
  codeSnippet: null, ruleId: 'r', toolSource: 'semgrep', cveId: null, aiRecommendation: null, status: 'OPEN',
});

function stub(urls: string[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      urls.push(String(url));
      const u = String(url);
      const body = u.startsWith('/api/v1/findings?')
        ? {
            total: 2,
            bySystem: [
              { systemId: 1, systemName: 'conta-corrente-api', auditId: 10, count: 1, stack: 'JAVA', critical: 1, high: 0, auditedAt: '2026-10-22T09:00:00' },
              { systemId: 2, systemName: 'portal-cooperado', auditId: 20, count: 1, stack: 'JAVASCRIPT', critical: 0, high: 17, auditedAt: '2026-10-21T09:00:00' },
            ],
            items: [
              { systemId: 1, systemName: 'conta-corrente-api', auditId: 10, finding: finding(1, 'SQL por concatenação') },
              { systemId: 2, systemName: 'portal-cooperado', auditId: 20, finding: finding(2, 'CORS aberto') },
            ],
          }
        : [];
      return new Response(JSON.stringify(body), { status: 200 });
    }),
  );
}

afterEach(() => vi.unstubAllGlobals());

function renderFindings(url = '/findings') {
  stub([]);
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[url]}>
        <FindingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('FindingsPage', () => {
  it('sem auditoria na URL mostra os achados de todos os sistemas, com o sistema de cada um', async () => {
    const urls: string[] = [];
    stub(urls);
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/findings']}>
          <FindingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('SQL por concatenação')).toBeInTheDocument();
    expect(screen.getByText('CORS aberto')).toBeInTheDocument();
    // o nome do sistema aparece no resumo do topo e na coluna da tabela
    expect(screen.getAllByText('portal-cooperado').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('Auditoria #20', { exact: false, selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText('17')).toBeInTheDocument(); // altos do portal no cartão do resumo
    expect(screen.getByText('TypeScript')).toBeInTheDocument(); // stack na coluna Sistema
    // padrão: críticos e altos, abertos
    const first = urls.find((u) => u.startsWith('/api/v1/findings?')) ?? '';
    expect(first).toContain('severity=CRITICAL&severity=HIGH');
    expect(first).toContain('status=OPEN');
    expect(first).toContain('limit=20');
    expect(first).toContain('offset=0');
    expect(screen.getByText('Mostrando 2 de 2 abertos (críticos e altos) em 2 sistemas')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument();
  });

  it('chama a tela de Informações de Auditoria, com a aba de achados ativa, a Revisão do Claude disponível e a Reauditoria desativada', async () => {
    renderFindings();
    expect(await screen.findByRole('heading', { level: 1, name: 'Informações de Auditoria' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Achados e nota' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Revisão do Claude' })).toBeEnabled();
    expect(screen.getByRole('tab', { name: 'Reauditoria' })).toBeDisabled();
  });

  it('mostra de qual analisador veio cada achado', async () => {
    renderFindings();
    await screen.findByText('SQL por concatenação');
    expect(screen.getAllByText('Semgrep').length).toBe(2);
  });

  it('agrupa os achados por sistema quando o usuário pede', async () => {
    renderFindings();
    await screen.findByText('SQL por concatenação');
    expect(screen.queryByRole('region', { name: 'Achados de portal-cooperado' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Agrupar por sistema' }));
    expect(await screen.findByRole('region', { name: 'Achados de conta-corrente-api' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Achados de portal-cooperado' })).toBeInTheDocument();
  });

  it('mostra os chips dos filtros ativos e deixa restaurar o padrão', async () => {
    renderFindings('/findings?onlyNew=1');
    const chips = await screen.findByLabelText('Filtros ativos');
    expect(chips).toHaveTextContent('Todos os sistemas');
    expect(chips).toHaveTextContent('Crítica e alta');
    expect(chips).toHaveTextContent('Novos desde a última auditoria');
    fireEvent.click(screen.getByRole('button', { name: 'Restaurar padrão' }));
    expect(screen.getByLabelText('Filtros ativos')).toHaveTextContent('Abertos');
    expect(screen.queryByRole('button', { name: 'Restaurar padrão' })).not.toBeInTheDocument();
  });

  it('escolher um sistema no filtro acrescenta o chip dele e filtra a consulta', async () => {
    const urls: string[] = [];
    stub(urls);
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/findings']}>
          <FindingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText('SQL por concatenação');
    fireEvent.change(screen.getByLabelText('Severidade'), { target: { value: 'all' } });
    expect(screen.getByLabelText('Filtros ativos')).not.toHaveTextContent('Crítica e alta');
    await vi.waitFor(() => expect(urls.some((u) => u.startsWith('/api/v1/findings?') && !u.includes('severity='))).toBe(true));
  });

  it('suprime vários achados de uma vez pela seleção', async () => {
    const calls: { url: string; body: string }[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const u = String(url);
        if (u === '/api/v1/findings/suppress-batch') {
          calls.push({ url: u, body: String(init?.body) });
          return new Response(JSON.stringify({ requested: 2, suppressed: 2, failures: [] }), { status: 200 });
        }
        const body = u.startsWith('/api/v1/findings?')
          ? {
              total: 2,
              bySystem: [],
              items: [
                { systemId: 1, systemName: 'a', auditId: 10, finding: finding(1, 'SQL por concatenação') },
                { systemId: 2, systemName: 'b', auditId: 20, finding: finding(2, 'CORS aberto') },
              ],
            }
          : [];
        return new Response(JSON.stringify(body), { status: 200 });
      }),
    );
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/findings']}>
          <FindingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText('SQL por concatenação');
    fireEvent.click(screen.getByRole('checkbox', { name: 'Selecionar todos os findings desta lista' }));
    expect(screen.getByText('2 selecionado(s)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Suprimir selecionados…' }));
    fireEvent.change(screen.getByLabelText(/Justificativa/), { target: { value: 'ferramenta errou nos dois casos' } });
    fireEvent.click(screen.getByRole('button', { name: 'Suprimir 2' }));
    await vi.waitFor(() => expect(calls).toHaveLength(1));
    const sent = JSON.parse(calls[0].body);
    expect(sent.findingIds).toEqual([1, 2]);
    expect(sent.reason).toBe('FALSE_POSITIVE');
  });
});
