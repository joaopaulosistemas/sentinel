import { describe, expect, it } from 'vitest';
import type { FindingResponse } from '../types';
import { activeFilterChips, groupBySystem } from './findingsView';

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

  it('cria um chip por filtro ligado e nenhum sem filtros', () => {
    expect(activeFilterChips({})).toEqual([]);
    expect(
      activeFilterChips({ systemName: 'conta', severity: 'CRITICAL', status: 'FIXED', onlyNew: true, includeSuppressed: false }).map(
        (c) => c.label,
      ),
    ).toEqual(['Sistema: conta', 'Severidade: Crítico', 'Status: Resolvido', 'Somente novos']);
  });
});
