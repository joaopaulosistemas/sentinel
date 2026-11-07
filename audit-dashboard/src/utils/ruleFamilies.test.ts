import { describe, expect, it } from 'vitest';
import type { IEnginePackState } from '../types';
import type { IRuleRow } from './ruleCatalog';
import { analyzersBySide, familyOf, familyTabs, groupPacks, hiddenAdjustedCount, limitSide, packsBySide, presetOf, rowsOfActivePacks, ruleCountByPack, sideIsOff, sideOfPack, sideOfRule, sideTotals, sidesOffOf } from './ruleFamilies';

const pack = (id: string): IEnginePackState => ({ id, name: id, description: null, inProfile: true, active: true });
interface IRowFields {
  id: string;
  pack: string | null;
  category: string | null;
  count?: number;
}
const row = ({ id, pack, category, count = 0 }: IRowFields): IRuleRow => ({ id, title: null, pack, declared: null, category, count, gain: null, inProfile: false });

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
    const rows = [row({ id: 'a', pack: 'p', category: 'quality', count: 2 }), row({ id: 'b', pack: 'p', category: null }), row({ id: 'c', pack: 'p', category: 'security' }), row({ id: 'd', pack: 'p', category: 'security', count: 1 }), row({ id: 'e', pack: 'p', category: 'zeta' })];
    const tabs = familyTabs(rows, new Set(['c']));
    expect(tabs.map((t) => t.key)).toEqual(['security', 'quality', 'zeta', 'none']);
    expect(tabs[0]).toMatchObject({ name: 'Segurança', total: 2, seen: 1, adjusted: 1 });
    expect(tabs[2].name).toBe('Zeta');
  });

  it('esconde as regras de pacote desligado, mas mantém as sem pacote ou de pacote desconhecido', () => {
    const rows = [row({ id: 'a', pack: 'on', category: 'quality' }), row({ id: 'b', pack: 'off', category: 'quality' }), row({ id: 'c', pack: null, category: null }), row({ id: 'd', pack: 'fora-da-lista', category: null })];
    const packs = { on: true, off: false };
    expect(rowsOfActivePacks(rows, packs).map((r) => r.id)).toEqual(['a', 'c', 'd']);
    expect(hiddenAdjustedCount({ rows, packs, adjustedIds: new Set(['b', 'a']) })).toBe(1);
  });

  describe('lado da regra', () => {
    const c = ({ id, packId, side }: { id: string; packId: string; side?: 'back' | 'front' | 'common' }) => ({ id, pack: packId, message: null, severity: null, category: null, side });

    it('usa o lado da API e, sem ele, o do pacote', () => {
      expect(sideOfRule({ side: 'front', pack: 'core-security' })).toBe('front');
      expect(sideOfRule({ side: undefined, pack: 'core-quarkus' })).toBe('back');
      expect(sideOfRule({ side: undefined, pack: 'core-security' })).toBe('common');
      expect(sideOfRule({ side: undefined, pack: null })).toBe('common');
    });

    it('conta as regras por lado', () => {
      expect(sideTotals([c({ id: 'a', packId: 'p', side: 'back' }), c({ id: 'b', packId: 'p', side: 'back' }), c({ id: 'c', packId: 'q', side: 'front' }), c({ id: 'd', packId: 'r', side: 'common' })])).toEqual({ back: 2, front: 1, common: 1 });
      expect(sideTotals(undefined)).toEqual({ back: 0, front: 0, common: 0 });
    });

    it('um pacote misto aparece nos dois lados com a contagem de cada um', () => {
      const catalog = [c({ id: 'a', packId: 'core-security', side: 'back' }), c({ id: 'b', packId: 'core-security', side: 'back' }), c({ id: 'c', packId: 'core-security', side: 'front' }), c({ id: 'd', packId: 'core-iac', side: 'common' })];
      const groups = packsBySide([pack('core-security'), pack('core-iac')], catalog);
      expect(groups.map((g) => g.title)).toEqual(['Back-end', 'Front-end', 'Infra e comum']);
      expect(groups[0].packs[0]).toMatchObject({ count: 2, others: [{ side: 'front', count: 1 }] });
      expect(groups[1].packs[0]).toMatchObject({ count: 1, others: [{ side: 'back', count: 2 }] });
      expect(groups[0].rules).toBe(2);
    });

    it('pacote sem regras no catálogo cai no lado do mapa antigo e grupo vazio não aparece', () => {
      const groups = packsBySide([pack('java-google-style')], []);
      expect(groups.map((g) => g.title)).toEqual(['Back-end']);
    });

    it('agrupa analisadores por lado e manda os sem lado para o comum', () => {
      const a = (name: string, side?: 'back' | 'front' | 'common') => ({ name, description: '', disabled: false, side });
      const groups = analyzersBySide([a('PMD', 'back'), a('ESLint', 'front'), a('Gitleaks'), a('Trivy', 'common')]);
      expect(groups.map((g) => [g.side, g.items.map((i) => i.name)])).toEqual([['back', ['PMD']], ['front', ['ESLint']], ['common', ['Gitleaks', 'Trivy']]]);
    });

    it('atalhos de escopo e lado desligado', () => {
      expect(presetOf([])).toBe('full');
      expect(presetOf(['front'])).toBe('back');
      expect(presetOf(['back'])).toBe('front');
      expect(presetOf(['back', 'front'])).toBeNull();
      expect(sidesOffOf('back')).toEqual(['front']);
      expect(sidesOffOf('front')).toEqual(['back']);
      expect(sidesOffOf('full')).toEqual([]);
      expect(sideIsOff('front', ['front'])).toBe(true);
      expect(sideIsOff('common', ['front', 'back'])).toBe(false);
      expect(sideIsOff('back', ['front'])).toBe(false);
    });
  });
});
