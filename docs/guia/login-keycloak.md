# Login com Keycloak (admin e executor)

O Sentinel pode exigir login. O Keycloak identifica a pessoa, e a API confere o token a cada chamada e aplica o papel.

## Subir o protótipo

O Keycloak **não** está no `docker-compose.yml` principal; fica num arquivo à parte. Use os dois juntos:

```bash
docker compose -f docker-compose.yml -f deploy/keycloak/docker-compose.yml up -d --build
```

- Painel: http://localhost:3000 (abre direto na tela de login do Keycloak)
- Keycloak: http://localhost:8180
- Neste modo a `API_KEY` fica desligada: o acesso é pelo login.
- Sem o arquivo do Keycloak, tudo continua como antes (`API_KEY` ou `ALLOW_OPEN_API=true`).

## Logins prontos (só para desenvolvimento)

| Usuário | Senha | Papel | Pode |
|---|---|---|---|
| `admin` | `admin` | `sentinel-admin` | tudo |
| `executor` | `executor` | `sentinel-executor` | consultar tudo e disparar auditorias |

O console do Keycloak (administração de usuários) é outro login: `kcadmin` / `kcadmin` (troque com `KEYCLOAK_ADMIN_PASSWORD`).

## Papéis

| Ação | executor | admin |
|---|---|---|
| Consultar (qualquer GET) | sim | sim |
| Disparar auditoria (`POST /api/v1/audits`) | sim | sim |
| Cadastrar, alterar ou apagar (sistemas, supressões etc.) | não (403) | sim |
| Configurações (`/api/v1/settings`) | não (403) | sim |
| `GET /api/v1/me` | qualquer pessoa autenticada | |

Sem token ou com token inválido a API responde 401. Quem tem login mas nenhum papel recebe 403 em tudo. A regra está em `RolePolicy` e o filtro em `RoleAuthorizationFilter`.

## Criar outro usuário

1. Entre em http://localhost:8180 com `kcadmin` e escolha o realm `sentinel`.
2. Users → Add user, preencha o usuário e salve.
3. Aba Credentials: defina a senha e desmarque "Temporary".
4. Aba Role mapping: atribua `sentinel-executor` ou `sentinel-admin`.

O Keycloak do protótipo não tem volume: usuários criados no console somem quando o container é recriado (`down`). Para um usuário fixo, inclua-o em `deploy/keycloak/realm-sentinel.json` (importado na subida).

## Como o token é validado

A API só aceita um token que (1) tenha assinatura válida do Keycloak, (2) venha do emissor configurado e (3) tenha `aud=sentinel-api`; um token do mesmo Keycloak feito para outro cliente é recusado. O nome vem de `preferred_username` e os papéis de `realm_access.roles`. O painel usa o fluxo de código com PKCE e guarda o token só em memória.

## Produção

As senhas e o `start-dev` são só para teste. Em produção, use um Keycloak real e configure na API `QUARKUS_PROFILE=oidc`, `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID` e `OIDC_AUDIENCE`, e no painel `OIDC_AUTHORITY`, `OIDC_CLIENT_ID` e `OIDC_ORIGIN`. A imagem da API precisa do build arg `WITH_OIDC=true`. O Helm ainda não tem este modo. Limitação: o login separa por papel, não por squad.

Verificação de ponta a ponta: `scripts/verificar-login-keycloak.sh` (roda no CI no workflow "Login com Keycloak").
