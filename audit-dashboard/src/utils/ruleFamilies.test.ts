import { describe, expect, it } from 'vitest';
import type { EnginePackState } from '../types';
import type { RuleRow } from './ruleCatalog';
import { familyOf, familyTabs, groupPacks, hiddenAdjustedCount, limitSide, rowsOfActivePacks, ruleCountByPack, sideOfPack } from './ruleFamilies';

const pack = (id: string): EnginePackState => ({ id, name: id, description: null, inProfile: true, active: true });
const row = (id: string, pack: string | null, category: string | null, count = 0): RuleRow => ({ id, title: null, pack, declared: null, category, count, gain: null, inProfile: false });

describe('ruleFamilies', () => {
  it('classifica os pacotes conhecidos por lado e manda o resto para transversais', () => {
    expect(sideOfPack('core-quarkus')).toBe('back');
    expect(sideOfPack('core-vue')).toBe('front');
    expect(sideOfPack('core-security')).toBe('both');
    expect(sideOfPack('pacote-da-casa')).toBe('both');
  });

  it('agrupa na ordem back, front, transversais e omite grupo vazio', () => {
    const groups = groupPacks([pack('core-security'), pack('core-vue'), pack('core-quarkus')]);
    expect(groups.map((g) => g.title)).toEqual(['Back-end', 'Front-end', 'Transversais']);
    expect(groupPacks([pack('core-vue')]).map((g) => g.title)).toEqual(['Front-end']);
  });

  it('só linhas por componente é limite de front-end', () => {
    expect(limitSide('max-component-lines')).toBe('front');
    for (const k of ['max-function-lines', 'max-parameters', 'max-complexity', 'max-nesting', 'max-findings']) expect(limitSide(k)).toBe('both');
  });

  it('conta as regras do catálogo por pacote', () => {
    expect(ruleCountByPack([{ id: 'a', pack: 'p', message: null, severity: null, category: null }, { id: 'b', pack: 'p', message: null, severity: null, category: null }])).toEqual({ p: 2 });
    expect(ruleCountByPack(undefined)).toEqual({});
  });

  it('família é a categoria da regra; sem categoria vira "Sem família"', () => {
    expect(familyOf({ category: 'Security' })).toBe('security');
    expect(familyOf({ category: null })).toBe('none');
  });

  it('monta uma aba por família, na ordem do peso na nota e com "Sem família" no fim', () => {
    const rows = [row('a', 'p', 'quality', 2), row('b', 'p', null), row('c', 'p', 'security'), row('d', 'p', 'security', 1), row('e', 'p', 'zeta')];
    const tabs = familyTabs(rows, new Set(['c']));
    expect(tabs.map((t) => t.key)).toEqual(['security', 'quality', 'zeta', 'none']);
    expect(tabs[0]).toMatchObject({ name: 'Segurança', total: 2, seen: 1, adjusted: 1 });
    expect(tabs[2].name).toBe('Zeta');
  });

  it('esconde as regras de pacote desligado, mas mantém as sem pacote ou de pacote desconhecido', () => {
    const rows = [row('a', 'on', 'quality'), row('b', 'off', 'quality'), row('c', null, null), row('d', 'fora-da-lista', null)];
    const packs = { on: true, off: false };
    expect(rowsOfActivePacks(rows, packs).map((r) => r.id)).toEqual(['a', 'c', 'd']);
    expect(hiddenAdjustedCount(rows, packs, new Set(['b', 'a']))).toBe(1);
  });
});
