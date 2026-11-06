import { describe, expect, it } from 'vitest';
import { HIGH_ALERT_THRESHOLD, edgeColor, findingsCrumbs, reviewSystemIds } from './findingsRows';

describe('findingsRows', () => {
  it('na auditoria aberta, as sugestões são as do sistema dela', () => {
    expect(reviewSystemIds({ auditMode: true, auditSystemId: 4, systemId: 9, items: [] })).toEqual([4]);
    expect(reviewSystemIds({ auditMode: true, auditSystemId: undefined, systemId: 9, items: [] })).toEqual([]);
  });

  it('fora da auditoria, usa o sistema escolhido', () => {
    expect(reviewSystemIds({ auditMode: false, auditSystemId: undefined, systemId: 9, items: [] })).toEqual([9]);
  });

  it('migalhas de todos os sistemas não citam auditoria', () => {
    const labels = findingsCrumbs({ auditMode: false, audit: undefined, auditId: 0 }).map((c) => c.label);
    expect(labels).toEqual(['Início', 'Informações de Auditoria']);
  });

  it('migalhas da auditoria terminam em Achados', () => {
    const labels = findingsCrumbs({ auditMode: true, audit: undefined, auditId: 12 }).map((c) => c.label);
    expect(labels).toEqual(['Início', 'Sistemas', 'Auditoria #12', 'Achados']);
  });

  it('a borda fica vermelha com críticos ou com muitos altos', () => {
    expect(edgeColor(1, 0)).toBe('#da1e28');
    expect(edgeColor(0, HIGH_ALERT_THRESHOLD)).toBe('#da1e28');
    expect(edgeColor(0, 1)).toBe('#ff832b');
    expect(edgeColor(0, 0)).toBe('#198038');
  });
});
