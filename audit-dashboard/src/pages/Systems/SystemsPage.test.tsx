import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SystemsPage } from './SystemsPage';

function renderPage(check: unknown, systems: unknown[] = []) {
  const calls: Array<{ url: string; method: string; body?: string }> = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const u = String(url);
      calls.push({ url: u, method: init?.method ?? 'GET', body: init?.body as string | undefined });
      if (u.endsWith('/check-repo')) return new Response(JSON.stringify(check), { status: 200 });
      if (u.includes('/mcp-access/') && u.includes('/calls')) return new Response('[]', { status: 200 });
      if (u.includes('/mcp-access/')) return new Response(JSON.stringify({ enabled: false, hasKey: false, keyPrefix: null, keyCreatedAt: null, dataLevel: 'TITLES', tools: [] }), { status: 200 });
      if (u.includes('/packs')) return new Response(JSON.stringify({ packs: [], profiles: [] }), { status: 200 });
      if (u.endsWith('/schedule')) return new Response(JSON.stringify({ id: 7, name: 'api', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 }), { status: 200 });
      if ((init?.method ?? 'GET') === 'POST') return new Response(JSON.stringify({ id: 7, name: 'api', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 }), { status: 201 });
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

  it('Editar abre o cadastro em pop-up, com a linha destacada, e fechar some com ele', async () => {
    renderPage({}, [
      { id: 3, name: 'conta-corrente-api', stack: 'JAVA', team: 'Squad Contas', lastAuditScore: 61, lastAuditDate: null, openCritical: 0, openHigh: 0, repoUrl: 'https://github.com/sicoob/conta-corrente', auditIntervalHours: 24, scheduledBranch: 'main', auditOnPush: true },
    ]);
    expect(await screen.findByText('sicoob/conta-corrente')).toBeInTheDocument();
    expect(screen.getByText('a cada 24 h · main · push')).toBeInTheDocument();
    expect(screen.queryByLabelText('Editar sistema')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }));
    const panel = await screen.findByRole('dialog', { name: 'Editar sistema' });
    expect(panel).toHaveTextContent('Editando');
    expect(screen.getByLabelText(/Nome do sistema/)).toHaveValue('conta-corrente-api');
    expect(screen.getByRole('button', { name: 'Salvar e auditar agora' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }));
    expect(screen.queryByLabelText('Editar sistema')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }));
    await screen.findByRole('dialog', { name: 'Editar sistema' });
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Editar sistema' })).not.toBeInTheDocument();
  });

  it('guarda token, hosts, histórico, webhook e notificação no cadastro do sistema', async () => {
    const calls = renderPage({});
    await openNew();
    fireEvent.change(screen.getByLabelText(/Token Git/), { target: { value: 'ghp_abc' } });
    fireEvent.change(screen.getByLabelText(/Hosts que recebem o token/), { target: { value: 'github.com, git.empresa.com' } });
    fireEvent.change(screen.getByLabelText('Commits do histórico'), { target: { value: '30' } });
    fireEvent.click(screen.getByLabelText(/Auditar a cada push/));
    fireEvent.click(screen.getByRole('button', { name: 'Gerar segredo' }));
    fireEvent.change(screen.getByLabelText('URL do webhook'), { target: { value: 'https://hooks.slack.com/services/T/B/X' } });
    fireEvent.change(screen.getByLabelText('Formato'), { target: { value: 'teams' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/systems'))).toBe(true));
    const body = JSON.parse(calls.find((c) => c.method === 'POST' && c.url.endsWith('/systems'))?.body ?? '{}');
    expect(body).toMatchObject({
      gitToken: 'ghp_abc',
      gitTokenHosts: 'github.com, git.empresa.com',
      gitHistoryDepth: 30,
      notifyUrl: 'https://hooks.slack.com/services/T/B/X',
      notifyFormat: 'teams',
      notifyMinSeverity: 'HIGH',
    });
    expect(body.webhookSecret).toMatch(/^[0-9a-f]{64}$/);
  });

  it('ao editar, token e segredos salvos não aparecem e podem ser mantidos ou removidos', async () => {
    const calls = renderPage({}, [
      { id: 4, name: 'conta', stack: 'JAVA', team: 'T', lastAuditScore: 80, lastAuditDate: null, openCritical: 0, openHigh: 0,
        repoUrl: 'https://github.com/org/conta', gitTokenSet: true, gitTokenHosts: 'github.com', webhookSecretSet: true, auditOnPush: true, notifyUrlSet: true },
    ]);
    fireEvent.click(await screen.findByRole('button', { name: /Editar/ }));
    expect(await screen.findByLabelText(/Token Git/)).toHaveValue('');
    expect(screen.getByPlaceholderText('Token salvo. Deixe vazio para manter')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('Remover o token salvo'));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.url.endsWith('/systems/4'))).toBe(true));
    const body = JSON.parse(calls.find((c) => c.method === 'PUT' && c.url.endsWith('/systems/4'))?.body ?? '{}');
    expect(body.clearGitToken).toBe(true);
    expect(body.gitToken).toBeUndefined();
    expect(body.webhookSecret).toBeUndefined();
    expect(body.notifyUrl).toBeUndefined();
  });

  it('a aba Claude (MCP) fica bloqueada num sistema novo e abre no sistema já salvo', async () => {
    renderPage({}, [
      { id: 4, name: 'conta-corrente-api', stack: 'JAVA', team: '', lastAuditScore: 62, lastAuditDate: null, openCritical: 0, openHigh: 0 },
    ]);
    fireEvent.click(await screen.findByRole('button', { name: 'Novo sistema' }));
    expect(await screen.findByRole('tab', { name: 'Claude (MCP)' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }));

    fireEvent.click(await screen.findByRole('button', { name: /Editar/ }));
    const tab = await screen.findByRole('tab', { name: 'Claude (MCP)' });
    expect(tab).toBeEnabled();
    expect(screen.getByRole('tab', { name: 'Cadastro e Git' })).toHaveAttribute('aria-selected', 'true');
    fireEvent.click(tab);
    expect(await screen.findByText(/O Claude consulta o resultado deste sistema por MCP/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Token Git/)).not.toBeInTheDocument();
  });
});
