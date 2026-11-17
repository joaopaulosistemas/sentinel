# Combinações do Compose

O `docker-compose.yml` da raiz sobe o Sentinel completo (PostgreSQL, API e painel). Keycloak e observabilidade são **overlays**: arquivos em `deploy/` que só acrescentam serviços e **não funcionam sozinhos**, porque dependem do arquivo base (por isso `docker compose -f deploy/observabilidade/docker-compose.yml down` dá `service "audit-api" has neither an image nor a build context specified: invalid compose project`). Sempre informe o base primeiro.

| O que subir | Comando |
|---|---|
| Só o Sentinel | `docker compose up -d --build` |
| Com login Keycloak | `docker compose -f docker-compose.yml -f deploy/keycloak/docker-compose.yml up -d --build` |
| Com observabilidade | `docker compose -f docker-compose.yml -f deploy/observabilidade/docker-compose.yml up -d --build` |
| Com HTTPS | `docker compose -f docker-compose.yml -f deploy/https/docker-compose.yml up -d --build` ([https.md](https.md)) |
| Com os dois (Keycloak e observabilidade) | `docker compose -f docker-compose.yml -f deploy/keycloak/docker-compose.yml -f deploy/observabilidade/docker-compose.yml up -d --build` |

Detalhes: [login-keycloak.md](login-keycloak.md) e [observabilidade.md](observabilidade.md).

## Use os mesmos arquivos em todos os comandos

`ps`, `logs`, `down` e `restart` precisam dos mesmos `-f` do `up`; sem eles o Compose não enxerga os serviços do overlay. Para não repetir, defina no `.env` (separador `:`):

```bash
COMPOSE_FILE=docker-compose.yml:deploy/keycloak/docker-compose.yml
```

A partir daí `docker compose up -d --build`, `ps` e `down` já usam os dois arquivos.

## Depois de recriar a API, reinicie o painel

O nginx do painel guarda o IP da API de quando subiu. Se a API for recriada (build novo, troca de arquivo ou variável), o painel passa a responder `502 Bad Gateway` até reiniciar:

```bash
docker compose restart audit-dashboard
```

(com os mesmos `-f` que você usa).

## Voltar ao modo simples

Trocar de combinação recria os contêineres. Ao tirar o Keycloak, a API volta a exigir `API_KEY` (ou `ALLOW_OPEN_API=true` em desenvolvimento local). Os dados do banco ficam, porque o volume `sentinel-audit-postgres-data` não é apagado por `down` (só por `down -v`).
