import { describe, expect, it } from 'vitest';
import type { IEngineConfig, IRuleAdjustment } from '../types';
import { limitInvalid, patchAdjustment, pickAllShown, setWith, setWithout, sliceOf, unpickAllShown, withNewRule } from './engineTuning';

const rule = (id: string, extra: Partial<IRuleAdjustment> = {}): IRuleAdjustment => ({ id, severity: null, disabled: false, shadow: null, ...extra });

describe('limitInvalid', () => {
  it('vazio vale o padrão do motor', () => {
    expect(limitInvalid(undefined)).toBe(false);
    expect(limitInvalid('  ')).toBe(false);
  });
  it('exige inteiro maior ou igual a 1', () => {
    expect(limitInvalid('1')).toBe(false);
    expect(limitInvalid('0')).toBe(true);
    expect(limitInvalid('2.5')).toBe(true);
    expect(limitInvalid('abc')).toBe(true);
  });
});

describe('sliceOf', () => {
  const data = {
    packs: [
      { id: 'extra', inProfile: false },
      { id: 'base', inProfile: true },
    ],
  } as unknown as IEngineConfig;

  it('separa pacotes ligados e desligados, limites válidos, regras com efeito e lados', () => {
    const slice = sliceOf({
      data,
      packs: { extra: true, base: false },
      limits: { 'max-function-lines': '80', 'max-params': 'x', vazio: ' ' },
      rules: [rule('a', { disabled: true }), rule('sem-efeito')],
      sidesOff: ['front'],
    });
    expect(slice).toEqual({
      enablePacks: ['extra'],
      disablePacks: ['base'],
      thresholds: { 'max-function-lines': 80 },
      rules: [rule('a', { disabled: true })],
      disableSides: ['front'],
    });
  });
  it('sem lados informados, nenhum fica desligado', () => {
    expect(sliceOf({ data, packs: {}, limits: {}, rules: [] }).disableSides).toEqual([]);
  });
});

describe('patchAdjustment', () => {
  it('muda só a regra pedida, criando o ajuste se não existir', () => {
    const rules = [rule('a'), rule('b')];
    const next = patchAdjustment({ rules, id: 'b', patch: { disabled: true } });
    expect(next.find((r) => r.id === 'b')).toEqual(rule('b', { disabled: true }));
    expect(next.find((r) => r.id === 'a')).toEqual(rule('a'));
    expect(patchAdjustment({ rules: [], id: 'novo', patch: { severity: 'LOW' } })).toEqual([rule('novo', { severity: 'LOW' })]);
  });
});

describe('withNewRule', () => {
  it('entra só observada, em minúsculas e sem espaços', () => {
    expect(withNewRule([], '  Minha-Regra ')).toEqual([rule('minha-regra', { shadow: true })]);
  });
  it('aceita padrão com asterisco', () => {
    expect(withNewRule([], 'sentinel-*')).toHaveLength(1);
  });
  it('ignora vazio, caractere inválido e id repetido', () => {
    const rules = [rule('a')];
    expect(withNewRule(rules, '   ')).toBe(rules);
    expect(withNewRule(rules, 'tem espaço')).toBe(rules);
    expect(withNewRule(rules, 'A')).toBe(rules);
  });
});

describe('seleção de linhas', () => {
  it('setWith e setWithout devolvem cópias e não mexem no original', () => {
    const original = new Set(['a']);
    expect([...setWith(original, 'b')]).toEqual(['a', 'b']);
    expect([...setWithout(original, 'a')]).toEqual([]);
    expect([...original]).toEqual(['a']);
  });
  it('pickAllShown marca as mostradas e mantém as de fora da página', () => {
    expect([...pickAllShown(new Set(['fora']), ['a', 'b'])].sort()).toEqual(['a', 'b', 'fora']);
  });
  it('unpickAllShown desmarca só as mostradas', () => {
    expect([...unpickAllShown(new Set(['fora', 'a', 'b']), ['a', 'b'])]).toEqual(['fora']);
  });
});
