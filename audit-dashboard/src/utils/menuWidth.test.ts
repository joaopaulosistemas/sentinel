import { afterEach, describe, expect, it } from 'vitest';
import { MENU_CHAVE, MENU_MIN, MENU_PADRAO, MENU_TRILHO, clampMenu, menuMaxFor, readMenuWidth, saveMenuWidth } from './menuWidth';

afterEach(() => window.localStorage.clear());

describe('menuWidth', () => {
  it('limita o máximo a 480 px e a 40% da janela', () => {
    expect(menuMaxFor(2000)).toBe(480);
    expect(menuMaxFor(1200)).toBe(480);
    expect(menuMaxFor(1056)).toBe(422);
    expect(menuMaxFor(300)).toBe(MENU_MIN);
  });
  it('encaixa em trilho abaixo de 140 e em 200 entre 140 e 200', () => {
    expect(clampMenu(100, 1600)).toBe(MENU_TRILHO);
    expect(clampMenu(139, 1600)).toBe(MENU_TRILHO);
    expect(clampMenu(150, 1600)).toBe(MENU_MIN);
    expect(clampMenu(300, 1600)).toBe(300);
    expect(clampMenu(900, 1600)).toBe(480);
    expect(clampMenu(NaN, 1600)).toBe(MENU_PADRAO);
  });
  it('lê e grava a escolha, com padrão quando não há nada ou o valor é inválido', () => {
    expect(readMenuWidth(1600)).toBe(MENU_PADRAO);
    saveMenuWidth(320);
    expect(window.localStorage.getItem(MENU_CHAVE)).toBe('320');
    expect(readMenuWidth(1600)).toBe(320);
    window.localStorage.setItem(MENU_CHAVE, 'lixo');
    expect(readMenuWidth(1600)).toBe(MENU_PADRAO);
  });
});
