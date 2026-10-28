import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SettingView, SettingsResponse } from '../../types';
import { SettingsPage } from './SettingsPage';

function setting(over: Partial<SettingView>): SettingView {
  return {
    key: 'TRIVY_ENABLED', section: 'ferr', group: 'Trivy', label: 'Trivy ligado', help: 'Dependências e infraestrutura', kind: 'bool', def: 'true',
    options: [], unit: '', min: 0, max: 0, editable: true, secret: false, value: 'true', savedValue: null, source: 'ambiente', pendingRestart: false, ...over,
  };
}

const base: SettingsResponse = {
  restartRequired: false,
  settings: [
    setting({}),
    setting({ key: 'TRIVY_TIMEOUT_SECONDS', label: 'Tempo limite do Trivy', help: 'Segundos antes de encerrar', kind: 'number', value: '900', min: 10, max: 7200, unit: 's' }),
    setting({ key: 'API_KEY', section: 'seg', group: 'Acesso à API', label: 'Chave da API', help: 'Exigida em toda a API', kind: 'text', value: '********', editable: false, secret: true }),
  ],
};

function stubApi(inicial: SettingsResponse) {
  let atual = inicial;
  const chamadas: { method: string; url: string; body?: string }[] = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    chamadas.push({ method, url: String(url), body: init?.body as string | undefined });
    if (method === 'PUT') {
      const values = JSON.parse(String(init?.body)).values as Record<string, string>;
      atual = { restartRequired: true, settings: atual.settings.map((s) => (s.key in values ? { ...s, savedValue: values[s.key], pendingRestart: true } : s)) };
    }
    return new Response(JSON.stringify(atual), { status: 200 });
  }));
  return chamadas;
}

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><SettingsPage /></MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('SettingsPage', () => {
  it('mostra as configurações da primeira seção com variável, ajuda e origem', async () => {
    stubApi(base);
    renderPage();
    expect(await screen.findByText('TRIVY_ENABLED')).toBeInTheDocument();
    expect(screen.getByText('TRIVY_ENABLED')).toBeInTheDocument();
    expect(screen.getByText('Dependências e infraestrutura')).toBeInTheDocument();
    expect(screen.getAllByText('Origem: ambiente').length).toBeGreaterThan(0);
  });

  it('o que só se muda no ambiente aparece em leitura, sem campo para editar e com o segredo mascarado', async () => {
    stubApi(base);
    renderPage();
    await screen.findByText('TRIVY_ENABLED');
    fireEvent.click(within(screen.getByRole('navigation', { name: 'Seções de configuração' })).getByRole('button', { name: /Segurança e acesso/ }));
    expect(await screen.findByText('Chave da API')).toBeInTheDocument();
    expect(screen.getByText('Só no ambiente')).toBeInTheDocument();
    expect(screen.getByTestId('valor-API_KEY')).toHaveTextContent('********');
    expect(screen.queryByRole('textbox', { name: 'Chave da API' })).toBeNull();
  });

  it('a busca procura em todas as seções', async () => {
    stubApi(base);
    renderPage();
    await screen.findByText('TRIVY_ENABLED');
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'chave' } });
    expect(await screen.findByText('Chave da API')).toBeInTheDocument();
    expect(screen.queryByText('TRIVY_ENABLED')).toBeNull();
  });

  it('salva só o que mudou e avisa que vale após o reinício', async () => {
    const chamadas = stubApi(base);
    renderPage();
    const campo = await screen.findByLabelText('Tempo limite do Trivy');
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeDisabled();
    fireEvent.change(campo, { target: { value: '120' } });
    expect(screen.getByText('1 alteração(ões) não salva(s).')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await waitFor(() => expect(chamadas.some((c) => c.method === 'PUT')).toBe(true));
    expect(JSON.parse(chamadas.find((c) => c.method === 'PUT')!.body!)).toEqual({ values: { TRIVY_TIMEOUT_SECONDS: '120' } });
    expect(await screen.findByText('Há configurações salvas aguardando reinício')).toBeInTheDocument();
    expect(screen.getByText('Aguarda reinício', { selector: '*' })).toBeInTheDocument();
  });

  it('valor fora da faixa bloqueia o salvamento', async () => {
    stubApi(base);
    renderPage();
    const campo = await screen.findByLabelText('Tempo limite do Trivy');
    fireEvent.change(campo, { target: { value: '5' } });
    expect(await screen.findByText('Use um valor entre 10 e 7200')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeDisabled();
  });

  it('descartar limpa as alterações', async () => {
    stubApi(base);
    renderPage();
    const campo = await screen.findByLabelText('Tempo limite do Trivy');
    fireEvent.change(campo, { target: { value: '120' } });
    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(screen.getByText('Nenhuma alteração pendente.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tempo limite do Trivy')).toHaveValue(900);
  });

  it('mostra o erro da API ao salvar', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init?: RequestInit) => {
      if (init?.method === 'PUT') return new Response(JSON.stringify({ message: 'Tempo limite: use um valor entre 10 e 7200' }), { status: 400 });
      return new Response(JSON.stringify(base), { status: 200 });
    }));
    renderPage();
    fireEvent.change(await screen.findByLabelText('Tempo limite do Trivy'), { target: { value: '60' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    expect(await screen.findByText('Não foi possível salvar')).toBeInTheDocument();
    expect(screen.getByText('Tempo limite: use um valor entre 10 e 7200')).toBeInTheDocument();
  });
});
