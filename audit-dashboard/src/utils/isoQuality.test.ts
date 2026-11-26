import { describe, expect, it } from 'vitest';
import type { IIsoCharacteristic, IIsoTrendPoint } from '../types';
import { isoDelta, isoDeltaLabel, isoDeltaSr, isoSituacao, orderedCharacteristics } from './isoQuality';

const point = (notas: Record<string, number>): IIsoTrendPoint => ({ auditId: 1, branch: 'main', completedAt: null, notaPonderada: 0, notas });
const c = (id: string, avaliada: boolean): IIsoCharacteristic => ({ id, nome: id, avaliada, nota: avaliada ? 80 : null, achados: 0, peso: 0, meta: null, atingiu: null });

describe('isoQuality', () => {
  it('delta compara os dois últimos pontos', () => {
    expect(isoDelta([point({ security: 70 }), point({ security: 75.5 })], 'security')).toBe(5.5);
    expect(isoDelta([point({ security: 80 }), point({ security: 75 })], 'security')).toBe(-5);
  });

  it('sem dois pontos ou sem a característica não há delta', () => {
    expect(isoDelta(undefined, 'security')).toBeNull();
    expect(isoDelta([point({ security: 70 })], 'security')).toBeNull();
    expect(isoDelta([point({}), point({ security: 70 })], 'security')).toBeNull();
  });

  it('rótulo mostra seta, sinal e zero', () => {
    expect(isoDeltaLabel(3.2)).toBe('▲ +3,2');
    expect(isoDeltaLabel(-1.5)).toBe('▼ −1,5');
    expect(isoDeltaLabel(0)).toBe('= 0');
    expect(isoDeltaLabel(null)).toBe('—');
  });

  it('ordena as avaliadas pela prioridade e deixa as não avaliadas no fim', () => {
    const out = orderedCharacteristics([c('safety', false), c('security', true), c('reliability', true)], ['reliability', 'security']);
    expect(out.map((x) => x.id)).toEqual(['reliability', 'security', 'safety']);
  });
});

describe('texto para leitor de tela e situação', () => {
  it('lê a variação por extenso', () => {
    expect(isoDeltaSr(5)).toBe('subiu 5 pontos');
    expect(isoDeltaSr(-1.5)).toBe('caiu 1,5 pontos');
    expect(isoDeltaSr(-1)).toBe('caiu 1 ponto');
    expect(isoDeltaSr(0)).toBe('sem variação');
    expect(isoDeltaSr(null)).toBe('sem histórico para comparar');
  });
  it('diz a situação em palavras, não só em cor', () => {
    expect(isoSituacao('good')).toBe('Bom');
    expect(isoSituacao('warn')).toBe('Atenção');
    expect(isoSituacao('bad')).toBe('Ruim');
    expect(isoSituacao('none')).toBe('Sem nota');
  });
});
