import { describe, expect, it } from 'vitest';
import type { SystemSummaryResponse } from '../../types';
import { formatTrend } from '../../utils/ranking';
import { sortSystems } from './RankingPage';

const sys = (id: number, name: string, score: number | null, critical: number): SystemSummaryResponse => ({
  id, name, stack: 'JAVA', team: 'T', lastAuditScore: score, lastAuditDate: null, openCritical: critical, openHigh: 0,
});

const list = [sys(1, 'a', 80, 0), sys(2, 'b', 40, 2), sys(3, 'c', null, 0), sys(4, 'd', 60, 5)];

describe('ordenação do ranking', () => {
  it('por nota põe o pior primeiro e o nunca auditado antes de todos', () => {
    expect(sortSystems(list, 'nota').map((s) => s.name)).toEqual(['c', 'b', 'd', 'a']);
  });
  it('por críticos põe o maior número primeiro', () => {
    expect(sortSystems(list, 'criticos').map((s) => s.name)).toEqual(['d', 'b', 'c', 'a']);
  });
  it('não altera a lista original', () => {
    sortSystems(list, 'nota');
    expect(list.map((s) => s.name)).toEqual(['a', 'b', 'c', 'd']);
  });

  it('por tendência põe as maiores quedas primeiro e quem não tem histórico por último', () => {
    const withDelta = [
      { ...sys(1, 'a', 80, 0), scoreDelta: 3 },
      { ...sys(2, 'b', 40, 2), scoreDelta: -4 },
      sys(3, 'c', null, 0),
      { ...sys(4, 'd', 60, 5), scoreDelta: 0 },
    ];
    expect(sortSystems(withDelta, 'tendencia').map((s) => s.name)).toEqual(['b', 'd', 'a', 'c']);
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
