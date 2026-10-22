import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
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
});
