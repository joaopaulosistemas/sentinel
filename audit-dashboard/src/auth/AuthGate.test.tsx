import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthGate, ehRetornoDoLogin, lerConfig } from './AuthGate';
import { useAuth } from './AuthContext';
import { rotuloDoPapel } from './UserMenu';
import { authHeader, setAccessToken } from './session';

const m = vi.hoisted(() => ({
  getUser: vi.fn(),
  signinRedirect: vi.fn(async () => undefined),
  signinRedirectCallback: vi.fn(),
}));

vi.mock('oidc-client-ts', () => ({
  WebStorageStateStore: class {},
  UserManager: class {
    events = { addUserLoaded: vi.fn(), addAccessTokenExpired: vi.fn() };
    getUser = m.getUser;
    signinRedirect = m.signinRedirect;
    signinRedirectCallback = m.signinRedirectCallback;
    signoutRedirect = vi.fn();
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
    expect(rotuloDoPapel(true, true)).toBe('Administrador');
    expect(rotuloDoPapel(false, true)).toBe('Executor');
    expect(rotuloDoPapel(false, false)).toBe('Sem papel no Sentinel');
  });
});
