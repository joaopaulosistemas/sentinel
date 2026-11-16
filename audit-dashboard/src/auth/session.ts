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
