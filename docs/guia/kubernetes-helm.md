# Kubernetes com Helm

O chart fica em `deploy/helm/sentinel/` (API, painel e PostgreSQL opcional). `helm lint` e `helm template` rodam no CI. Os valores padrão e os comentários estão em `values.yaml`.

## Antes de instalar

1. **Publique as imagens** `sentinel-audit-api` e `sentinel-audit-dashboard` no seu registry, com a tag fixa de `values.yaml` (nunca `:latest`), e ajuste `api.image` e `dashboard.image`.
2. **Crie o Secret** com as chaves `DB_PASSWORD`, `API_KEY` e `SECRETS_KEY` e informe `secrets.existingSecret` (preferível a pôr valores no chart). A `apiKey` é obrigatória: sem ela, e sem OIDC, a API recusa subir.
3. **Escolha o banco.** `database.internal: true` sobe um PostgreSQL simples (bom para teste). Em produção use um banco gerenciado: `database.internal: false`, `database.url` (`jdbc:postgresql://host:5432/sentinel_audit`) e o CIDR do banco em `networkPolicy.databaseCidrs`.

## Instalar

```bash
helm upgrade --install sentinel deploy/helm/sentinel \
  --namespace sentinel --create-namespace \
  -f meus-valores.yaml
```

## Pontos de atenção

| Assunto | O que saber |
|---|---|
| Réplicas da API | Mais de uma exige o agendador desligado em todas menos uma (`SCHEDULER_ENABLED=false`, via `api.env`) |
| Limites | A API tem limite de CPU de propósito: sem ele, as ferramentas externas de uma auditoria podem tomar o nó |
| Saída de rede | `networkPolicy` bloqueia faixas privadas, metadados de nuvem e CGNAT (anti-SSRF). Git corporativo interno: `networkPolicy.extraEgress` e `ALLOWED_INTERNAL_HOSTS` (em `api.env`). Exige um plugin de rede que aplique NetworkPolicy |
| Ingress | Desligado por padrão. `ingress.allowWithoutLogin` só vale `true` se outra camada (VPN, proxy autenticador) já protege o acesso |
| Variáveis extras | `api.env` (por exemplo `REPORT_TIMEZONE`, `OSV_ENABLED`). A lista está em [variáveis](../referencia/variaveis-de-ambiente.md) |
| Login Keycloak/OIDC | **O chart ainda não tem este modo.** O login com papéis funciona no Compose ([login-keycloak.md](login-keycloak.md)) |
| Banco e atualização | O esquema roda sozinho no start da API (Flyway). Veja [operação](operacao.md) |
