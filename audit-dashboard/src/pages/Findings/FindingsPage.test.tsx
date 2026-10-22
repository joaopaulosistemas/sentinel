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
              { systemId: 1, systemName: 'conta-corrente-api', auditId: 10, count: 1 },
              { systemId: 2, systemName: 'portal-cooperado', auditId: 20, count: 1 },
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
    expect(screen.getAllByText('1 achados', { exact: false })).toHaveLength(2);
    expect(urls.some((u) => u.startsWith('/api/v1/findings?') && u.includes('limit=20') && u.includes('offset=0'))).toBe(true);
  });

  it('agrupa os achados por sistema quando o usuário pede', async () => {
    renderFindings();
    await screen.findByText('SQL por concatenação');
    expect(screen.queryByRole('region', { name: 'Achados de portal-cooperado' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('switch', { name: 'Agrupar' }));
    expect(await screen.findByRole('region', { name: 'Achados de conta-corrente-api' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Achados de portal-cooperado' })).toBeInTheDocument();
  });

  it('mostra um chip por filtro ativo e deixa limpar tudo', async () => {
    renderFindings('/findings?onlyNew=1');
    expect(await screen.findByText('Somente novos', { selector: '.cds--tag *, .cds--tag' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }));
    expect(screen.queryByRole('button', { name: 'Limpar filtros' })).not.toBeInTheDocument();
  });

  it('escolher um sistema acrescenta o chip dele', async () => {
    renderFindings();
    fireEvent.click(await screen.findByRole('button', { name: /portal-cooperado 1 achados/ }));
    expect(await screen.findByText('Sistema: portal-cooperado')).toBeInTheDocument();
  });
});
