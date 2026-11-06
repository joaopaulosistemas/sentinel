import { describe, expect, it } from 'vitest';
import type { ISystemSummaryResponse } from '../../types';
import { formatTrend } from '../../utils/ranking';
import { sortSystems } from './RankingPage';

interface ISysInput {
  id: number;
  name: string;
  score: number | null;
  critical: number;
}

const sys = ({ id, name, score, critical }: ISysInput): ISystemSummaryResponse => ({
  id, name, stack: 'JAVA', team: 'T', lastAuditScore: score, lastAuditDate: null, openCritical: critical, openHigh: 0,
});

const list = [sys({ id: 1, name: 'a', score: 80, critical: 0 }), sys({ id: 2, name: 'b', score: 40, critical: 2 }), sys({ id: 3, name: 'c', score: null, critical: 0 }), sys({ id: 4, name: 'd', score: 60, critical: 5 })];

describe('ordenação do ranking', () => {
  it('por nota põe a melhor primeiro e o nunca auditado por último', () => {
    expect(sortSystems(list, 'nota').map((s) => s.name)).toEqual(['a', 'd', 'b', 'c']);
  });
  it('por críticos põe o maior número primeiro', () => {
    expect(sortSystems(list, 'criticos').map((s) => s.name)).toEqual(['d', 'b', 'a', 'c']);
  });
  it('não altera a lista original', () => {
    sortSystems(list, 'nota');
    expect(list.map((s) => s.name)).toEqual(['a', 'b', 'c', 'd']);
  });

  it('por tendência põe os maiores ganhos primeiro e quem não tem histórico por último', () => {
    const withDelta = [
      { ...sys({ id: 1, name: 'a', score: 80, critical: 0 }), scoreDelta: 3 },
      { ...sys({ id: 2, name: 'b', score: 40, critical: 2 }), scoreDelta: -4 },
      sys({ id: 3, name: 'c', score: null, critical: 0 }),
      { ...sys({ id: 4, name: 'd', score: 60, critical: 5 }), scoreDelta: 0 },
    ];
    expect(sortSystems(withDelta, 'tendencia').map((s) => s.name)).toEqual(['a', 'd', 'b', 'c']);
  });
});

describe('formatTrend', () => {
  it('mostra subida, queda, estabilidade e falta de histórico', () => {
    expect(formatTrend(3)).toEqual({ text: '▲ +3', tone: 'up' });
    expect(formatTrend(-4.26)).toEqual({ text: '▼ -4.3', tone: 'down' });
    expect(formatTrend(0)).toEqual({ text: '— 0', tone: 'flat' });
    expect(formatTrend(0.04)).toEqual({ text: '— 0', tone: 'flat' });
    expect(formatTrend(null)).toEqual({ text: 'sem histórico', tone: 'none' });
    expect(formatTrend(undefined).tone).toBe('none');
  });
});
