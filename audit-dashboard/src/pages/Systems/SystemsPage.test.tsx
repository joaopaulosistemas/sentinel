import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SystemsPage } from './SystemsPage';

function renderPage(check: unknown) {
  const calls: Array<{ url: string; method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      calls.push({ url: u, method: init?.method ?? 'GET', body: init?.body as string | undefined });
      if (u.endsWith('/check-repo')) return new Response(JSON.stringify(check), { status: 200 });
      if (u.includes('/packs')) return new Response(JSON.stringify({ packs: [], profiles: [] }), { status: 200 });
      if (u.endsWith('/schedule')) return new Response(JSON.stringify({ id: 7, name: 'api', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 }), { status: 200 });
      if ((init?.method ?? 'GET') === 'POST') return new Response(JSON.stringify({ id: 7, name: 'api', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 }), { status: 201 });
      return new Response(JSON.stringify([]), { status: 200 });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <SystemsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

async function openNew() {
  fireEvent.click(await screen.findByRole('button', { name: 'Novo sistema' }));
  fireEvent.change(await screen.findByLabelText(/Nome do sistema/), { target: { value: 'api' } });
  fireEvent.change(screen.getByLabelText(/Repositório Git/), { target: { value: 'https://github.com/org/api' } });
}

describe('SystemsPage', () => {
  it('verifica o repositório e mostra a branch padrão', async () => {
    const calls = renderPage({ reachable: true, defaultBranch: 'develop', branchFound: null, message: null });
    await openNew();
    fireEvent.click(screen.getByRole('button', { name: 'Verificar repositório' }));
    expect(await screen.findByText(/Alcançável/)).toHaveTextContent('branch padrão: develop');
    const check = calls.find((c) => c.url.endsWith('/check-repo'));
    expect(JSON.parse(check?.body ?? '{}')).toMatchObject({ repoUrl: 'https://github.com/org/api' });
  });

  it('mostra o motivo quando o repositório não responde', async () => {
    renderPage({ reachable: false, defaultBranch: null, branchFound: null, message: 'Repositório não encontrado' });
    await openNew();
    fireEvent.click(screen.getByRole('button', { name: 'Verificar repositório' }));
    expect(await screen.findByText(/Repositório não encontrado/)).toBeInTheDocument();
  });

  it('o botão de verificar fica desabilitado com endereço inválido', async () => {
    renderPage({});
    fireEvent.click(await screen.findByRole('button', { name: 'Novo sistema' }));
    expect(await screen.findByRole('button', { name: 'Verificar repositório' })).toBeDisabled();
  });

  it('salva o agendamento junto com o cadastro', async () => {
    const calls = renderPage({});
    await openNew();
    fireEvent.change(screen.getByLabelText(/Branch auditada/), { target: { value: 'develop' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/systems/7/schedule') && c.method === 'PUT')).toBe(true));
    const schedule = calls.find((c) => c.url.endsWith('/schedule'));
    expect(JSON.parse(schedule?.body ?? '{}')).toEqual({ auditIntervalHours: 0, branch: 'develop', auditOnPush: false });
  });
});
