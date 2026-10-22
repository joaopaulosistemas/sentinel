import { describe, expect, it } from 'vitest';
import type { FindingResponse } from '../types';
import { activeFilterChips, footerText, groupBySystem } from './findingsView';

const f = (id: number) => ({ id }) as FindingResponse;

describe('findingsView', () => {
  it('agrupa por sistema na ordem da primeira aparição', () => {
    const groups = groupBySystem([
      { systemId: 2, systemName: 'portal', auditId: 20, finding: f(1) },
      { systemId: 1, systemName: 'conta', auditId: 10, finding: f(2) },
      { systemId: 2, systemName: 'portal', auditId: 20, finding: f(3) },
    ]);
    expect(groups.map((g) => [g.systemName, g.findings.map((x) => x.id)])).toEqual([
      ['portal', [1, 3]],
      ['conta', [2]],
    ]);
    expect(groupBySystem([])).toEqual([]);
  });

  it('mostra os chips do estado padrão e acrescenta categoria e sistema', () => {
    const base = { showSystem: true, severity: 'critHigh' as const, situation: 'open' as const };
    expect(activeFilterChips(base).map((c) => c.label)).toEqual(['Todos os sistemas', 'Crítica e alta', 'Abertos']);
    expect(
      activeFilterChips({ ...base, systemName: 'conta', category: 'SECURITY', situation: 'new' }).map((c) => [c.key, c.label]),
    ).toEqual([
      ['system', 'conta'],
      ['severity', 'Crítica e alta'],
      ['category', 'Segurança'],
      ['situation', 'Novos desde a última auditoria'],
    ]);
  });

  it('"Todas" não gera chip e o chip do sistema some quando não se aplica', () => {
    expect(activeFilterChips({ showSystem: false, severity: 'all', situation: 'all' })).toEqual([]);
  });

  it('monta o texto do rodapé', () => {
    expect(footerText(9, 142, 'open', 'critHigh', 4)).toBe('Mostrando 9 de 142 abertos (críticos e altos) em 4 sistemas');
    expect(footerText(1, 1, 'all', 'all', 1)).toBe('Mostrando 1 de 1 achados em 1 sistema');
    expect(footerText(2, 2, 'suppressed', 'HIGH', 0)).toBe('Mostrando 2 de 2 suprimidos (altos)');
  });
});
