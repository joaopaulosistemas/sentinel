import { describe, expect, it } from 'vitest';
import type { ScoreGroup } from '../types';
import { deltaLabel, groupHint, toggleRule } from './scoreExplanation';

const group = (over: Partial<ScoreGroup>): ScoreGroup => ({ ruleId: 'r', severity: 'MEDIUM', count: 3, raw: 15, counted: 15, capped: false, ...over });

describe('scoreExplanation', () => {
  it('liga e desliga regras sem repetir nem mutar', () => {
    const base = ['a'];
    expect(toggleRule(base, 'b')).toEqual(['a', 'b']);
    expect(toggleRule(['a', 'b'], 'a')).toEqual(['b']);
    expect(base).toEqual(['a']);
  });

  it('explica o teto, a severidade sem teto e a contagem simples', () => {
    expect(groupHint(group({ capped: true, count: 9, raw: 45, counted: 20 }))).toContain('só 20 entram');
    expect(groupHint(group({ severity: 'HIGH' }))).toContain('Sem teto');
    expect(groupHint(group({ count: 1, counted: 5 }))).toBe('1 ocorrência, 5 pontos.');
    expect(groupHint(group({ count: 1, counted: 1 }))).toBe('1 ocorrência, 1 ponto.');
  });

  it('mostra a diferença da nota com sinal e some quando é zero', () => {
    expect(deltaLabel(40, 43.25)).toBe('+3,3');
    expect(deltaLabel(40, 38)).toBe('−2,0');
    expect(deltaLabel(40, 40.01)).toBe('');
  });
});
