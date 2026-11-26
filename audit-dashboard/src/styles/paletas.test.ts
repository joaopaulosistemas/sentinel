import { describe, expect, it } from 'vitest';
import css from './paletas.css?raw';

function bloco(seletor: string): Record<string, string> {
  const inicio = css.indexOf(seletor);
  expect(inicio, `bloco ${seletor}`).toBeGreaterThan(-1);
  const corpo = css.slice(css.indexOf('{', inicio) + 1, css.indexOf('}', inicio));
  return Object.fromEntries([...corpo.matchAll(/(--s-[\w-]+):\s*(#[0-9a-f]{6})/gi)].map((m) => [m[1], m[2]]));
}

function luminancia(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const contraste = (a: string, b: string) => {
  const [claro, escuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x);
  return (claro + 0.05) / (escuro + 0.05);
};

describe('paleta "mais suave" (tema claro)', () => {
  const v = bloco(":root[data-theme='light'][data-palette='mais-suave']");
  const fundos = ['--s-bg', '--s-surface', '--s-header-row', '--s-selected'];

  it('é mais escura que o clássico e o suave: sem branco no cartão', () => {
    const suave = bloco(":root[data-theme='light'][data-palette='suave']");
    expect(luminancia(v['--s-surface'])).toBeLessThan(luminancia(suave['--s-surface']));
    expect(luminancia(v['--s-surface'])).toBeLessThan(luminancia('#ffffff'));
    expect(v['--s-surface']).not.toBe('#ffffff');
  });

  it.each(['--s-text', '--s-text-2'])('o texto de corpo %s passa de 7:1 em todos os fundos', (token) => {
    for (const fundo of fundos) expect(contraste(v[token], v[fundo]), `${token} em ${fundo}`).toBeGreaterThanOrEqual(7);
  });

  it.each(['--s-text-3', '--s-text-4', '--s-link', '--s-danger-text', '--s-success-text', '--s-warn-text', '--s-crit-text', '--s-high-text'])(
    'o texto %s passa de 4,5:1 em todos os fundos',
    (token) => {
      for (const fundo of fundos) expect(contraste(v[token], v[fundo]), `${token} em ${fundo}`).toBeGreaterThanOrEqual(4.5);
    },
  );

  it('texto das etiquetas de status passa de 4,5:1 no próprio fundo', () => {
    expect(contraste(v['--s-success-text'], v['--s-good-bg'])).toBeGreaterThanOrEqual(4.5);
    expect(contraste(v['--s-danger-text'], v['--s-bad-bg'])).toBeGreaterThanOrEqual(4.5);
    expect(contraste(v['--s-warn-text'], v['--s-warn-bg'])).toBeGreaterThanOrEqual(4.5);
  });

  it('texto branco sobre o botão primário passa de 4,5:1 e a borda forte passa de 3:1 (componentes)', () => {
    expect(contraste('#ffffff', v['--s-primary'])).toBeGreaterThanOrEqual(4.5);
    expect(contraste(v['--s-border-strong'], v['--s-surface'])).toBeGreaterThanOrEqual(3);
  });
});

describe('paleta "mais suave" (tema escuro)', () => {
  it('usa o mesmo bloco do suave: o escuro não muda', () => {
    expect(css).toMatch(/:root\[data-theme='dark'\]\[data-palette='suave'\],\s*:root\[data-theme='dark'\]\[data-palette='mais-suave'\]\s*\{/);
  });

  it('cabeçalho e Carbon seguem as variáveis da paleta nova também', () => {
    expect(css).toContain(":root[data-palette='mais-suave'] .cds--header");
    expect(css).toContain(":root[data-palette='mais-suave'] .sentinel-content-theme");
  });
});
