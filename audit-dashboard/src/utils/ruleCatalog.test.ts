import { describe, expect, it } from 'vitest';
import type { FixPlanItem } from '../types';
import { applyBulk, buildRuleRows, estimatedGain, filterRuleRows, shortRuleId } from './ruleCatalog';

const plan = (ruleId: string, scoreGain: number): FixPlanItem => ({
  ruleId, title: `t-${ruleId}`, severity: 'HIGH', category: 'SECURITY', occurrences: 2, scoreGain, exampleFile: null, exampleLine: null, howToFix: null,
});

describe('ruleCatalog', () => {
  it('usa o id curto da regra, como o motor', () => {
    expect(shortRuleId('Semgrep.Rules.Sentinel-Crypto')).toBe('sentinel-crypto');
    expect(shortRuleId('sentinel-x')).toBe('sentinel-x');
  });

  it('junta catálogo, regras vistas e plano numa linha por regra', () => {
    const rows = buildRuleRows({
      catalog: [{ id: 'a-regra', pack: 'core', message: 'Texto', severity: 'ERROR', category: 'security' }, { id: 'b-regra', pack: 'core', message: null, severity: null, category: null }],
      known: [{ id: 'audit.packs.a-regra', count: 3 }],
      profileRules: [{ id: 'b-regra', severity: 'LOW', disabled: false, shadow: null }],
      adjusted: [{ id: 'so-ajuste', severity: null, disabled: true, shadow: null }],
      plan: [plan('audit.packs.a-regra', 2.5), plan('so-no-plano', 1)],
    });
    const byId = Object.fromEntries(rows.map((r) => [r.id, r]));
    expect(Object.keys(byId).sort()).toEqual(['a-regra', 'b-regra', 'so-ajuste', 'so-no-plano']);
    expect(byId['a-regra']).toMatchObject({ title: 'Texto', pack: 'core', count: 3, gain: 2.5 });
    expect(byId['b-regra'].inProfile).toBe(true);
    expect(byId['so-no-plano'].title).toBe('t-so-no-plano');
  });

  it('funciona sem catálogo (API antiga)', () => {
    const rows = buildRuleRows({ catalog: undefined, known: [{ id: 'x', count: 1 }], profileRules: [], adjusted: [], plan: [] });
    expect(rows).toHaveLength(1);
  });

  it('filtra por texto, escopo e pacote, com as ajustadas primeiro', () => {
    const rows = buildRuleRows({
      catalog: [
        { id: 'alpha', pack: 'p1', message: 'Senha em texto', severity: null, category: null },
        { id: 'beta', pack: 'p2', message: 'Outra coisa', severity: null, category: null },
      ],
      known: [{ id: 'beta', count: 5 }], profileRules: [], adjusted: [], plan: [],
    });
    const adjusted = new Set(['alpha']);
    expect(filterRuleRows(rows, { query: '', scope: 'all', pack: '' }, adjusted).map((r) => r.id)).toEqual(['alpha', 'beta']);
    expect(filterRuleRows(rows, { query: 'senha', scope: 'all', pack: '' }, adjusted).map((r) => r.id)).toEqual(['alpha']);
    expect(filterRuleRows(rows, { query: '', scope: 'seen', pack: '' }, adjusted).map((r) => r.id)).toEqual(['beta']);
    expect(filterRuleRows(rows, { query: '', scope: 'adjusted', pack: '' }, adjusted).map((r) => r.id)).toEqual(['alpha']);
    expect(filterRuleRows(rows, { query: '', scope: 'all', pack: 'p2' }, adjusted).map((r) => r.id)).toEqual(['beta']);
  });

  it('aplica ações em lote e descarta o que não tem efeito', () => {
    const start = [{ id: 'a', severity: null, disabled: true, shadow: null }];
    const shadowed = applyBulk(start, ['a', 'b'], { kind: 'shadow', on: true });
    expect(shadowed.find((r) => r.id === 'b')).toMatchObject({ shadow: true });
    expect(applyBulk(shadowed, ['a'], { kind: 'disable', on: false }).find((r) => r.id === 'a')).toMatchObject({ disabled: false, shadow: true });
    expect(applyBulk(shadowed, ['a', 'b'], { kind: 'reset' })).toEqual([]);
    expect(applyBulk([], ['x'], { kind: 'severity', value: 'LOW' })).toEqual([{ id: 'x', severity: 'LOW', disabled: false, shadow: null }]);
  });

  it('soma o ganho só das regras escolhidas', () => {
    const rows = buildRuleRows({ catalog: [], known: [], profileRules: [], adjusted: [], plan: [plan('a', 2), plan('b', 3)] });
    expect(estimatedGain(rows, ['a'])).toBe(2);
    expect(estimatedGain(rows, ['a', 'b'])).toBe(5);
    expect(estimatedGain(rows, [])).toBe(0);
  });
});
