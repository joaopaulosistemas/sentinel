import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useThemeStore } from '../../store/useThemeStore';
import { AppShell } from './AppShell';

function renderShell() {
  render(
    <QueryClientProvider client={new QueryClient()}><MemoryRouter>
      <AppShell>
        <p>conteúdo</p>
      </AppShell>
    </MemoryRouter></QueryClientProvider>,
  );
}

beforeEach(() => {
  useThemeStore.getState().setPalette('suave');
  useThemeStore.getState().setMode('light');
});
afterEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  document.documentElement.removeAttribute('data-palette');
});

describe('troca de tema', () => {
  it('o botão do cabeçalho alterna entre o tema normal e o escuro, e lembra a escolha', () => {
    renderShell();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    fireEvent.click(screen.getByRole('button', { name: 'Trocar para o tema escuro' }));
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(window.localStorage.getItem('sentinel.theme')).toBe('dark');

    fireEvent.click(screen.getByRole('button', { name: 'Trocar para o tema normal' }));
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(window.localStorage.getItem('sentinel.theme')).toBe('light');
  });

  it('a caixa de paleta troca entre clássico, suave e alternativo, e lembra a escolha', () => {
    renderShell();
    const caixa = screen.getByRole('combobox', { name: 'Paleta de cores' });
    expect(document.documentElement.getAttribute('data-palette')).toBe('suave');

    fireEvent.change(caixa, { target: { value: 'alternativo' } });
    expect(document.documentElement.getAttribute('data-palette')).toBe('alternativo');
    expect(window.localStorage.getItem('sentinel.palette')).toBe('alternativo');

    fireEvent.change(caixa, { target: { value: 'classico' } });
    expect(document.documentElement.getAttribute('data-palette')).toBe('classico');
    // a paleta continua valendo ao trocar o tema claro/escuro
    fireEvent.click(screen.getByRole('button', { name: 'Trocar para o tema escuro' }));
    expect(document.documentElement.getAttribute('data-palette')).toBe('classico');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
