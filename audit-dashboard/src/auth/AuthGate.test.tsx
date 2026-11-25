import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthGate, ehRetornoDoLogin, lerConfig, lerConfigDe, lerPerfilDe } from './AuthGate';
import { useAuth } from './AuthContext';
import { rotuloDoPapel } from './UserMenu';
import { authHeader, consumirSaida, marcarSaida, setAccessToken } from './session';

const m = vi.hoisted(() => ({
  getUser: vi.fn(),
  signinRedirect: vi.fn(async () => undefined),
  signinRedirectCallback: vi.fn(),
  signoutRedirect: vi.fn(async () => undefined),
}));

vi.mock('oidc-client-ts', () => ({
  WebStorageStateStore: class {},
  UserManager: class {
    events = { addUserLoaded: vi.fn(), addAccessTokenExpired: vi.fn() };
    getUser = m.getUser;
    signinRedirect = m.signinRedirect;
    signinRedirectCallback = m.signinRedirectCallback;
    signoutRedirect = m.signoutRedirect;
  },
}));

function Quem() {
  const a = useAuth();
  return <p>{a.enabled ? `${a.username}|${a.admin ? 'admin' : 'comum'}|${a.canExecute ? 'executa' : 'le'}` : 'sem login'}</p>;
}

function stubFetch(config: unknown, me?: unknown) {
  const fetchMock = vi.fn(async (url: string, _init?: RequestInit) => {
    if (String(url).includes('/config.json')) return new Response(JSON.stringify(config), { status: 200 });
    if (String(url).includes('/api/v1/me')) return new Response(JSON.stringify(me), { status: 200 });
    return new Response('', { status: 404 });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

beforeEach(() => {
  m.getUser.mockReset();
  m.signinRedirect.mockClear();
  m.signinRedirectCallback.mockReset();
  m.signoutRedirect.mockClear();
  window.sessionStorage.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  setAccessToken(undefined);
});

describe('AuthGate', () => {
  it('sem configuração de login mostra o painel como sempre, sem ir ao Keycloak', async () => {
    stubFetch({ oidcAuthority: '', oidcClientId: '' });
    render(<AuthGate><Quem /></AuthGate>);
    expect(await screen.findByText('sem login')).toBeInTheDocument();
    expect(m.signinRedirect).not.toHaveBeenCalled();
  });

  it('com login e sem sessão manda o usuário para o Keycloak', async () => {
    stubFetch({ oidcAuthority: 'http://kc/realms/s', oidcClientId: 'painel' });
    m.getUser.mockResolvedValue(null);
    render(<AuthGate><Quem /></AuthGate>);
    await waitFor(() => expect(m.signinRedirect).toHaveBeenCalled());
  });

  it('com sessão manda o token à API e usa o perfil que ela devolve', async () => {
    const fetchMock = stubFetch(
      { oidcAuthority: 'http://kc/realms/s', oidcClientId: 'painel' },
      { username: 'executor', roles: ['sentinel-executor'], admin: false, canExecute: true },
    );
    m.getUser.mockResolvedValue({ expired: false, access_token: 'tok-123' });
    render(<AuthGate><Quem /></AuthGate>);
    expect(await screen.findByText('executor|comum|executa')).toBeInTheDocument();
    expect(authHeader()).toEqual({ Authorization: 'Bearer tok-123' });
    const meCall = fetchMock.mock.calls.find((c) => String(c[0]).includes('/api/v1/me'));
    expect(meCall?.[1]?.headers).toEqual({ Authorization: 'Bearer tok-123' });
  });

  it('se a API recusa o token mostra o erro em vez do painel', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      String(url).includes('/config.json')
        ? new Response(JSON.stringify({ oidcAuthority: 'http://kc/realms/s', oidcClientId: 'painel' }), { status: 200 })
        : new Response('', { status: 401 })));
    m.getUser.mockResolvedValue({ expired: false, access_token: 'ruim' });
    render(<AuthGate><Quem /></AuthGate>);
    expect(await screen.findByText('A API recusou o token.')).toBeInTheDocument();
  });
});

