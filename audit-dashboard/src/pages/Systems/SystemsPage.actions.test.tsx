import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SystemsPage } from './SystemsPage';

const sys = (id: number, name: string, auditCount: number) => ({
  id, name, stack: 'JAVA', team: 'Core', lastAuditScore: auditCount ? 80 : null, lastAuditDate: auditCount ? '2026-10-20T10:00:00' : null,
  openCritical: 0, openHigh: 0, auditCount, repoUrl: `https://github.com/org/${name}`,
});

function renderPage(systems = [sys(1, 'com-auditoria', 3), sys(2, 'sem-auditoria', 0)]) {
  const calls: Array<{ url: string; method: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      const method = init?.method ?? 'GET';
      calls.push({ url: u, method });
      if (method === 'DELETE') return new Response(null, { status: 204 });
      if (u.includes('/packs')) return new Response(JSON.stringify({ packs: [], profiles: [] }), { status: 200 });
      return new Response(JSON.stringify(systems), { status: 200 });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/systems']}>
        <Routes>
          <Route path="/systems" element={<SystemsPage />} />
          <Route path="/systems/:systemId" element={<p>tela do sistema</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return calls;
}

afterEach(() => vi.unstubAllGlobals());

/** O pop-up de confirmação do Carbon: o texto da pergunta e os botões ficam no mesmo bloco. */
async function askDialog() {
  const texto = await screen.findByText(/Tem certeza/);
  return texto.closest('.cds--modal-container') as HTMLElement;
}

describe('SystemsPage: ações da tabela', () => {
  it('o botão Auditorias fica desabilitado quando o sistema não tem auditorias', async () => {
    renderPage();
    expect(await screen.findByRole('button', { name: 'Auditorias de sem-auditoria' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Auditorias de com-auditoria' })).toBeEnabled();
  });

  it('o botão Auditorias abre a mesma tela de quando se clica no nome do sistema', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Auditorias de com-auditoria' }));
    expect(await screen.findByText('tela do sistema')).toBeInTheDocument();
  });

  it('Excluir pergunta antes e só apaga depois da confirmação', async () => {
    const calls = renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Excluir com-auditoria' }));
    const dialog = await askDialog();
    expect(within(dialog).getByText(/Tem certeza/)).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false);

    fireEvent.click(within(dialog).getByRole('button', { name: 'Excluir sistema' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE' && c.url.endsWith('/api/v1/systems/1'))).toBe(true));
  });

  it('cancelar a exclusão não apaga nada', async () => {
    const calls = renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Excluir com-auditoria' }));
    const dialog = await askDialog();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false);
  });

  it('o pop-up de editar ocupa 70% da largura da tela', async () => {
    renderPage();
    fireEvent.click((await screen.findAllByRole('button', { name: 'Editar' }))[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Editar sistema' });
    expect(dialog.style.width).toBe('70vw');
  });
});
