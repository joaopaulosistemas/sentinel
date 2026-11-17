# HTTPS

Por padrão o Compose publica tudo só em `127.0.0.1`, em HTTP: serve para uso na própria máquina. Para outras pessoas da rede acessarem, ponha HTTPS na frente do painel. O overlay `deploy/https/` faz isso com o Caddy, **sem domínio público e sem certificado comprado**: o Caddy cria uma autoridade certificadora local e assina o certificado do seu endereço (`tls internal`).

## Subir

1. No `.env`, defina o endereço pelo qual as pessoas vão acessar (nome interno ou o IP da máquina):

   ```bash
   SENTINEL_HOST=192.168.0.10
   ```

   Sem isso vale `localhost`.

2. Suba junto com o arquivo base ([combinações](compose-combinacoes.md)):

   ```bash
   docker compose -f docker-compose.yml -f deploy/https/docker-compose.yml up -d --build
   ```

3. Acesse `https://<SENTINEL_HOST>`. A porta 80 só redireciona para o HTTPS. O painel (3000) e a API (8080) continuam só em `127.0.0.1`: de fora só se chega pelo HTTPS.

## O aviso do navegador

O certificado é de uma autoridade criada pelo seu Caddy, que os computadores ainda não conhecem. Na primeira vez o navegador avisa "conexão não segura". Há duas saídas:

- **Aceitar o aviso** (teste, ou uma pessoa só).
- **Instalar a autoridade em cada computador** (o aviso some de vez). Copie o certificado raiz do Caddy:

  ```bash
  docker compose -f docker-compose.yml -f deploy/https/docker-compose.yml cp https:/data/caddy/pki/authorities/local/root.crt ./sentinel-root.crt
  ```

  e instale esse arquivo como autoridade confiável no sistema ou no navegador de cada pessoa (no Windows, "Autoridades de Certificação Raiz Confiáveis").

Não apague o volume `sentinel-audit-https-data`: ele guarda a autoridade. Se for recriado, cada computador precisa confiar de novo.

## Com Keycloak

O navegador bloqueia misturar página HTTPS com um Keycloak em HTTP. Ao usar o [login](login-keycloak.md) atrás do HTTPS:

- o `redirect_uri` e as origens do cliente no Keycloak (`realm-sentinel.json`) e o `OIDC_ORIGIN` do painel devem usar `https://<SENTINEL_HOST>`;
- o Keycloak também precisa ser servido em HTTPS (por exemplo, um segundo bloco no `Caddyfile`), e `OIDC_AUTHORITY` e `OIDC_AUTH_SERVER_URL` apontam para ele;
- o endereço público do Keycloak tem de ser o mesmo para o navegador e para a API (campo `iss` do token).

Esta combinação ainda não tem verificação automática: teste o login depois de subir.

## Se tiver domínio público

Troque `tls internal` por nada (apague a linha) no `Caddyfile` e use o domínio como `SENTINEL_HOST`: o Caddy emite e renova o certificado do Let's Encrypt sozinho. As portas 80 e 443 precisam estar abertas para a internet.

## Verificação

`scripts/verificar-https.sh` (rodado pelo CI) sobe só o Caddy e confere que o TLS termina, que o HTTP redireciona para o HTTPS e que o certificado é o da autoridade local. Não cobre o painel nem o login por trás dele.
