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

O realm de teste tem os clientes `sentinel-dashboard` (painel, código + PKCE), `sentinel-cli` (CLI) e `sentinel-api` (a API, que só valida o token) e `sentinel-teste-sem-audiencia` (só para provar que um token sem a audiência certa é recusado).

A API só aceita um token que (1) tenha assinatura válida do Keycloak, (2) venha do emissor configurado e (3) tenha `aud=sentinel-api`; um token do mesmo Keycloak feito para outro cliente é recusado. O nome vem de `preferred_username` e os papéis de `realm_access.roles`. O painel usa o fluxo de código com PKCE e guarda o token só em memória.

## Produção

### Esqueci a senha (Mailpit)

O overlay sobe também o **Mailpit**, uma caixa de e-mails de teste: o Keycloak envia para ele e nada sai da sua máquina. Na tela de login, "Esqueceu a senha?" pede o e-mail (`admin@sentinel.local` ou `executor@sentinel.local`); abra **http://localhost:8025**, clique no link do e-mail recebido e defina a nova senha. O link usa `http://localhost:8180`, então abre no mesmo computador. Para outras pessoas abrirem, o `KC_HOSTNAME` do overlay precisa ser o endereço de rede da máquina.

Quem já tinha o Keycloak criado precisa recriá-lo para receber a configuração do realm: `docker compose -f docker-compose.yml -f deploy/keycloak/docker-compose.yml up -d --force-recreate keycloak mailpit` (como o protótipo não guarda os dados do Keycloak, os usuários criados à mão se perdem). **Em produção** troque o `smtpServer` do realm (host, porta, usuário e senha) por um SMTP real; o Mailpit é só para teste. O workflow "Login com Keycloak" confere que o e-mail chega com o link.

As senhas e o `start-dev` são só para teste. Em produção, use um Keycloak real e configure na API `QUARKUS_PROFILE=oidc`, `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID` e `OIDC_AUDIENCE`, e no painel `OIDC_AUTHORITY`, `OIDC_CLIENT_ID` e `OIDC_ORIGIN`. A imagem da API precisa do build arg `WITH_OIDC=true`. O Helm ainda não tem este modo. Limitação: o login separa por papel, não por squad.

Verificação de ponta a ponta: `scripts/verificar-login-keycloak.sh` (roda no CI no workflow "Login com Keycloak").

## Tela de login (tema do Sentinel)

A tela de login do Keycloak usa o tema `sentinel` (`deploy/keycloak/themes/sentinel/login/`), no padrão Carbon do painel: painel de marca em cinza escuro à esquerda, com um radar de anéis finos ao fundo (`resources/img/radar.svg`), o nome "Sentinel Audit Tool" com a frase "Plataforma de auditoria técnica contínua de código" embaixo, e a chamada e o texto de apoio centralizados na vertical; à direita, o formulário no mesmo cinza escuro (cinza 100 do Carbon, sem branco na tela), separado por uma linha fina, com campos em cinza 90 e linha só embaixo, botão grande com seta (`resources/img/seta.svg`) e o erro de senha em vermelho. Fonte IBM Plex Sans (em `resources/fonts`, licença OFL). É só CSS sobre o tema padrão `keycloak.v2`, ligado pelo realm (`loginTheme`) e montado no container pelo overlay do Compose; o realm também fica em português (`pt-BR`). Abaixo de 900 px o painel de marca some e o nome aparece acima do formulário. Para trocar a frase ou as cores, edite `resources/css/sentinel.css` e reinicie o Keycloak. As telas "Entrando…" e "Não foi possível entrar" do painel (`audit-dashboard/src/auth/AuthScreens.tsx`) seguem o mesmo estilo e ficam sempre escuras (tema `g100` do Carbon), mesmo que o painel esteja no tema claro. O workflow "Login com Keycloak" guarda uma captura da tela (artefato `tela-login-keycloak`).
