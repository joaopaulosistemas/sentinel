import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SystemsPage } from './SystemsPage';

const created = { id: 7, name: 'legado', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 };

function renderPage(enabled: boolean, pathCheck: unknown = {}, systems: unknown[] = []) {
  const calls: Array<{ url: string; method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      const method = init?.method ?? 'GET';
      calls.push({ url: u, method, body: init?.body as string | undefined });
      if (u.endsWith('/local-source')) return new Response(JSON.stringify({ enabled }), { status: 200 });
      if (u.endsWith('/check-path')) return new Response(JSON.stringify(pathCheck), { status: 200 });
      if (u.includes('/mcp-access/') && u.includes('/calls')) return new Response('[]', { status: 200 });
      if (u.includes('/mcp-access/')) return new Response(JSON.stringify({ enabled: false, hasKey: false, keyPrefix: null, keyCreatedAt: null, dataLevel: 'TITLES', tools: [] }), { status: 200 });
      if (u.includes('/packs')) return new Response(JSON.stringify({ packs: [], profiles: [] }), { status: 200 });
      if (method === 'POST' || method === 'PUT') return new Response(JSON.stringify(created), { status: 201 });
      return new Response(JSON.stringify(systems), { status: 200 });
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

describe('SystemsPage com pasta local', () => {
  it('não oferece a pasta local quando o servidor não a habilitou', async () => {
    renderPage(false);
    fireEvent.click(await screen.findByRole('button', { name: 'Novo sistema' }));
    await screen.findByLabelText(/Nome do sistema/);
    expect(screen.queryByRole('tab', { name: 'Pasta local' })).toBeNull();
    expect(screen.queryByText('Pasta local')).toBeNull();
  });

  it('cadastra um sistema por pasta local, sem repoUrl', async () => {
    const calls = renderPage(true, { ok: true, message: null, defaultBranch: 'main', branchFound: null, fileCount: 812, uncommittedFiles: 3 });
    fireEvent.click(await screen.findByRole('button', { name: 'Novo sistema' }));
    fireEvent.change(await screen.findByLabelText(/Nome do sistema/), { target: { value: 'legado' } });
    fireEvent.click(await screen.findByText('Pasta local'));
    fireEvent.change(screen.getByLabelText(/Pasta \(dentro da raiz/), { target: { value: 'legado/app' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar pasta' }));
    expect(await screen.findByText(/Pasta encontrada/)).toHaveTextContent('3 com alterações não commitadas');
    expect(screen.queryByLabelText(/Token Git/)).toBeNull();
    expect(screen.queryByLabelText(/Auditar a cada push/)).toBeNull();
    fireEvent.click(screen.getByLabelText(/Pasta como está agora/));
    fireEvent.click(screen.getByRole('button', { name: /^Salvar$/ }));
    await vi.waitFor(() => expect(calls.some((c) => c.url.endsWith('/systems') && c.method === 'POST')).toBe(true));
    const body = JSON.parse(calls.find((c) => c.url.endsWith('/systems') && c.method === 'POST')?.body ?? '{}');
    expect(body).toMatchObject({ sourceType: 'LOCAL_PATH', localPath: 'legado/app', localSnapshot: 'TREE' });
    expect(body.repoUrl).toBeUndefined();
    expect(JSON.parse(calls.find((c) => c.url.endsWith('/check-path'))?.body ?? '{}')).toMatchObject({ path: 'legado/app' });
  });

  it('mostra o motivo quando a pasta não serve', async () => {
    renderPage(true, { ok: false, message: 'A pasta não é um repositório Git.' });
    fireEvent.click(await screen.findByRole('button', { name: 'Novo sistema' }));
    fireEvent.click(await screen.findByText('Pasta local'));
    fireEvent.change(screen.getByLabelText(/Pasta \(dentro da raiz/), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar pasta' }));
    expect(await screen.findByText(/não é um repositório Git/)).toBeInTheDocument();
  });

  it('lista a pasta no lugar do repositório', async () => {
    renderPage(true, {}, [{ ...created, sourceType: 'LOCAL_PATH', localPath: 'legado/app', repoUrl: null }]);
    expect(await screen.findByText('pasta: legado/app')).toBeInTheDocument();
  });
});
