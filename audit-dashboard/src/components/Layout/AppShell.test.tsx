import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from './AppShell';
import pkg from '../../../package.json';

describe('AppShell', () => {
  it('mostra a versão do package.json no cabeçalho', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <p>conteúdo</p>
        </AppShell>
      </MemoryRouter>,
    );
    expect(screen.getByText(`v${pkg.version}`)).toBeInTheDocument();
    expect(screen.getByText('conteúdo')).toBeInTheDocument();
  });

  it('lista os itens principais de navegação', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <p>x</p>
        </AppShell>
      </MemoryRouter>,
    );
    const nav = screen.getByRole('navigation', { name: 'Navegação principal' });
    expect(within(nav).getAllByRole('link').map((a) => a.textContent)).toEqual([
      'Visão geral',
      'Sistemas',
      'Informações de Auditoria',
      'Ranking de Sistemas',
      'Relatórios',
    ]);
  });
});
