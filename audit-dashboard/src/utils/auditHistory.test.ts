import { describe, expect, it } from 'vitest';
import type { IAuditRunResponse } from '../types';
import { buildHistory, historySummary } from './auditHistory';

const SCORE = { security: 0, quality: 0, architecture: 0, dependencies: 0, observability: 0 };

type RunInput = Partial<IAuditRunResponse> & Pick<IAuditRunResponse, 'id' | 'startedAt'>;

function run({ id, startedAt, ...extra }: RunInput): IAuditRunResponse {
  return {
    id,
    systemName: 'frequency',
    branch: 'main',
    status: 'COMPLETED',
    riskScore: { overall: 70, ...SCORE },
    findingsSummary: { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 },
    startedAt,
    completedAt: startedAt,
    ...extra,
  };
}

const at = (day: number, hour = 10) => `2026-10-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:00:00`;
const withScore = (overall: number): Partial<IAuditRunResponse> => ({ riskScore: { overall, ...SCORE } });

describe('buildHistory', () => {
  it('ordena da mais antiga para a mais recente, venha a lista como vier', () => {
    const points = buildHistory([run({ id: 3, startedAt: at(3) }), run({ id: 1, startedAt: at(1) }), run({ id: 2, startedAt: at(2) })]);
    expect(points.map((p) => p.id)).toEqual([1, 2, 3]);
  });

  it('fica só com as dez mais recentes', () => {
    const runs = Array.from({ length: 14 }, (_, i) => run({ id: i + 1, startedAt: at(i + 1) }));
    const points = buildHistory(runs);
    expect(points).toHaveLength(10);
    expect(points[0].id).toBe(5);
    expect(points[9].id).toBe(14);
  });

  it('arredonda a nota e a classifica pela régua única da nota', () => {
    const points = buildHistory([run({ id: 1, startedAt: at(1), ...withScore(74.6) }), run({ id: 2, startedAt: at(2), ...withScore(49.4) }), run({ id: 3, startedAt: at(3), ...withScore(75) })]);
    expect(points.map((p) => [p.score, p.band])).toEqual([[75, 'good'], [49, 'bad'], [75, 'good']]);
  });

  it('auditoria que falhou, foi cancelada ou está rodando não tem nota', () => {
    const points = buildHistory([
      run({ id: 1, startedAt: at(1), status: 'FAILED', riskScore: null }),
      run({ id: 2, startedAt: at(2), status: 'CANCELLED', riskScore: null }),
      run({ id: 3, startedAt: at(3), status: 'RUNNING', riskScore: null }),
      run({ id: 4, startedAt: at(4), status: 'PENDING', riskScore: null }),
    ]);
    expect(points.map((p) => [p.kind, p.score])).toEqual([['failed', null], ['cancelled', null], ['active', null], ['active', null]]);
  });

  it('a variação compara com a anterior que gerou nota, pulando as que falharam', () => {
    const points = buildHistory([
      run({ id: 1, startedAt: at(1), ...withScore(60) }),
      run({ id: 2, startedAt: at(2), status: 'FAILED', riskScore: null }),
      run({ id: 3, startedAt: at(3), ...withScore(72) }),
    ]);
    expect(points.map((p) => p.delta)).toEqual([null, null, 12]);
  });

  it('a variação do servidor tem prioridade sobre a calculada', () => {
    const points = buildHistory([run({ id: 1, startedAt: at(1), ...withScore(60) }), run({ id: 2, startedAt: at(2), ...withScore(72), scoreDelta: 11.6 })]);
    expect(points[1].delta).toBe(12);
  });

  it('traz críticos e altos e o rótulo de dia e hora', () => {
    const [point] = buildHistory([run({ id: 1, startedAt: '2026-10-30T13:20:00', findingsSummary: { CRITICAL: 2, HIGH: 5, MEDIUM: 0, LOW: 0, INFO: 0 } })]);
    expect([point.critical, point.high, point.dayLabel, point.timeLabel]).toEqual([2, 5, '30/10', '13:20']);
  });

  it('data inválida não quebra: o rótulo vira traço', () => {
    const [point] = buildHistory([run({ id: 1, startedAt: 'sem-data', completedAt: null })]);
    expect([point.dayLabel, point.timeLabel]).toEqual(['-', '']);
  });

  it('lista vazia dá gráfico vazio', () => {
    expect(buildHistory([])).toEqual([]);
  });
});

describe('historySummary', () => {
  it('conta quantas com nota estão na meta', () => {
    const points = buildHistory([
      run({ id: 1, startedAt: at(1), ...withScore(80) }),
      run({ id: 2, startedAt: at(2), ...withScore(74) }),
      run({ id: 3, startedAt: at(3), status: 'FAILED', riskScore: null }),
      run({ id: 4, startedAt: at(4), ...withScore(75) }),
    ]);
    expect(historySummary(points)).toEqual({ onTarget: 2, scored: 3 });
  });
});
