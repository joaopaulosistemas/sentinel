import { describe, expect, it } from 'vitest';
import { codeLines, codeLocation } from './codeContext';

describe('codeLines', () => {
  it('numera a partir da linha inicial e marca só a linha do achado', () => {
    const lines = codeLines('a\nb\nc', 8, 9);
    expect(lines).toEqual([
      { n: 8, text: 'a', hit: false },
      { n: 9, text: 'b', hit: true },
      { n: 10, text: 'c', hit: false },
    ]);
  });

  it('sem contexto ou sem linha inicial não inventa linhas', () => {
    expect(codeLines(null, 1, 1)).toEqual([]);
    expect(codeLines('', 1, 1)).toEqual([]);
    expect(codeLines('a', null, 1)).toEqual([]);
    expect(codeLines('a', 0, 1)).toEqual([]);
  });

  it('a linha do achado fora da janela não marca nenhuma', () => {
    expect(codeLines('a\nb', 1, 50).some((l) => l.hit)).toBe(false);
  });
});

describe('codeLocation', () => {
  it('junta arquivo e linha; sem linha mostra só o arquivo', () => {
    expect(codeLocation('pom.xml', 63)).toBe('pom.xml:63');
    expect(codeLocation('pom.xml', 0)).toBe('pom.xml');
  });
});
