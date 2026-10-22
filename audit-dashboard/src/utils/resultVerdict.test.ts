import { describe, expect, it } from 'vitest';
import type { FixPlan, RiskScoreDto } from '../types';
import { categoriesByScore, verdict } from './resultVerdict';

const plan = (over: Partial<FixPlan>): FixPlan => ({ scoreNow: 63, scoreIfCriticalFixed: 74, criticalCount: 3, items: [], ...over });

describe('veredito do resultado', () => {
  it('com críticos mostra quantos e o ganho de corrigir só eles', () => {
    const v = verdict(plan({}));
    expect(v.headline).toBe('3 problemas críticos para resolver primeiro');
    expect(v.detail).toBe('Corrigindo só os críticos, a nota estimada vai de 63 para 74 (meta: 75).');
    expect(v.tone).toBe('warn');
  });

  it('singular e meta atingida', () => {
    const v = verdict(plan({ criticalCount: 1, scoreIfCriticalFixed: 80 }));
    expect(v.headline).toBe('1 problema crítico para resolver primeiro');
    expect(v.detail).toContain('passa da meta de 75');
  });

  it('sem críticos aponta o maior ganho', () => {
    const v = verdict(
      plan({
        criticalCount: 0,
        scoreNow: 70,
        items: [{ ruleId: 'r', title: 'Hash fraco', severity: 'HIGH', category: 'SECURITY', occurrences: 2, scoreGain: 3, exampleFile: null, exampleLine: null, howToFix: null }],
      }),
    );
    expect(v.headline).toBe('Sem críticos, mas abaixo da meta');
    expect(v.detail).toBe('O maior ganho vem de "Hash fraco": a nota sobe 3.0 pontos.');
  });

  it('nota alta e nada a corrigir', () => {
    const v = verdict(plan({ criticalCount: 0, scoreNow: 100, items: [] }));
    expect(v.tone).toBe('good');
    expect(v.headline).toBe('Nada a corrigir');
  });

  it('categorias saem da pior para a melhor e ignoram dimensão ausente', () => {
    const score: RiskScoreDto = { overall: 60, security: 40, quality: 80, architecture: 60, dependencies: 20, observability: 90 };
    expect(categoriesByScore(score).map((c) => c.label)).toEqual(['Dependências', 'Segurança', 'Arquitetura', 'Qualidade', 'Observabilidade']);
    expect(categoriesByScore(null)).toEqual([]);
  });
});
