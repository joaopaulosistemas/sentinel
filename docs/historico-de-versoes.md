# Novidades por versão (resumo antigo do README)

Resumo mantido do README antigo. O histórico completo, com o que foi verificado em cada versão, está em [`evolucao/LOG.md`](evolucao/LOG.md).

## Novidades da v10

- **Evidências offline**: coloque dumps e exports em `evidencias/<nome-do-sistema>/` (veja `docs/evidencias/README.md`). O analisador "Evidências" lê `schema.sql`, estatísticas do `pg_stat_*`, planos `EXPLAIN` JSON, cobertura (JaCoCo/lcov) e histograma de heap. Nenhuma conexão com banco é aberta.
- **Dimensão Performance** no score (seis dimensões) e **ajuste por tamanho** do código (`sentinel.audit.score.density.enabled`, `reference-lines`).
- **Camadas**: Controller→Repository, entidade exposta em Controller, `HttpClient` em componente Angular.
- **Taint (mesmo arquivo)**: parâmetro HTTP → SQL e URL → `innerHTML`. Não cobre fluxo entre arquivos (isso exigiria CodeQL).

### Pacote `core-corporativo` e perfil `corporativo-java-ee-angular` (v10)

Regras extraídas da documentação de arquitetura (sem URLs, credenciais ou dados internos):

- **Java (6):** catch que relança sem causa, `throw` de exceção genérica, `@Stateless` sem interface, `InitialContext` fora de Locator, dinheiro em `double/float`, dado pessoal em log.
- **SQL (5, regex em `*.sql` e `*.queries.xml`):** nomes de constraint (PK_/FK_/DF_/CK_), índice (IDX_) e trigger (TRG_) fora do padrão, `SELECT *` e `LIKE '%…'` em arquivo.
- **Angular (2):** `detectChanges()` manual e componente sem `OnPush`.
- **Spectral** (`market-tools`): guia REST (recurso kebab-case minúsculo, plural, atributos sem abreviação, erro `problem+json`, coleção paginada, gateway Swagger 2).
- O pacote vem desligado por padrão. O perfil `corporativo-java-ee-angular` o liga e deixa a nomenclatura de SQL, `OnPush` e EJB sem interface em modo sombra (aparecem, mas não reprovam).

## Sentinel + MCP (v12.5 a v12.10)

O Claude passa a consultar o Sentinel por MCP, sempre fora da nota (o motor de regras continua sendo a única fonte dela). Seis fases, uma por PR:

1. **Informações de Auditoria**: menu único com achados e nota.
2. **MCP só de leitura**: chave por sistema, nível de dado (padrão "só números e títulos"), ferramentas de consulta e registro das chamadas.
3. **Motor por sistema**: regras, limites, pacotes e analisadores ajustáveis em cada sistema; cadastro de sistema em janela com abas.
4. **Revisão do Claude**: sugestões e regras propostas, com funil (proposta, revisão, teste de amostra, observação em sombra, promoção ou descarte).
5. **Reauditoria**: `iniciar_auditoria` e `suprimir_achado` só criam pedidos; uma pessoa aprova no dashboard. Antes e depois da nota.
6. **Medir e endurecer**: limite de chamadas, métricas de quanto o Claude acerta, testes de segurança.

Guia completo: `docs/integracao/mcp.md`. O token Git digitado na tela dispensa `SECRETS_KEY` no `.env` (a API cria uma chave no volume `audit_secrets`). Histórico por versão em `docs/evolucao/LOG.md`.

## Novidades da v12

- **CI do projeto** (`.github/workflows/ci.yml`): API/CLI, testes Semgrep dos pacotes e dashboard. Modelos para o projeto auditado em `docs/integracao/` (GitHub Actions com gate de código novo e SARIF, pre-commit).
- **Calibração em repositórios públicos** (`docs/evolucao/calibracao.md`): corrigidos 2 falsos positivos (`SELECT *` em `EXISTS`, `<html>` sem `lang` em fragmentos Thymeleaf), 1 falha de detecção (desserialização) e nova regra `sentinel-command-injection-shell`. O Semgrep passou a ignorar bibliotecas de terceiros embutidas (`libs`, `vendor`, `*.min.js`).
- **Dashboard**: 16 testes (vitest + Testing Library, inclui acessibilidade com axe) e versão no cabeçalho.
- **Verificador de regras SQL/XML** (`audit-rules/scripts/testar-regex-sql.py`), porque o `semgrep --test` não avalia essas regras.
- Placar e histórico em `docs/evolucao/LOG.md`.
- **Verificação em CI real**: Maven (suíte Java), imagens Docker da API e do dashboard, `helm lint`/`helm template`, teste ponta a ponta (Postgres + API + auditoria do petclinic) e calibração em repositórios públicos rodam no GitHub Actions.
- **Agentes de evolução** (`docs/evolucao/AGENTE.md`):
  - **Sentinel Evolution** lê o CI com falha e abre PRs `evolucao/*` com a correção, testes positivo e negativo e entrada no LOG; uma vez por semana, com o CI verde, persegue o backlog do placar.
  - **Sentinel Descoberta** escolhe repositórios públicos novos, audita com a API real, revisa uma amostra à mão, cruza os resultados e propõe regras, ajustes e analisadores, além de um relatório por rodada (`docs/evolucao/relatorios/`) e a taxa de acerto medida por regra (`docs/evolucao/precisao.md`).
  - Os dois só abrem PR; o merge é manual. O placar com critérios verificáveis está em `docs/evolucao/PLACAR.md` (hoje 2,0; meta 2,5). **O Sentinel ainda não está no nível profissional**; os agentes relatam evidências e lacunas e não declaram esse nível.
- **Pacotes**: `core-quality` 1.0.2 (`@ts-nocheck` em código gerado deixa de ser achado; `any` como argumento genérico) e `core-security` 1.0.1 (JWT com segredo fixo ou padrão em JS/TS). As regras do Angular no ESLint só valem quando o projeto depende de `@angular/*`.
- **Limite do GitHub Actions**: repositório privado no plano gratuito tem 2000 min/mês; sem minutos os jobs não iniciam. Veja o orçamento em `docs/evolucao/AGENTE.md`.
- **Quando os agentes rodam**: Descoberta toda segunda; Evolution quando o CI da `main` falha e às quartas (backlog); CI completo com calibração aos domingos e em PRs `evolucao/*`. Pushes só de documentação não disparam o CI.
- **Descoberta, rodada 2**: 4 repositórios novos (`audit-rules/calibracao/descobertos.txt`), 4 PRs verdes mesclados. A revisão manual soma 10 achados em 4 repositórios (`precisao.md`); isso é indicativo, não uma medição estatística de precisão.
- **Mapa da arquitetura**: `docs/arquitetura/ARQUITETURA.md` descreve API, dashboard, CLI, pacotes de regras e automação, com 13 diagramas e a ligação entre funcionalidades e componentes.
