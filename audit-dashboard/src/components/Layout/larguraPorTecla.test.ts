import { describe, expect, it } from 'vitest';
import { MENU_MIN, MENU_PADRAO, MENU_TRILHO } from '../../utils/menuWidth';
import { larguraPorTecla } from './AppShell';

const MAX = 480;

describe('larguraPorTecla', () => {
  it('seta para a direita alarga de 16 em 16 (48 com Shift) até o máximo', () => {
    expect(larguraPorTecla({ key: 'ArrowRight', shift: false, atual: 256, max: MAX })).toBe(272);
    expect(larguraPorTecla({ key: 'ArrowRight', shift: true, atual: 256, max: MAX })).toBe(304);
    expect(larguraPorTecla({ key: 'ArrowRight', shift: true, atual: 470, max: MAX })).toBe(MAX);
  });

  it('seta para a direita no trilho abre no tamanho mínimo', () => {
    expect(larguraPorTecla({ key: 'ArrowRight', shift: false, atual: MENU_TRILHO, max: MAX })).toBe(MENU_MIN);
  });

  it('seta para a esquerda estreita e recolhe quando passa do mínimo', () => {
    expect(larguraPorTecla({ key: 'ArrowLeft', shift: false, atual: 256, max: MAX })).toBe(240);
    expect(larguraPorTecla({ key: 'ArrowLeft', shift: false, atual: MENU_MIN, max: MAX })).toBe(0);
  });

  it('Home, End e Enter levam ao início, ao máximo e alternam trilho e padrão', () => {
    expect(larguraPorTecla({ key: 'Home', shift: false, atual: 256, max: MAX })).toBe(0);
    expect(larguraPorTecla({ key: 'End', shift: false, atual: 256, max: MAX })).toBe(MAX);
    expect(larguraPorTecla({ key: 'Enter', shift: false, atual: MENU_TRILHO, max: MAX })).toBe(MENU_PADRAO);
    expect(larguraPorTecla({ key: 'Enter', shift: false, atual: 256, max: MAX })).toBe(MENU_TRILHO);
  });

  it('ignora teclas que não são do separador', () => {
    expect(larguraPorTecla({ key: 'a', shift: false, atual: 256, max: MAX })).toBeNull();
  });
});
