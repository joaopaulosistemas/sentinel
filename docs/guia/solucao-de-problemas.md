# Solução de problemas

Erros comuns na subida e no uso, com a causa e o que fazer.

| Sintoma | Causa | O que fazer |
|---|---|---|
| Painel mostra `Erro ao carregar ... HTTP 502: Bad Gateway` | O nginx do painel guarda o IP antigo da API, que foi recriada | `docker compose restart audit-dashboard` ([combinações](compose-combinacoes.md)) |
| A API não sobe e pede chave | Sem `API_KEY` e sem OIDC a API recusa subir | Defina `API_KEY` no `.env` (`openssl rand -hex 32`). Só em desenvolvimento local: `ALLOW_OPEN_API=true` |
| `HTTP 401` ao chamar a API | Toda a API exige a chave | Envie `X-API-Key: <chave>` (ou `Authorization: Bearer`). Pelo painel a chave é injetada pelo nginx. `/api/v1/health` é livre |
| Build falha com `CERTIFICATE_VERIFY_FAILED` (pip) ou erro de TLS | A rede da empresa reassina o TLS | Coloque o CA raiz em `audit-api/certs/` e rode `./audit-api/setup-certs.sh` ([certificados](certificados.md)). Sem o CA, só nessa rede: `INSECURE_SSL=true` no `.env` (desliga a verificação só durante o build) |
| `COPY audit-api/certs/ ... not found` | A pasta de certificados não existe | Ela precisa existir com ao menos um arquivo (o README dela basta) |
| `service "audit-api" has neither an image nor a build context` | Comando de overlay sem o arquivo base | Informe `-f docker-compose.yml` antes do overlay ([combinações](compose-combinacoes.md)) |
| Keycloak não sobe | Subiu só o `docker-compose.yml`, ou sem o overlay | `docker compose -f docker-compose.yml -f deploy/keycloak/docker-compose.yml up -d --build`; veja `docker compose logs keycloak` |
| Hora do relatório diferente da do painel | Relatórios usam `REPORT_TIMEZONE` (padrão `America/Sao_Paulo`); o painel usa o fuso do navegador | Defina `REPORT_TIMEZONE` no `.env` e recrie a API |
| Aviso `PMD terminou com código 1` e resultado parcial | O PMD teve exceção interna em algum arquivo | O resultado vale para o que foi analisado. Para investigar, pegue a saída completa: `docker compose logs audit-api` na hora da auditoria e abra uma issue com ela |
| Trivy recebe `429` do Maven Central | Muitas consultas do mesmo IP | Use o [cache Maven](cache-maven.md) (`TRIVY_OFFLINE=true`) |
| Repositório recusado: "rede interna" | A API não audita hosts internos por padrão | Liste o host em `ALLOWED_INTERNAL_HOSTS`. No Helm, libere também `networkPolicy.extraEgress` |
| Tokens Git salvos não abrem depois de recriar o ambiente | A chave que cifra os segredos mudou ou o volume sumiu | Mantenha `SECRETS_KEY` fixa, ou guarde o volume `sentinel-audit-secrets` junto com o banco ([operação](operacao.md)) |
| Auditoria acusa ruído que o código já corrigiu | Imagem antiga: a API roda sobre os fontes novos, mas com regras da imagem velha | `docker compose up -d --build` e `docker compose restart audit-dashboard` |

Variáveis citadas: [referência](../referencia/variaveis-de-ambiente.md).
