import { describe, expect, it } from 'vitest';
import { groupLimits, languageShares, testRatioPercent } from './projectLimits';

describe('groupLimits', () => {
  it('separa analisadores, áreas, alcance do mapa e o resto, mantendo a ordem', () => {
    const groups = groupLimits([
      'Segurança: autorização em tempo de execução',
      'Analisador Trivy não executou (falhou: tempo esgotado): o que ele cobriria não foi verificado',
      'Segurança: segredos fora do repositório',
      'Repositório muito grande: o mapa do projeto foi truncado em 5000 arquivos',
      'Desempenho sem medição',
    ]);
    expect(groups.map((g) => g.title)).toEqual([
      'Analisadores que não rodaram por inteiro',
      'Segurança',
      'Alcance do mapa do projeto',
      'Outros pontos',
    ]);
    expect(groups[0].items).toEqual(['Trivy não executou (falhou: tempo esgotado): o que ele cobriria não foi verificado']);
    expect(groups[1].items).toEqual(['autorização em tempo de execução', 'segredos fora do repositório']);
  });

  it('devolve lista vazia quando não há limites', () => {
    expect(groupLimits([])).toEqual([]);
  });
});

describe('languageShares', () => {
  it('ordena da maior para a menor e soma 100%', () => {
    const shares = languageShares({ TypeScript: 30, Java: 70 });
    expect(shares.map((s) => s.name)).toEqual(['Java', 'TypeScript']);
    expect(shares[0].percent).toBeCloseTo(70);
    expect(shares.reduce((sum, s) => sum + s.percent, 0)).toBeCloseTo(100);
  });

  it('agrupa o que passa de seis linguagens em "Outras"', () => {
    const shares = languageShares({ A: 9, B: 8, C: 7, D: 6, E: 5, F: 4, G: 3, H: 2 });
    expect(shares).toHaveLength(7);
    expect(shares[6]).toMatchObject({ name: 'Outras', files: 5 });
  });

  it('ignora linguagens sem arquivo e devolve vazio sem nenhuma', () => {
    expect(languageShares({ Java: 0 })).toEqual([]);
    expect(languageShares({})).toEqual([]);
  });
});

describe('testRatioPercent', () => {
  it('calcula testes por 100 arquivos de código', () => {
    expect(testRatioPercent(200, 50)).toBe(25);
  });

  it('não divide por zero', () => {
    expect(testRatioPercent(0, 10)).toBeNull();
  });
});
