import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { IFindingResponse } from '../types';
import { FindingsTable } from './FindingsTable';

const finding = (id: number, ruleId: string): IFindingResponse => ({
  id, category: 'SECURITY', severity: 'HIGH', title: `Achado ${id}`, description: '', filePath: 'A.java', lineNumber: id,
  codeSnippet: null, ruleId, toolSource: 'semgrep', cveId: null, aiRecommendation: null, status: 'OPEN',
});

describe('FindingsTable', () => {
  it('mostra "Claude sugeriu" só nos achados que têm sugestão', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <FindingsTable
            findings={[finding(1, 'a.sql-injection'), finding(2, 'a.outra')]}
            claudeNoteOf={(f) => (f.id === 1 ? { title: 'Revisar a concatenação' } : undefined)}
          />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const badges = screen.getAllByText('Claude sugeriu');
    expect(badges).toHaveLength(1);
    expect(badges[0]).toHaveAttribute('title', expect.stringContaining('Revisar a concatenação'));
  });

  const open = (f: IFindingResponse) => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter><FindingsTable findings={[f]} /></MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: `Expandir ${f.title}` }));
  };

  it('ao abrir o achado mostra o código real do arquivo, com número de linha e a linha do achado marcada', () => {
    open({ ...finding(7, 'a.sql'), filePath: 'src/ContaRepository.java', lineNumber: 9, codeSnippet: 'public Conta buscar(long id)',
      codeContext: 'linha A\nlinha B\nlinha C', codeContextStart: 8 });

    const code = screen.getByRole('region', { name: 'Código de src/ContaRepository.java:9' });
    expect(within(code).getByText('8')).toBeInTheDocument();
    expect(within(code).getByText('linha B')).toBeInTheDocument();
    expect(within(code).getByText(/\(linha do achado\)/)).toBeInTheDocument();
    expect(screen.queryByText('public Conta buscar(long id)')).not.toBeInTheDocument();
  });

  it('sem contexto de código (auditoria antiga ou segredo) segue mostrando o trecho de antes', () => {
    open({ ...finding(8, 'a.x'), codeSnippet: 'String sql = "SELECT " + id;', codeContext: null, codeContextStart: null });

    expect(screen.getByText('String sql = "SELECT " + id;')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /^Código de/ })).not.toBeInTheDocument();
  });
});
