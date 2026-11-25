import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthContext, SEM_LOGIN, type IAuth } from './AuthContext';
import { AuthErrorScreen, AuthLoadingScreen, AuthSignedOutScreen } from './AuthScreens';
import { authHeader, consumirSaida, marcarSaida, setAccessToken } from './session';

interface IConfig {
  oidcAuthority?: string;
  oidcClientId?: string;
}

interface IMe {
  username: string;
  roles: string[];
  admin: boolean;
  canExecute: boolean;
}

type Estado = { fase: 'carregando' } | { fase: 'livre' } | { fase: 'saiu' } | { fase: 'erro'; mensagem: string } | { fase: 'ok'; me: IMe };

const TIMEOUT_MS = 10_000;
const HTTP_NAO_AUTORIZADO = 401;

const ehTexto = (v: unknown): v is string => typeof v === 'string' && v.length > 0;

/** Confere o JSON da configuração em vez de confiar nele: só vale com os dois campos preenchidos. */
export function lerConfigDe(dado: unknown): IConfig | null {
  if (typeof dado !== 'object' || dado === null) return null;
  const { oidcAuthority, oidcClientId } = dado as Record<string, unknown>;
  return ehTexto(oidcAuthority) && ehTexto(oidcClientId) ? { oidcAuthority, oidcClientId } : null;
}

/** Confere a resposta de /api/v1/me: um dado fora do formato vira erro, não um painel quebrado. */
export function lerPerfilDe(dado: unknown): IMe {
  const perfil = (typeof dado === 'object' && dado !== null ? dado : {}) as Record<string, unknown>;
  if (!ehTexto(perfil.username) || !Array.isArray(perfil.roles)) throw new Error('A API devolveu um perfil inválido.');
  return {
    username: perfil.username,
    roles: perfil.roles.filter((r): r is string => typeof r === 'string'),
    admin: perfil.admin === true,
    canExecute: perfil.canExecute === true,
  };
}

/** Lê a configuração do login (servida pelo nginx). Qualquer falha ou valor vazio significa "sem login". */
export async function lerConfig(): Promise<IConfig | null> {
  try {
    const res = await fetch('/config.json', { cache: 'no-store', signal: AbortSignal.timeout(TIMEOUT_MS) });
    if (!res.ok) return null;
    return lerConfigDe(await res.json());
  } catch {
    return null; // em desenvolvimento /config.json cai no index.html: não é JSON
  }
}

/** A volta do Keycloak traz ?code=...&state=... na URL. */
export function ehRetornoDoLogin(search: string): boolean {
  const p = new URLSearchParams(search);
  return p.has('code') && p.has('state');
}

function criarGerenciador(cfg: IConfig): UserManager {
  const m = new UserManager({
    authority: cfg.oidcAuthority ?? '',
    client_id: cfg.oidcClientId ?? '',
    redirect_uri: `${window.location.origin}/`,
    post_logout_redirect_uri: `${window.location.origin}/`,
    response_type: 'code',
    scope: 'openid profile',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  });
  m.events.addUserLoaded((u: User) => setAccessToken(u.access_token));
  m.events.addAccessTokenExpired(() => void m.signinRedirect());
  return m;
}

/** O usuário da volta do Keycloak (se a URL trouxer o código) ou o que já estava guardado. */
async function obterUsuario(m: UserManager): Promise<User | null | undefined> {
  if (!ehRetornoDoLogin(window.location.search)) return m.getUser();
  const user = await m.signinRedirectCallback();
  window.history.replaceState({}, '', '/');
  return user;
}

async function buscarPerfil(): Promise<IMe> {
  const res = await fetch('/api/v1/me', { headers: authHeader(), signal: AbortSignal.timeout(TIMEOUT_MS) });
  if (!res.ok) throw new Error(res.status === HTTP_NAO_AUTORIZADO ? 'A API recusou o token.' : `A API respondeu ${res.status}.`);
  return lerPerfilDe(await res.json());
}

/**
 * Porta de entrada do painel. Com login ligado, leva o usuário ao Keycloak (código + PKCE), guarda o token só em memória
 * e pergunta à API quem ele é e o que pode. O painel não decide permissões: a API valida o token e os papéis.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>({ fase: 'carregando' });
  const [mgr, setMgr] = useState<UserManager | null>(null);

  useEffect(() => {
    let vivo = true;
    void (async () => {
      const cfg = await lerConfig();
      if (!vivo) return;
      if (!cfg) {
        setEstado({ fase: 'livre' });
        return;
      }
      const m = criarGerenciador(cfg);
      setMgr(m);
      if (consumirSaida() && !ehRetornoDoLogin(window.location.search)) {
        setEstado({ fase: 'saiu' }); // voltou do Keycloak depois de Sair: não manda para o login sozinho
        return;
      }
      try {
        const user = await obterUsuario(m);
        if (!user || user.expired) {
          await m.signinRedirect();
          return; // a página sai daqui para o Keycloak
        }
        setAccessToken(user.access_token);
        const me = await buscarPerfil();
        if (vivo) setEstado({ fase: 'ok', me });
      } catch (e) {
        if (vivo) setEstado({ fase: 'erro', mensagem: e instanceof Error ? e.message : 'Falha no login.' });
      }
    })();
    return () => {
      vivo = false;
    };
  }, []);

  const valor: IAuth = useMemo(() => {
    if (estado.fase !== 'ok') return SEM_LOGIN;
    const { me } = estado;
    return {
      enabled: true,
      username: me.username,
      roles: me.roles,
      admin: me.admin,
      canExecute: me.canExecute,
      logout: () => {
        setAccessToken(undefined);
        marcarSaida();
        void mgr?.signoutRedirect();
      },
    };
  }, [estado, mgr]);

  if (estado.fase === 'carregando') return <AuthLoadingScreen />;
  if (estado.fase === 'saiu') return <AuthSignedOutScreen onSignIn={() => void mgr?.signinRedirect()} />;
  if (estado.fase === 'erro') return <AuthErrorScreen message={estado.mensagem} onRetry={() => void mgr?.signinRedirect()} />;
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}
