import { describe, expect, it } from 'vitest';
import type { AnalyzerProgress, AuditRunResponse } from '../types';
import { analyzerCounts, elapsedSeconds, formatDuration, phaseIndex, progressPercent } from './auditProgress';

const an = (name: string, status: AnalyzerProgress['status']): AnalyzerProgress => ({
  name,
  status,
  findings: 0,
  durationMs: 0,
  note: null,
});

const running = (phase: NonNullable<AuditRunResponse['progress']>['phase'], analyzers: AnalyzerProgress[] = []) =>
  ({ status: 'RUNNING', progress: { phase, analyzers, events: [] } }) as Pick<AuditRunResponse, 'status' | 'progress'>;

describe('andamento da auditoria', () => {
  it('concluída tem todas as etapas prontas e 100%', () => {
    const run = { status: 'COMPLETED', progress: null } as Pick<AuditRunResponse, 'status' | 'progress'>;
    expect(phaseIndex(run)).toBe(5);
    expect(progressPercent(run)).toBe(100);
  });

  it('pendente sem andamento fica na fila; em execução sem andamento fica no clone', () => {
    expect(phaseIndex({ status: 'PENDING', progress: null })).toBe(0);
    expect(phaseIndex({ status: 'RUNNING', progress: null })).toBe(1);
  });

  it('conta só os analisadores que realmente rodam', () => {
    const list = [an('A', 'OK'), an('B', 'EXECUTANDO'), an('C', 'FALHOU'), an('D', 'INDISPONIVEL'), an('E', 'NAO_SUPORTADO')];
    expect(analyzerCounts(list)).toEqual({ done: 2, total: 3 });
  });

  it('a análise anda com os analisadores e nunca passa de 80', () => {
    expect(progressPercent(running('ANALISE', [an('A', 'ESPERANDO'), an('B', 'ESPERANDO')]))).toBe(20);
    expect(progressPercent(running('ANALISE', [an('A', 'OK'), an('B', 'EXECUTANDO')]))).toBe(50);
    expect(progressPercent(running('ANALISE', [an('A', 'OK'), an('B', 'OK')]))).toBe(80);
    expect(progressPercent(running('RELATORIO'))).toBeLessThan(100);
  });

  it('duração usa só horários do servidor', () => {
    expect(
      elapsedSeconds({ startedAt: '2026-10-22T10:00:00', completedAt: '2026-10-22T10:04:12', progress: null }),
    ).toBe(252);
    expect(
      elapsedSeconds({
        startedAt: '2026-10-22T10:00:00',
        completedAt: null,
        progress: { phase: 'ANALISE', analyzers: [], events: [{ at: '2026-10-22T10:00:09', message: 'x' }] },
      }),
    ).toBe(9);
    expect(formatDuration(252)).toBe('4 min 12 s');
    expect(formatDuration(9)).toBe('9 s');
  });
});
