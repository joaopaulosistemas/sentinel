import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useThemeStore } from '../store/useThemeStore';
import { AuthContext, SEM_LOGIN, type IAuth } from './AuthContext';
import { inicial, papelDe, rotuloDoPapel, UserMenu } from './UserMenu';

const logout = vi.fn();
const ADMIN: IAuth = { enabled: true, username: 'admin', roles: ['sentinel-admin'], admin: true, canExecute: true, logout };
const EXECUTOR: IAuth = { enabled: true, username: 'executor', roles: ['sentinel-executor'], admin: false, canExecute: true, logout };

function renderMenu(auth: IAuth) {
  return render(
    <AuthContext.Provider value={auth}>
      <UserMenu />
    </AuthContext.Provider>,
  );
}

beforeEach(() => {
  logout.mockClear();
  useThemeStore.getState().setPalette('suave');
  useThemeStore.getState().setTextSize('pequeno');
});
afterEach(() => window.localStorage.clear());

describe('funções do menu do usuário', () => {
  it('papel e rótulo vêm das permissões', () => {
    expect(papelDe(true, true)).toBe('admin');
    expect(papelDe(false, true)).toBe('executor');
    expect(papelDe(false, false)).toBe('nenhum');
    expect(rotuloDoPapel(false, false)).toBe('Sem papel no Sentinel');
  });

  it('a inicial do avatar sai do nome, e "?" se vier vazio', () => {
    expect(inicial('executor')).toBe('E');
    expect(inicial('  maria')).toBe('M');
    expect(inicial('')).toBe('?');
  });
});

describe('menu do usuário', () => {
  it('fechado mostra só o botão, com o papel; o menu abre e fecha pelo botão', () => {
    renderMenu(ADMIN);
    expect(screen.getByText('Administrador')).toBeTruthy();
    expect(screen.queryByRole('group', { name: 'Menu do usuário' })).toBeNull();

    const botao = screen.getByRole('button', { name: 'Menu de admin' });
    expect(botao.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(botao);
    expect(botao.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByRole('group', { name: 'Menu do usuário' })).toBeTruthy();
    fireEvent.click(botao);
    expect(screen.queryByRole('group', { name: 'Menu do usuário' })).toBeNull();
  });

  it('o executor aparece com o papel Executor', () => {
    renderMenu(EXECUTOR);
    expect(screen.getByText('Executor')).toBeTruthy();
    expect(screen.queryByText('Administrador')).toBeNull();
  });

  it('Esc e clique fora fecham o menu', () => {
    renderMenu(ADMIN);
    fireEvent.click(screen.getByRole('button', { name: 'Menu de admin' }));
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('group', { name: 'Menu do usuário' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Menu de admin' }));
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole('group', { name: 'Menu do usuário' })).toBeNull();
  });

  it('o tamanho do texto troca pelos botões e lembra a escolha', () => {
    renderMenu(ADMIN);
    fireEvent.click(screen.getByRole('button', { name: 'Menu de admin' }));
    expect(screen.getByRole('button', { name: 'Pequeno' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Grande' }));
    expect(useThemeStore.getState().textSize).toBe('grande');
    expect(screen.getByRole('button', { name: 'Grande' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Pequeno' }).getAttribute('aria-pressed')).toBe('false');
    expect(window.localStorage.getItem('sentinel.textsize')).toBe('grande');
  });

  it('a paleta troca pela caixa de seleção', () => {
    renderMenu(ADMIN);
    fireEvent.click(screen.getByRole('button', { name: 'Menu de admin' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Paleta de cores' }), { target: { value: 'alternativo' } });
    expect(useThemeStore.getState().palette).toBe('alternativo');
  });

  it('Sair chama o logout e fecha o menu', () => {
    renderMenu(EXECUTOR);
    fireEvent.click(screen.getByRole('button', { name: 'Menu de executor' }));
    fireEvent.click(screen.getByRole('button', { name: 'Sair' }));
    expect(logout).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('group', { name: 'Menu do usuário' })).toBeNull();
  });

  it('sem login vira só "Aparência": sem nome, papel nem Sair', () => {
    renderMenu(SEM_LOGIN);
    fireEvent.click(screen.getByRole('button', { name: 'Aparência' }));
    expect(screen.getByRole('combobox', { name: 'Paleta de cores' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Normal' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Sair' })).toBeNull();
    expect(screen.queryByText('Administrador')).toBeNull();
  });
});
