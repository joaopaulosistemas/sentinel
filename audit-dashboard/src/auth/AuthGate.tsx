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

const ehTexto = (valor: unknown): valor is string => typeof valor === 'string' && valor.length > 0;

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
    roles: perfil.roles.filter((papel): papel is string => typeof papel === 'string'),
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
  const params = new URLSearchParams(search);
  return params.has('code') && params.has('state');
}

function criarGerenciador(cfg: IConfig): UserManager {
  const gerenciador = new UserManager({
    authority: cfg.oidcAuthority ?? '',
    client_id: cfg.oidcClientId ?? '',
    redirect_uri: `${window.location.origin}/`,
    post_logout_redirect_uri: `${window.location.origin}/`,
    response_type: 'code',
    scope: 'openid profile',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  });
  gerenciador.events.addUserLoaded((usuario: User) => setAccessToken(usuario.access_token));
  gerenciador.events.addAccessTokenExpired(() => void gerenciador.signinRedirect());
  return gerenciador;
}

/** O usuário da volta do Keycloak (se a URL trouxer o código) ou o que já estava guardado. */
async function obterUsuario(gerenciador: UserManager): Promise<User | null | undefined> {
  if (!ehRetornoDoLogin(window.location.search)) return gerenciador.getUser();
  const user = await gerenciador.signinRedirectCallback();
  window.history.replaceState({}, '', '/');
  return user;
}

async function buscarPerfil(): Promise<IMe> {
  const res = await fetch('/api/v1/me', { headers: authHeader(), signal: AbortSignal.timeout(TIMEOUT_MS) });
  if (!res.ok) throw new Error(res.status === HTTP_NAO_AUTORIZADO ? 'A API recusou o token.' : `A API respondeu ${res.status}.`);
  return lerPerfilDe(await res.json());
}

interface IEntrada {
  aindaVivo: () => boolean;
  setEstado: (estado: Estado) => void;
  setMgr: (mgr: UserManager) => void;
}

/** Com a sessão do Keycloak em mãos, termina com o perfil (ou o erro) no estado do AuthGate. */
async function concluirLogin(gerenciador: UserManager, { aindaVivo, setEstado }: Pick<IEntrada, 'aindaVivo' | 'setEstado'>): Promise<void> {
  try {
    const user = await obterUsuario(gerenciador);
    if (!user || user.expired) {
      await gerenciador.signinRedirect();
      return; // a página sai daqui para o Keycloak
    }
    setAccessToken(user.access_token);
    const me = await buscarPerfil();
    if (aindaVivo()) setEstado({ fase: 'ok', me });
  } catch (erro) {
    if (aindaVivo()) setEstado({ fase: 'erro', mensagem: erro instanceof Error ? erro.message : 'Falha no login.' });
  }
}

/** Descobre se há login e, havendo, leva ao Keycloak quando precisa. */
async function entrar(entrada: IEntrada): Promise<void> {
  const { aindaVivo, setEstado, setMgr } = entrada;
  const cfg = await lerConfig();
  if (!aindaVivo()) return;
  if (!cfg) {
    setEstado({ fase: 'livre' });
    return;
  }
  const gerenciador = criarGerenciador(cfg);
  setMgr(gerenciador);
  if (consumirSaida() && !ehRetornoDoLogin(window.location.search)) {
    setEstado({ fase: 'saiu' }); // voltou do Keycloak depois de Sair: não manda para o login sozinho
    return;
  }
  await concluirLogin(gerenciador, entrada);
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
    void entrar({ aindaVivo: () => vivo, setEstado, setMgr });
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
