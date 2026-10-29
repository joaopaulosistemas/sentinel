import { describe, expect, it } from 'vitest';
import type { GuideRule, GuideStack } from '../types';
import { coverageOf, criteriaOf, filterRules, formatGrade, gradeOf, rulesOfStack } from './guide';

function rule(over: Partial<GuideRule>): GuideRule {
  return { id: 'sentinel-x', pack: 'core', title: 'Título', action: 'Faça', severity: 'media', cwe: '', before: '', after: '', ...over };
}

function stack(over: Partial<GuideStack> = {}): GuideStack {
  return {
    id: 'quarkus', name: 'Quarkus', kind: 'backend', summary: '', tools: [], gaps: [], commands: ['mvn verify'], commandsVerified: true,
    review: { docs: false, team: false, ai: false }, practices: [{ title: 'p', doIt: 'a', avoid: 'b' }], baseLanguage: false,
    ownRules: 7, inheritedRules: 90, rules: [{ id: 'sentinel-x', own: true }], ...over,
  };
}

describe('rulesOfStack', () => {
  it('junta a referência da tecnologia com os dados da regra e ignora id desconhecido', () => {
    const out = rulesOfStack(stack({ rules: [{ id: 'a', own: true }, { id: 'zzz', own: false }, { id: 'b', own: false }] }), [rule({ id: 'b' }), rule({ id: 'a' })]);
    expect(out.map((r) => [r.id, r.own])).toEqual([['a', true], ['b', false]]);
  });
});

describe('filterRules', () => {
  const rules = [
    rule({ id: 'r1', title: 'Beta', severity: 'baixa' }),
    rule({ id: 'r2', title: 'Alfa', severity: 'alta', cwe: 'CWE-79' }),
    rule({ id: 'r3', title: 'Gama', severity: 'alta', action: 'use parâmetros' }),
  ];
  it('ordena do mais grave para o menos grave e, no empate, por título', () => {
    expect(filterRules(rules, 'todas', '').map((r) => r.id)).toEqual(['r2', 'r3', 'r1']);
  });
  it('filtra por severidade', () => {
    expect(filterRules(rules, 'baixa', '').map((r) => r.id)).toEqual(['r1']);
  });
  it('busca no título, no id, no CWE e na ação, sem diferenciar maiúsculas', () => {
    expect(filterRules(rules, 'todas', 'cwe-79').map((r) => r.id)).toEqual(['r2']);
    expect(filterRules(rules, 'todas', 'PARÂMETROS').map((r) => r.id)).toEqual(['r3']);
    expect(filterRules(rules, 'todas', 'r1').map((r) => r.id)).toEqual(['r1']);
  });
});

describe('coverageOf', () => {
  it('conta as específicas, ou as herdadas quando é a linguagem base', () => {
    expect(coverageOf(stack({ ownRules: 7 })).label).toBe('Boa');
    expect(coverageOf(stack({ ownRules: 3 })).label).toBe('Parcial');
    expect(coverageOf(stack({ ownRules: 0 })).label).toBe('Só regras gerais');
    expect(coverageOf(stack({ ownRules: 0, baseLanguage: true, inheritedRules: 90 })).label).toBe('Boa');
  });
});

describe('nota do guia', () => {
  it('os critérios somam 10', () => {
    expect(criteriaOf(stack()).reduce((s, c) => s + c.points, 0)).toBe(10);
  });
  it('só conta o que foi feito: revisão de pessoas e teste com a IA começam pendentes', () => {
    expect(gradeOf(criteriaOf(stack()))).toBe(6.5);
  });
  it('chega a 10 quando tudo foi feito', () => {
    expect(gradeOf(criteriaOf(stack({ review: { docs: true, team: true, ai: true } })))).toBe(10);
  });
  it('sem comandos conferidos e com poucas regras perde pontos', () => {
    expect(gradeOf(criteriaOf(stack({ commandsVerified: false, ownRules: 2 })))).toBe(3.5);
  });
  it('formata com vírgula', () => {
    expect(formatGrade(6.5)).toBe('6,5');
  });
});
