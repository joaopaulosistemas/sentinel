import { describe, expect, it } from 'vitest';
import type { IAnalyzerProgress, IAuditRunResponse } from '../types';
import {
  analyzerCounts,
  analyzerStatusLabel,
  averageDurationSeconds,
  elapsedSeconds,
  formatDuration,
  isActiveRun,
  phaseIndex,
  progressPercent,
  remainingSeconds,
} from './auditProgress';

const an = (name: string, status: IAnalyzerProgress['status']): IAnalyzerProgress => ({
  name,
  status,
  findings: 0,
  durationMs: 0,
  note: null,
});

const running = (phase: NonNullable<IAuditRunResponse['progress']>['phase'], analyzers: IAnalyzerProgress[] = []) =>
  ({ status: 'RUNNING', progress: { phase, analyzers, events: [] } }) as Pick<IAuditRunResponse, 'status' | 'progress'>;

describe('andamento da auditoria', () => {
  it('concluída tem todas as etapas prontas e 100%', () => {
    const run = { status: 'COMPLETED', progress: null } as Pick<IAuditRunResponse, 'status' | 'progress'>;
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

  it('analisador parcial conta como concluído (terminou), mas com rótulo próprio', () => {
    expect(analyzerCounts([an('A', 'PARCIAL'), an('B', 'EXECUTANDO')])).toEqual({ done: 1, total: 2 });
    expect(analyzerStatusLabel.PARCIAL).toBe('Parcial');
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

describe('estimativa de tempo', () => {
  const done = (start: string, end: string | null, status: IAuditRunResponse['status'] = 'COMPLETED') => ({
    status,
    startedAt: start,
    completedAt: end,
  });

  it('tira a média das concluídas e ignora falhas, canceladas e em andamento', () => {
    expect(
      averageDurationSeconds([
        done('2026-10-22T10:00:00', '2026-10-22T10:04:00'), // 240 s
        done('2026-10-21T10:00:00', '2026-10-21T10:02:00'), // 120 s
        done('2026-10-20T10:00:00', '2026-10-20T10:00:05', 'FAILED'),
        done('2026-10-19T10:00:00', null, 'RUNNING'),
        done('2026-10-18T10:00:00', '2026-10-18T10:00:30', 'CANCELLED'),
      ]),
    ).toBe(180);
    expect(averageDurationSeconds([])).toBeNull();
  });

  it('usa só as 5 mais recentes', () => {
    const recent = Array.from({ length: 5 }, () => done('2026-10-22T10:00:00', '2026-10-22T10:01:00'));
    const old = done('2026-01-01T10:00:00', '2026-01-01T12:00:00');
    expect(averageDurationSeconds([...recent, old])).toBe(60);
  });

  it('calcula o que falta e some quando passa da média ou não há base', () => {
    expect(remainingSeconds(60, 180)).toBe(120);
    expect(remainingSeconds(200, 180)).toBeNull();
    expect(remainingSeconds(null, 180)).toBeNull();
    expect(remainingSeconds(60, null)).toBeNull();
  });

  it('reconhece auditoria em andamento', () => {
    expect(isActiveRun({ status: 'PENDING' })).toBe(true);
    expect(isActiveRun({ status: 'RUNNING' })).toBe(true);
    expect(isActiveRun({ status: 'CANCELLED' })).toBe(false);
  });
});

import { clock, copyText, etaText, isLoadingSource, isSlowLoad, phaseViews, shortRepo } from './auditProgress';

describe('apoio à tela de execução', () => {
  it('clock formata mm:ss', () => {
    expect(clock(56)).toBe('00:56');
    expect(clock(125)).toBe('02:05');
  });

  it('shortRepo tira protocolo, credencial e .git', () => {
    expect(shortRepo('https://github.com/org/repo.git')).toBe('github.com/org/repo');
    expect(shortRepo('git@github.com:org/repo.git')).toBe('github.com:org/repo');
    expect(shortRepo(null)).toBeNull();
  });

  it('etaText usa minutos a partir de 60 s', () => {
    expect(etaText(null)).toContain('estimando');
    expect(etaText(42)).toBe('faltam cerca de 40 s');
    expect(etaText(100)).toBe('faltam cerca de 2 min');
  });

  it('phaseViews marca concluídas, ativa e pendentes', () => {
    const v = phaseViews(
      { status: 'RUNNING', progress: { phase: 'ANALISE', analyzers: [], events: [], repoUrl: 'https://github.com/a/b' } },
      { done: 3, total: 12 },
    );
    expect(v.map((p) => p.state)).toEqual(['done', 'done', 'active', 'pending', 'pending']);
    expect(v[1].sub).toBe('concluído');
    expect(v[2].sub).toBe('3 de 12 analisadores');
  });

  it('na falha a fase corrente fica interrompida', () => {
    const v = phaseViews({ status: 'FAILED', progress: { phase: 'CLONE', analyzers: [], events: [] } }, { done: 0, total: 0 });
    expect(v[1].state).toBe('failed');
  });
});

describe('carregamento do código (fila e clone)', () => {
  it('só é "carregando" enquanto a auditoria está ativa e na fila ou no clone', () => {
    expect(isLoadingSource({ status: 'PENDING', progress: null })).toBe(true);
    expect(isLoadingSource({ status: 'RUNNING', progress: null })).toBe(true);
    expect(isLoadingSource({ status: 'RUNNING', progress: { phase: 'CLONE', analyzers: [], events: [] } })).toBe(true);
    expect(isLoadingSource({ status: 'RUNNING', progress: { phase: 'ANALISE', analyzers: [], events: [] } })).toBe(false);
    expect(isLoadingSource({ status: 'FAILED', progress: { phase: 'CLONE', analyzers: [], events: [] } })).toBe(false);
    expect(isLoadingSource({ status: 'COMPLETED', progress: null })).toBe(false);
  });

  it('copyText mostra o contador só quando o total é conhecido', () => {
    expect(copyText(null)).toBeNull();
    expect(copyText({ phase: 'CLONE', analyzers: [], events: [] })).toBeNull();
    expect(copyText({ phase: 'CLONE', analyzers: [], events: [], copyDone: 0, copyTotal: 0 })).toBeNull();
    expect(copyText({ phase: 'CLONE', analyzers: [], events: [], copyDone: 1350, copyTotal: 48000 })).toBe('1.350 de 48.000 arquivos');
  });

  it('avisa que está demorando depois de 30 s', () => {
    expect(isSlowLoad(29)).toBe(false);
    expect(isSlowLoad(30)).toBe(true);
    expect(isSlowLoad(null)).toBe(false);
  });

  it('na fase de clone a legenda traz o andamento da cópia', () => {
    const v = phaseViews(
      { status: 'RUNNING', progress: { phase: 'CLONE', analyzers: [], events: [], copyDone: 100, copyTotal: 830 } },
      { done: 0, total: 0 },
    );
    expect(v[1].sub).toBe('100 de 830 arquivos');
  });
});