describe('sair do painel', () => {
  const CONFIG = { oidcAuthority: 'http://kc/realms/s', oidcClientId: 'painel' };
  const ME = { username: 'ana', roles: [], admin: false, canExecute: false };

  function BotaoSair() {
    const a = useAuth();
    return <button onClick={a.logout}>sair</button>;
  }

  it('Sair anota a saída, limpa o token e vai ao Keycloak encerrar a sessão', async () => {
    stubFetch(CONFIG, ME);
    m.getUser.mockResolvedValue({ expired: false, access_token: 'tok' });
    render(<AuthGate><BotaoSair /></AuthGate>);
    fireEvent.click(await screen.findByText('sair'));
    expect(m.signoutRedirect).toHaveBeenCalledTimes(1);
    expect(authHeader()).toEqual({});
    expect(window.sessionStorage.getItem('sentinel.saiu')).toBe('1');
  });

  it('na volta do logout mostra "Você saiu" sem mandar para o login sozinho', async () => {
    stubFetch(CONFIG);
    m.getUser.mockResolvedValue(null);
    marcarSaida();
    render(<AuthGate><Quem /></AuthGate>);
    expect(await screen.findByRole('heading', { level: 1, name: 'Você saiu' })).toBeInTheDocument();
    expect(screen.queryByText('Entrando…')).toBeNull();
    expect(m.signinRedirect).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem('sentinel.saiu')).toBeNull();
  });

  it('o botão da tela "Você saiu" leva ao login', async () => {
    stubFetch(CONFIG);
    marcarSaida();
    render(<AuthGate><Quem /></AuthGate>);
    fireEvent.click(await screen.findByRole('button', { name: /Entrar de novo/ }));
    expect(m.signinRedirect).toHaveBeenCalledTimes(1);
  });

  it('a anotação vale uma vez: recarregar depois vai direto ao login', () => {
    marcarSaida();
    expect(consumirSaida()).toBe(true);
    expect(consumirSaida()).toBe(false);
  });
});

describe('helpers do login', () => {
  it('reconhece a volta do Keycloak', () => {
    expect(ehRetornoDoLogin('?code=a&state=b')).toBe(true);
    expect(ehRetornoDoLogin('?code=a')).toBe(false);
    expect(ehRetornoDoLogin('')).toBe(false);
  });

  it('config que não é JSON (modo dev) vale como sem login', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html></html>', { status: 200 })));
    expect(await lerConfig()).toBeNull();
  });

  it('rótulo do papel', () => {
    expect(rotuloDoPapel({ admin: true, canExecute: true })).toBe('Administrador');
    expect(rotuloDoPapel({ admin: false, canExecute: true })).toBe('Executor');
    expect(rotuloDoPapel({ admin: false, canExecute: false })).toBe('Sem papel no Sentinel');
  });
});

describe('conferência dos dados recebidos', () => {
  it('lerConfigDe só aceita os dois campos preenchidos', () => {
    expect(lerConfigDe({ oidcAuthority: 'http://kc', oidcClientId: 'sentinel' })).toEqual({ oidcAuthority: 'http://kc', oidcClientId: 'sentinel' });
    expect(lerConfigDe({ oidcAuthority: 'http://kc' })).toBeNull();
    expect(lerConfigDe({ oidcAuthority: 1, oidcClientId: 'x' })).toBeNull();
    expect(lerConfigDe(null)).toBeNull();
    expect(lerConfigDe('texto')).toBeNull();
  });

  it('lerPerfilDe descarta o que não é do tipo esperado e rejeita perfil inválido', () => {
    expect(lerPerfilDe({ username: 'ana', roles: ['admin', 3], admin: true, canExecute: 'sim' })).toEqual({
      username: 'ana',
      roles: ['admin'],
      admin: true,
      canExecute: false,
    });
    expect(() => lerPerfilDe({ roles: [] })).toThrow('perfil inválido');
    expect(() => lerPerfilDe(undefined)).toThrow('perfil inválido');
  });
});

describe('telas de entrada', () => {
  it('enquanto confirma a sessão mostra "Entrando…" com o aviso de status', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)));
    render(<AuthGate><Quem /></AuthGate>);
    expect(screen.getByRole('status')).toHaveTextContent('Entrando…');
    expect(screen.getByRole('status')).toHaveTextContent('Confirmando a sua sessão');
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull();
  });

  it('na falha explica o que houve e o botão tenta entrar de novo', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      String(url).includes('/config.json')
        ? new Response(JSON.stringify({ oidcAuthority: 'http://kc/realms/s', oidcClientId: 'painel' }), { status: 200 })
        : new Response('', { status: 401 })));
    m.getUser.mockResolvedValue({ expired: false, access_token: 'ruim' });
    render(<AuthGate><Quem /></AuthGate>);
    expect(await screen.findByRole('heading', { name: 'Não foi possível entrar' })).toBeInTheDocument();
    expect(screen.getByText('A sessão pode ter expirado ou o servidor ficou indisponível.')).toBeInTheDocument();
    m.signinRedirect.mockClear();
    fireEvent.click(screen.getByRole('button', { name: 'Entrar de novo' }));
    expect(m.signinRedirect).toHaveBeenCalledTimes(1);
  });
});
