import { describe, expect, it } from 'vitest';

/** Guarda de regressão: toda tabela das telas tem nome acessível e todo cabeçalho de coluna tem escopo. */
const fontes = import.meta.glob(['../**/*.tsx', '!../**/*.test.tsx'], { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
const arquivos = Object.entries(fontes);

describe('acessibilidade das tabelas', () => {
  it('encontra as telas', () => {
    expect(arquivos.length).toBeGreaterThan(30);
  });

  it('todo <th> declara scope', () => {
    const faltando = arquivos.flatMap(([f, src]) =>
      [...src.matchAll(/<th(?![a-zA-Z])([^>]*)>/g)].filter((m) => !/scope=/.test(m[1])).map(() => f));
    expect(faltando).toEqual([]);
  });

  it('toda <table> tem aria-label ou <caption>', () => {
    const faltando = arquivos.flatMap(([f, src]) =>
      [...src.matchAll(/<table(?![a-zA-Z])([^>]*)>/g)].filter((m) => !/aria-label|aria-labelledby/.test(m[1]) && !src.includes('<caption')).map(() => f));
    expect(faltando).toEqual([]);
  });
});
