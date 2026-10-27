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

function setup(initial: EngineConfig, Panel = EngineRulesPanel, audits: unknown = [], onDirty?: (d: boolean) => void, plan: unknown = { scoreNow: 60, scoreIfCriticalFixed: 60, criticalCount: 0, items: [] }) {
  const calls: Array<{ method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';
      calls.push({ method, body: init?.body as string | undefined });
      if (method === 'PUT') return new Response(JSON.stringify({ ...initial, tuning: JSON.parse(String(init?.body)) }), { status: 200 });
      // O painel também consulta as auditorias do sistema (para estimar o efeito de cada regra na nota).
      if (String(_url).includes('fix-plan')) return new Response(JSON.stringify(plan), { status: 200 });
      if (String(_url).includes('/audits')) return new Response(JSON.stringify(audits), { status: 200 });
      return new Response(JSON.stringify(initial), { status: 200 });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <Panel system={system} onDirtyChange={onDirty} />
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

  const catalogConfig = () =>
    config({
      knownRules: [{ id: 'semgrep.rules.sentinel-god-class', count: 4 }],
      catalog: [
        { id: 'sentinel-god-class', pack: 'core-quality', message: 'Classe grande demais.', severity: 'WARNING', category: 'quality' },
        { id: 'sentinel-crypto-hash-fraco', pack: 'core-security', message: 'Hash fraco (MD5/SHA-1).', severity: 'ERROR', category: 'security' },
        { id: 'sentinel-logs-pii', pack: 'core-security', message: 'Dado pessoal em log.', severity: 'WARNING', category: 'security' },
      ],
    });

  it('mostra o que cada regra faz, inclusive as que não apareceram na última auditoria', async () => {
    setup(catalogConfig());
    expect(await screen.findByText('Hash fraco (MD5/SHA-1).')).toBeInTheDocument();
    expect(screen.getByText('Classe grande demais.')).toBeInTheDocument();
    expect(screen.getAllByText('não apareceu').length).toBe(2);
  });

  it('a busca filtra por texto da regra e o filtro de pacote separa os pacotes', async () => {
    setup(catalogConfig());
    await screen.findByText('Hash fraco (MD5/SHA-1).');
    fireEvent.change(screen.getByLabelText('Buscar regra'), { target: { value: 'pessoal' } });
    expect(screen.getByText('Dado pessoal em log.')).toBeInTheDocument();
    expect(screen.queryByText('Hash fraco (MD5/SHA-1).')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Buscar regra'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Filtrar por pacote'), { target: { value: 'core-quality' } });
    expect(screen.getByText('Classe grande demais.')).toBeInTheDocument();
    expect(screen.queryByText('Dado pessoal em log.')).not.toBeInTheDocument();
  });

  it('ação em lote coloca várias regras em "só observar" de uma vez', async () => {
    const calls = setup(catalogConfig());
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Selecionar sentinel-logs-pii' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Selecionar sentinel-crypto-hash-fraco' }));
    expect(screen.getByText('2 selecionadas')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Só observar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar ajustes' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = JSON.parse(calls.find((c) => c.method === 'PUT')?.body ?? '{}');
    expect(body.rules.map((r: { id: string }) => r.id).sort()).toEqual(['sentinel-crypto-hash-fraco', 'sentinel-logs-pii']);
    expect(body.rules.every((r: { shadow: boolean | null }) => r.shadow === true)).toBe(true);
  });

  it('mostra o ganho estimado na nota vindo do plano da última auditoria', async () => {
    const audits = [{ id: 11, status: 'COMPLETED', startedAt: '2026-10-20T10:00:00' }];
    const plan = {
      scoreNow: 60,
      scoreIfCriticalFixed: 62,
      criticalCount: 0,
      items: [{ ruleId: 'semgrep.rules.sentinel-god-class', title: 'Classe grande', severity: 'HIGH', category: 'QUALITY', occurrences: 4, scoreGain: 3.5, exampleFile: null, exampleLine: null, howToFix: null }],
    };
    setup(catalogConfig(), EngineRulesPanel, audits, undefined, plan);
    expect(await screen.findByText('+3.5')).toBeInTheDocument();
  });

  it('avisa quando há alteração sem salvar e deixa de avisar depois de salvar', async () => {
    const onDirty = vi.fn();
    setup(config(), EngineRulesPanel, [], onDirty);
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Desligar sentinel-god-class' }));
    await waitFor(() => expect(onDirty).toHaveBeenLastCalledWith(true));
    expect(screen.getByText('Alterações não salvas')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Salvar ajustes' }));
    await waitFor(() => expect(onDirty).toHaveBeenLastCalledWith(false));
  });
});
