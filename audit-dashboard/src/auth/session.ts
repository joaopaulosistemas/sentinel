// Token de acesso da sessão, mantido só em memória (o oidc-client-ts guarda o resto na sessionStorage da aba).
let accessToken: string | undefined;

export function setAccessToken(token: string | undefined): void {
  accessToken = token;
}

/** Cabeçalho para a API: vazio sem login (o nginx injeta a API_KEY nesse caso). */
export function authHeader(): Record<string, string> {
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

/** Há login corporativo ativo (token em memória)? Sem ele, os links da API funcionam direto (o nginx injeta a API_KEY). */
export function temToken(): boolean {
  return accessToken !== undefined;
}

const CHAVE_SAIDA = 'sentinel.saiu';

/** Anota, na aba, que o usuário pediu para sair: a volta do Keycloak mostra "Você saiu" em vez de pedir login de novo. */
export function marcarSaida(): void {
  try {
    window.sessionStorage.setItem(CHAVE_SAIDA, '1');
  } catch {
    /* sem sessionStorage a volta cai no login, como antes */
  }
}

/** Lê e apaga a anotação de saída: vale uma vez só, para um recarregamento depois levar ao login. */
export function consumirSaida(): boolean {
  try {
    const saiu = window.sessionStorage.getItem(CHAVE_SAIDA) === '1';
    if (saiu) window.sessionStorage.removeItem(CHAVE_SAIDA);
    return saiu;
  } catch {
    return false;
  }
}
