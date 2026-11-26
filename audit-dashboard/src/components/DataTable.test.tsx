import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DataTable } from './DataTable';

describe('DataTable', () => {
  it('dá nome à tabela e escopo a todos os cabeçalhos de coluna', () => {
    render(
      <DataTable label="Auditorias recentes" columns={[{ label: 'Sistema' }, { label: 'Nota', align: 'right' }]}>
        <tr><td>conta-corrente-api</td><td>61</td></tr>
      </DataTable>,
    );
    const table = screen.getByRole('table', { name: 'Auditorias recentes' });
    const heads = within(table).getAllByRole('columnheader');
    expect(heads.map((h) => h.textContent)).toEqual(['Sistema', 'Nota']);
    expect(heads.every((h) => h.getAttribute('scope') === 'col')).toBe(true);
    expect(heads[1]).toHaveStyle({ textAlign: 'right' });
    expect(within(table).getByText('conta-corrente-api')).toBeInTheDocument();
  });

  it('o cabeçalho "faixa" tem fundo e o "linha" tem borda embaixo', () => {
    const { rerender } = render(<DataTable label="a" columns={[{ label: 'x' }]}><tr><td>1</td></tr></DataTable>);
    expect(screen.getAllByRole('row')[0].getAttribute('style')).toContain('--s-header-row');
    rerender(<DataTable label="a" header="linha" columns={[{ label: 'x' }]}><tr><td>1</td></tr></DataTable>);
    expect(screen.getAllByRole('row')[0].getAttribute('style')).toContain('border-bottom');
  });
});
