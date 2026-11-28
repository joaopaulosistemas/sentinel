import { describe, expect, it } from 'vitest';
import css from './acoes.css?raw';

describe('acoes.css: estados dos botões', () => {
  it('desenha desabilitado, hover, pressionado e foco, sem esperar o estilo em linha', () => {
    expect(css).toMatch(/\.sentinel-btn:disabled/);
    expect(css).toMatch(/\.sentinel-btn:hover:not\(:disabled\)/);
    expect(css).toMatch(/\.sentinel-btn\[aria-pressed='true'\]/);
  });

  it('o desabilitado não parece clicável', () => {
    const bloco = /\.sentinel-btn:disabled[^{]*\{([^}]*)\}/.exec(css)?.[1] ?? '';
    expect(bloco).toContain('cursor: not-allowed');
    expect(bloco).toContain('var(--s-disabled)');
  });

  it('o pressionado tem contorno além do fundo (WCAG 1.4.11): a cor sozinha não basta', () => {
    const bloco = /\.sentinel-btn\[aria-pressed='true'\][^{]*\{([^}]*)\}/.exec(css)?.[1] ?? '';
    expect(bloco).toContain('box-shadow: inset 0 0 0 1px var(--s-link)');
    expect(bloco).toContain('font-weight: 600');
  });

  it('o movimento respeita prefers-reduced-motion', () => {
    expect(css).toMatch(/@media \(prefers-reduced-motion: no-preference\)[\s\S]*transition/);
  });

  it('as cores vêm dos tokens: o único hexadecimal é o texto sobre o botão principal', () => {
    const hexes = css.match(/#[0-9a-fA-F]{3,8}\b/g) ?? [];
    expect(hexes).toEqual(['#ffffff']);
  });
});
