import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { InlineNotification, Loading } from '@carbon/react';
import { AuthContext, SEM_LOGIN, type IAuth } from './AuthContext';
import { authHeader, setAccessToken } from './session';

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

type Estado = { fase: 'carregando' } | { fase: 'livre' } | { fase: 'erro'; mensagem: string } | { fase: 'ok'; me: IMe };

/** Lê a configuração do login (servida pelo nginx). Qualquer falha ou valor vazio significa "sem login". */
export async function lerConfig(): Promise<IConfig | null> {
  try {
    const res = await fetch('/config.json', { cache: 'no-store' });
    if (!res.ok) return null;
    const cfg = (await res.json()) as IConfig;
    return cfg.oidcAuthority && cfg.oidcClientId ? cfg : null;
  } catch {
    return null; // em desenvolvimento /config.json cai no index.html: não é JSON
  }
}

/** A volta do Keycloak traz ?code=...&state=... na URL. */
export function ehRetornoDoLogin(search: string): boolean {
  const p = new URLSearchParams(search);
  return p.has('code') && p.has('state');
}

async function buscarPerfil(): Promise<IMe> {
  const res = await fetch('/api/v1/me', { headers: authHeader() });
  if (!res.ok) throw new Error(res.status === 401 ? 'A API recusou o token.' : `A API respondeu ${res.status}.`);
  return (await res.json()) as IMe;
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
      const m = new UserManager({
        authority: cfg.oidcAuthority!,
        client_id: cfg.oidcClientId!,
        redirect_uri: `${window.location.origin}/`,
        post_logout_redirect_uri: `${window.location.origin}/`,
        response_type: 'code',
        scope: 'openid profile',
        automaticSilentRenew: true,
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
      });
      setMgr(m);
      m.events.addUserLoaded((u: User) => setAccessToken(u.access_token));
      m.events.addAccessTokenExpired(() => void m.signinRedirect());
      try {
        let user: User | null | undefined;
        if (ehRetornoDoLogin(window.location.search)) {
          user = await m.signinRedirectCallback();
          window.history.replaceState({}, '', '/');
        } else {
          user = await m.getUser();
        }
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
        void mgr?.signoutRedirect();
      },
    };
  }, [estado, mgr]);

  if (estado.fase === 'carregando') return <Loading description="Entrando…" withOverlay={false} />;
  if (estado.fase === 'erro') {
    return (
      <div style={{ padding: '2rem' }}>
        <InlineNotification kind="error" lowContrast hideCloseButton title="Não foi possível entrar" subtitle={estado.mensagem} />
        <button type="button" onClick={() => void mgr?.signinRedirect()}>Entrar de novo</button>
      </div>
    );
  }
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}
