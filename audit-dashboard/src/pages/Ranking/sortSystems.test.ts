import { describe, expect, it } from 'vitest';
import type { SystemSummaryResponse } from '../../types';
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
});
