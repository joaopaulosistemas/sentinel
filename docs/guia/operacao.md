# Operação: backup, atualização e restauração

Comandos para o Compose. Use os mesmos arquivos `-f` do seu `up` ([combinações](compose-combinacoes.md)).

## O que guardar

| Dado | Onde | Backup |
|---|---|---|
| Sistemas, auditorias, achados, supressões, configurações | PostgreSQL, volume `sentinel-audit-postgres-data` | `pg_dump` (abaixo) |
| Chave que cifra os tokens e segredos | `SECRETS_KEY` no `.env`, ou o volume `sentinel-audit-secrets` (`/var/lib/sentinel/secrets.key`) quando `SECRETS_KEY` e `API_KEY` estão vazias | Guarde junto com o banco: sem a chave os segredos salvos não abrem |
| `.env` | Raiz do projeto | Cópia em local seguro (não versionar) |
| Clones dos repositórios | Volume `sentinel-audit-clones` | Não precisa: são refeitos a cada auditoria |

## Backup do banco

```bash
docker compose exec -T postgres pg_dump -U sentinel_audit -d sentinel_audit -Fc > sentinel-$(date +%F).dump
```

## Restaurar

Com a API parada, para não escrever durante a restauração:

```bash
docker compose stop audit-api audit-dashboard
docker compose exec -T postgres dropdb   -U sentinel_audit --if-exists sentinel_audit
docker compose exec -T postgres createdb -U sentinel_audit sentinel_audit
docker compose exec -T postgres pg_restore -U sentinel_audit -d sentinel_audit < sentinel-AAAA-MM-DD.dump
docker compose up -d
```

Se o `dropdb` recusar (conexões abertas), confirme que a API está parada. Restaurar apaga o que está no banco atual.

## Atualizar a versão

```bash
git pull
docker compose up -d --build
docker compose restart audit-dashboard   # o nginx guarda o IP da API recriada (senão: 502)
```

As migrações do Flyway rodam sozinhas quando a API sobe. Banco criado por versão antiga (Hibernate) entra em baseline na versão 1 e recebe as demais. Para ver o que foi aplicado:

```bash
docker compose exec postgres psql -U sentinel_audit -d sentinel_audit \
  -c "select version, description, success from flyway_schema_history order by installed_rank"
```

## Voltar de versão

Não há migração de volta. Para voltar a uma versão anterior que não conhece o esquema novo, restaure o backup feito **antes** da atualização. Por isso faça o `pg_dump` antes de cada `--build` com versão nova.

## Limpar espaço

`AUDIT_RETENTION_DAYS` apaga auditorias antigas por dia (guardando as `AUDIT_RETENTION_KEEP` mais recentes de cada sistema). `docker compose down` mantém os volumes; `down -v` **apaga o banco**.
