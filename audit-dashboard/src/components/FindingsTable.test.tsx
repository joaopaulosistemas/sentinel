import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { FindingResponse } from '../types';
import { FindingsTable } from './FindingsTable';

const finding = (id: number, ruleId: string): FindingResponse => ({
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
});
