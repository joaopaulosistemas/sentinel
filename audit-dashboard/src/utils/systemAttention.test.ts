import { describe, expect, it } from 'vitest';
import type { SystemSummaryResponse } from '../types';
import { daysSinceAudit, isStale, systemsNeedingAttention } from './systemAttention';

const NOW = Date.parse('2026-10-22T12:00:00Z');
const day = (n: number) => new Date(NOW - n * 86_400_000).toISOString();

const sys = (id: number, name: string, over: Partial<SystemSummaryResponse> = {}): SystemSummaryResponse => ({
  id, name, stack: 'JAVA', team: 'T', lastAuditScore: 90, lastAuditDate: day(1), openCritical: 0, openHigh: 0, ...over,
});

describe('sistemas que precisam de atenção', () => {
  it('conta os dias desde a última auditoria e trata nunca auditado', () => {
    expect(daysSinceAudit(sys(1, 'a', { lastAuditDate: day(3) }), NOW)).toBe(3);
    expect(daysSinceAudit(sys(1, 'a', { lastAuditDate: null }), NOW)).toBeNull();
    expect(isStale(sys(1, 'a', { lastAuditDate: day(5) }), NOW)).toBe(false);
    expect(isStale(sys(1, 'a', { lastAuditDate: day(6) }), NOW)).toBe(true);
    expect(isStale(sys(1, 'a', { lastAuditDate: null }), NOW)).toBe(true);
  });

  it('ignora quem está bem e lista os motivos de quem não está', () => {
    const list = systemsNeedingAttention(
      [
        sys(1, 'ok'),
        sys(2, 'critico', { openCritical: 1, lastAuditScore: 60 }),
        sys(3, 'velho', { lastAuditDate: day(9) }),
        sys(4, 'novo', { lastAuditScore: null, lastAuditDate: null }),
      ],
      NOW,
    );
    expect(list.map((i) => i.system.name)).toEqual(['critico', 'novo', 'velho']);
    expect(list[0].reasons).toEqual(['1 crítico aberto', 'nota 60, abaixo da meta 75']);
    expect(list[1].reasons).toEqual(['nunca auditado']);
    expect(list[2].reasons).toEqual(['sem auditar há 9 dias']);
  });

  it('desempata por nome e pluraliza críticos', () => {
    const list = systemsNeedingAttention([sys(1, 'b', { openCritical: 2 }), sys(2, 'a', { openCritical: 2 })], NOW);
    expect(list.map((i) => i.system.name)).toEqual(['a', 'b']);
    expect(list[0].reasons[0]).toBe('2 críticos abertos');
  });

  it('lista vazia quando está tudo em ordem', () => {
    expect(systemsNeedingAttention([sys(1, 'ok')], NOW)).toEqual([]);
  });
});
