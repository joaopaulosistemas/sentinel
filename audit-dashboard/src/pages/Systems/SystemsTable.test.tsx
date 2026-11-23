import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { ISystemSummaryResponse } from '../../types';
import { SystemsTable } from './SystemsTable';

const NOW = Date.parse('2026-11-23T12:00:00Z');
const day = (n: number) => new Date(NOW - n * 86_400_000).toISOString();

const sys = (over: Partial<ISystemSummaryResponse> & { id: number; name: string }): ISystemSummaryResponse => ({
  stack: 'JAVA', team: 'T', lastAuditScore: 80, lastAuditDate: day(1), auditCount: 3, openCritical: 0, openHigh: 0, ...over,
});

function renderTable(systems: ISystemSummaryResponse[]) {
  render(
    <MemoryRouter>
      <SystemsTable systems={systems} selectedId={null} now={NOW} onAudit={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />
    </MemoryRouter>,
  );
}

const linha = (nome: string) => screen.getByRole('button', { name: nome }).closest('tr') as HTMLElement;

describe('SystemsTable: selo de auditoria antiga', () => {
  it('mostra o selo só nos sistemas há mais de 7 dias sem auditar, com o nível certo', () => {
    renderTable([
      sys({ id: 1, name: 'em-dia', lastAuditDate: day(7) }),
      sys({ id: 2, name: 'atencao', lastAuditDate: day(9) }),
      sys({ id: 3, name: 'critico', lastAuditDate: day(23) }),
      sys({ id: 4, name: 'novo', lastAuditDate: null, lastAuditScore: null, auditCount: 0 }),
    ]);
    expect(within(linha('em-dia')).queryByTestId('audit-age-badge')).toBeNull();
    expect(within(linha('atencao')).getByTestId('audit-age-badge')).toHaveTextContent('Sem auditar há 9 dias');
    expect(within(linha('atencao')).getByTestId('audit-age-badge')).toHaveAttribute('data-level', 'attention');
    expect(within(linha('critico')).getByTestId('audit-age-badge')).toHaveAttribute('data-level', 'critical');
    expect(within(linha('novo')).getByTestId('audit-age-badge')).toHaveTextContent('Nunca auditado');
  });
});
