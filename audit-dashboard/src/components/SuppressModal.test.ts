import { describe, expect, it } from 'vitest';
import type { IFindingResponse } from '../types';
import { commonBody, justificationTooShort, MIN_JUSTIFICATION_LENGTH, primaryLabel, similarTo } from './SuppressModal';

const f = ({ id, ruleId, status = 'OPEN', tool = 'Semgrep' }: { id: number; ruleId: string; status?: string; tool?: string }) =>
  ({ id, ruleId, status, toolSource: tool }) as IFindingResponse;

describe('similarTo', () => {
  it('traz os abertos da mesma regra e ferramenta, fora da seleção', () => {
    const sel = [f({ id: 1, ruleId: 'a' })];
    const all = [f({ id: 1, ruleId: 'a' }), f({ id: 2, ruleId: 'a' }), f({ id: 3, ruleId: 'b' }), f({ id: 4, ruleId: 'a', status: 'SUPPRESSED' }), f({ id: 5, ruleId: 'a', status: 'OPEN', tool: 'Trivy' })];
    expect(similarTo(sel, all).map((x) => x.id)).toEqual([2]);
  });
  it('sem regra não há parecidos', () => {
    expect(similarTo([f({ id: 1, ruleId: '' })], [f({ id: 2, ruleId: '' })])).toEqual([]);
  });
});

describe('justificativa', () => {
  it('exige o mínimo de caracteres, sem contar espaços nas pontas', () => {
    expect(justificationTooShort('x'.repeat(MIN_JUSTIFICATION_LENGTH - 1))).toBe(true);
    expect(justificationTooShort(`  ${'x'.repeat(MIN_JUSTIFICATION_LENGTH - 1)}  `)).toBe(true);
    expect(justificationTooShort('x'.repeat(MIN_JUSTIFICATION_LENGTH))).toBe(false);
  });
});

describe('primaryLabel', () => {
  it('mostra "Salvando…" enquanto grava', () => {
    expect(primaryLabel({ pending: true, bulk: true, total: 3 })).toBe('Salvando…');
  });
  it('mostra a quantidade quando são vários', () => {
    expect(primaryLabel({ pending: false, bulk: true, total: 3 })).toBe('Suprimir 3');
  });
  it('mostra só "Suprimir" quando é um', () => {
    expect(primaryLabel({ pending: false, bulk: false, total: 1 })).toBe('Suprimir');
  });
});

describe('commonBody', () => {
  it('apara a justificativa, omite autor vazio e vale até o fim do dia da validade', () => {
    const body = commonBody({ reason: 'FALSE_POSITIVE', justification: '  motivo longo  ', createdBy: ' ', expires: '2030-01-31' });
    expect(body).toEqual({ reason: 'FALSE_POSITIVE', justification: 'motivo longo', createdBy: undefined, expiresAt: '2030-01-31T23:59:59' });
  });
  it('sem validade, expiresAt é nulo', () => {
    expect(commonBody({ reason: 'FALSE_POSITIVE', justification: 'x', createdBy: 'ana', expires: '' })).toMatchObject({ createdBy: 'ana', expiresAt: null });
  });
});
