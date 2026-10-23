import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { EngineConfig, SystemSummaryResponse } from '../types';
import { EngineAnalyzersPanel, EngineRulesPanel, isNoop, mergeTuning } from './EnginePanel';

const system = { id: 7, name: 'conta-corrente-api', stack: 'JAVA', team: '', lastAuditScore: 62, lastAuditDate: null, openCritical: 0, openHigh: 0 } as SystemSummaryResponse;

function config(over: Partial<EngineConfig> = {}): EngineConfig {
  return {
    profile: 'java-bancario',
    profileDescription: 'Regras para serviços Java',
    adjustments: 0,
    tuning: { rules: [], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [] },
    profileRules: [],
    packs: [
      { id: 'core', name: 'Core', description: null, inProfile: true, active: true },
      { id: 'extra', name: 'Extra', description: null, inProfile: false, active: false },
    ],
    analyzers: [
      { name: 'PMD', description: 'Lint de Java', disabled: false },
      { name: 'Trivy', description: 'Dependências', disabled: false },
    ],
    thresholds: [{ key: 'max-function-lines', profileValue: 80, override: null }],
    knownRules: [{ id: 'sentinel-god-class', count: 4 }],
    ...over,
  };
}

function setup(initial: EngineConfig, Panel = EngineRulesPanel) {
  const calls: Array<{ method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';
      calls.push({ method, body: init?.body as string | undefined });
      if (method === 'PUT') return new Response(JSON.stringify({ ...initial, tuning: JSON.parse(String(init?.body)) }), { status: 200 });
      return new Response(JSON.stringify(initial), { status: 200 });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <Panel system={system} />
    </QueryClientProvider>,
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

describe('EnginePanel', () => {
  it('sem ajustes diz que o sistema é igual ao perfil', async () => {
    setup(config());
    expect(await screen.findByText('Igual ao perfil')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Voltar ao perfil' })).not.toBeInTheDocument();
  });

  it('mostra quantos ajustes existem em relação ao perfil', async () => {
    setup(config({ adjustments: 3 }));
    expect(await screen.findByText('3 ajustes em relação ao perfil')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Voltar ao perfil' })).toBeInTheDocument();
  });

  it('desligar uma regra e salvar manda só esse ajuste', async () => {
    const calls = setup(config());
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Desligar sentinel-god-class' }));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar ajustes' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = JSON.parse(calls.find((c) => c.method === 'PUT')?.body ?? '{}');
    expect(body.rules).toEqual([{ id: 'sentinel-god-class', severity: null, disabled: true, shadow: null }]);
    expect(body.enablePacks).toEqual([]);
    expect(body.disablePacks).toEqual([]);
    expect(body.thresholds).toEqual({});
  });

  it('pacote ligado fora do perfil e limite novo entram como ajuste', async () => {
    const calls = setup(config());
    fireEvent.click(await screen.findByRole('checkbox', { name: /Extra/ }));
    fireEvent.change(screen.getByLabelText('Linhas por função'), { target: { value: '50' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar ajustes' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = JSON.parse(calls.find((c) => c.method === 'PUT')?.body ?? '{}');
    expect(body.enablePacks).toEqual(['extra']);
    expect(body.thresholds).toEqual({ 'max-function-lines': 50 });
  });

  it('limite inválido bloqueia o salvar', async () => {
    setup(config());
    fireEvent.change(await screen.findByLabelText('Linhas por função'), { target: { value: '0' } });
    expect(screen.getByText('Use um inteiro maior que zero.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salvar ajustes' })).toBeDisabled();
  });

  it('voltar ao perfil manda tudo vazio', async () => {
    const calls = setup(config({ adjustments: 1 }));
    fireEvent.click(await screen.findByRole('button', { name: 'Voltar ao perfil' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    expect(JSON.parse(calls.find((c) => c.method === 'PUT')?.body ?? '{}')).toEqual({
      rules: [], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [],
    });
  });

  it('desligar um analisador salva sem perder os ajustes de regra', async () => {
    const tuning = { rules: [{ id: 'x', severity: 'LOW' as const, disabled: false, shadow: null }], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [] };
    const calls = setup(config({ adjustments: 1, tuning }), EngineAnalyzersPanel);
    fireEvent.click(await screen.findByRole('checkbox', { name: /PMD/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar ajustes' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = JSON.parse(calls.find((c) => c.method === 'PUT')?.body ?? '{}');
    expect(body.disableAnalyzers).toEqual(['PMD']);
    expect(body.rules).toEqual(tuning.rules);
  });

  it('funções puras', () => {
    expect(isNoop({ id: 'a', severity: null, disabled: false, shadow: null })).toBe(true);
    expect(isNoop({ id: 'a', severity: null, disabled: false, shadow: true })).toBe(false);
    const server = { rules: [], thresholds: { a: 1 }, enablePacks: [], disablePacks: [], disableAnalyzers: ['PMD'] };
    expect(mergeTuning(server, { thresholds: { b: 2 } })).toEqual({ ...server, thresholds: { b: 2 } });
  });
});
