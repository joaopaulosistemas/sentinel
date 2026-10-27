import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useThemeStore } from '../../store/useThemeStore';
import { AppShell } from './AppShell';

function renderShell() {
  render(
    <MemoryRouter>
      <AppShell>
        <p>conteúdo</p>
      </AppShell>
    </MemoryRouter>,
  );
}

beforeEach(() => useThemeStore.getState().setMode('light'));
afterEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
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
});
