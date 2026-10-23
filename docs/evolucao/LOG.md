# Registro de evolução do Sentinel

Placar de 0 a 3 por eixo (0 ausente, 1 frágil, 2 funciona e é testado, 3 validado em repositório real).

## Volta 1 — 2026-10-21

Placar antes → depois (avaliado com evidência no repositório):

| Eixo | Antes | Depois | Evidência |
|---|---|---|---|
| 1 Cobertura de regras | 2 | 2 | 171 regras; segurança com CWE/OWASP; +4 CWE (System.exit, SSH, network host, printStackTrace) |
| 2 Precisão | 1 | 1 | modo sombra e supressão existem; falso positivo nunca medido em repositório real |
| 3 Padrões de saída | 2 | 2 | SARIF/JUnit no CLI, SBOM CycloneDX |
| 4 Quality gate e tendência | 2 | 2 | `--fail-below`, `--fail-on-new`, diff entre execuções |
| 5 Integração | 1 | 2 | `.github/workflows/ci.yml` do projeto, modelo de GitHub Actions para PR, modelo de pre-commit |
| 6 Remediação | 2 | 2 | uma entrada por regra exigida pelo teste estrito |
| 7 Dashboard | 1 | 2 | 14 testes (vitest + Testing Library), `tsc` e build verdes |
| 8 Operação | 1 | 1 | OIDC opcional; sem teste de carga nem imagem verificada |
| 9 Qualidade interna | 2 | 2 | 363 testes Java; verificador próprio para regras de SQL/XML |
| 10 Credibilidade | 0 | 0 | sem calibração em repositório real |

Lacunas atacadas: integração/CI (eixo 5), testes do dashboard (eixo 7), CWE nas regras sem mapeamento.

Verificado: bateria Java (363), testes Semgrep dos 8 pacotes (SQL via `audit-rules/scripts/testar-regex-sql.py`), `vitest` (14), `tsc`, `vite build`.
Não verificado: os workflows do GitHub não rodaram num GitHub real; Maven, imagem Docker e Quarkus real não foram executados aqui.

## Volta 2 — 2026-10-21

| Eixo | Antes | Depois | Evidência |
|---|---|---|---|
| 1 Cobertura de regras | 2 | 2 | recall verificado no WebGoat; +1 regra (`command-injection-shell`), 1 regra de desserialização corrigida |
| 2 Precisão | 1 | 2 | falsos positivos medidos em 4 repositórios reais e corrigidos (ver `calibracao.md`); 134→80 e 5619→1609 achados |
| 10 Credibilidade | 0 | 1 | calibração em 4 repositórios públicos; faltam projetos do João (precisa de 3 reais para chegar a 3) |

Atualização na mesma volta: calibração nos repositórios do João (Frequency e Sentinel) corrigiu mais 4 regras (ver `calibracao.md`). Credibilidade passa a 2 (6 repositórios, 2 deles do João), sem chegar a 3 porque não houve revisão manual completa dos achados.

Verificado: bateria Java 364 testes verdes; Semgrep dos 8 pacotes.
Não verificado: calibração só das regras (não do pipeline completo); sem revisão manual de todos os achados.

## Volta 3 — 2026-10-21

- Workflows validados com `actionlint`: o CI do projeto passou limpo; o modelo de PR tinha injeção de script (`github.head_ref` direto no `run`) e foi corrigido (valores externos por variável de ambiente).
- Desempenho medido: varredura Semgrep com as 158 regras em 1.212 arquivos (WebGoat, 42 MB) levou 28 s; o tempo de casamento das regras soma 4,9 s e nenhuma regra isolada passa de 1,6 s (`sentinel-api-path-traversal`). Sem gargalo de regra.
- Dashboard: 2 testes de acessibilidade com axe (vitest-axe) sobre a casca, o badge e a nota; sem violações (16 testes no total).
- Nenhum eixo mudou de nota nesta volta. Os bloqueios que restam são externos: build real (Maven/Quarkus/Docker) e revisão manual de achados.

## Volta 4 — 2026-10-21 (verificação em CI real)

O GitHub Actions do repositório passou a ser o ambiente de verificação: o Maven e o Docker não rodam no ambiente de desenvolvimento do Claude, mas rodam no runner. O log completo não é acessível ao Claude, então o CI publica a causa das falhas como anotações (`scripts/ci-anotar.sh` e função `anotar` no job das regras), lidas pela API de check-runs.

Verificado no CI real (4 jobs verdes no commit da volta):
- `mvn verify` completo: compilação do Quarkus e todos os testes Java.
- Imagem Docker da API e do dashboard construídas.
- `helm lint` e `helm template` do chart (com o segredo obrigatório informado).
- Testes Semgrep dos 8 pacotes com Semgrep 1.172.0 fixo, mais o verificador de SQL/XML.
- Dashboard: `tsc`, 16 testes e build.

Defeitos encontrados só por rodar de verdade, todos corrigidos:
- `PackToolTest` e `RulePlaygroundServiceTest` precisam do Semgrep instalado no job do Maven.
- O Semgrep ignora pastas `tests/` dentro de repositório git; o verificador de SQL passou a copiar os casos para outra pasta.
- Uma linha sobrando no próprio workflow derrubava o job das regras.

| Eixo | Antes | Depois | Evidência |
|---|---|---|---|
| 8 Operação | 1 | 2 | imagens e chart Helm construídos/validados em CI; OIDC e carga ainda não verificados |
| 9 Qualidade interna | 2 | 2 | suíte Java passa no Maven real, não só no harness |

Média do placar: 2,0 (meta 2,5). Faltam: revisão manual dos achados (credibilidade 3), subir o Quarkus de verdade com banco e rodar uma auditoria ponta a ponta no CI, teste do login OIDC, métricas da própria ferramenta e corrigir o N+1 do `RuleQualityService`/`AuditScheduler`.

## Evolução — 2026-10-21 (`sentinel-ts-any-explicito`)

- **Classe:** falso negativo (o CI do pacote `core-quality` falhou no teste do caso positivo).
- **Causa:** a regex só cobria `: any`, `as any`, `<any>` e `any[]`; `any` como argumento genérico com outros parâmetros (`Record<string, any>`, `Map<any, string>`) não casava.
- **Evidência:** entrada da execução 39651811612, `✖ sentinel-ts-any-explicito` em `audit-rules/packs/core-quality`; caso `Record<string, any>` em `tests/sentinel-typescript.ts`.
- **O que mudou:** `<any>` virou `<(?:[^<>(){};=]*,\s*)?any\s*[,>]`; casos `ruleid` (`Map<any, string>`) e `ok` (`Record<string, unknown>`, `chamar(a, anyValue, b)`); `pack.yml` 1.0.0 → 1.0.1.
- **Verificado:** a regex, em pasta temporária com Semgrep 1.172.0, casa `Record<string, any>`, `Map<any, string>`, `Array<any>` e `: any`, e não casa os casos `ok`.
- **Não verificado:** `semgrep --test` do pacote inteiro (o ambiente não permitiu montar a pasta como o CI); impacto da regex em repositórios reais (calibração); o CI completo.

## Evolução — 2026-10-21 (`PackToolTest` com versão fixa do `core-quality`)

- **Classe:** regressão (defeito do teste, não da regra).
- **Causa:** o commit 64d56b3 subiu o `core-quality` para 1.0.1, mas `PackToolTest.validaOsPacotesEmbutidosEListaPorPacote` esperava a saída `core-quality v1.0.0`.
- **Evidência:** execução 39653677569, `PackToolTest.java:39` falhou (1 falha em 349 testes); o `pack.yml` do `core-quality` está em `version: 1.0.1`.
- **O que mudou:** a asserção passou a esperar `v1.0.1`; a contagem (41 regras) e as demais verificações ficam iguais. O PR parte da branch `evolucao/ts-any-generico`.
- **Verificado:** só a leitura do código e do `pack.yml`.
- **Não verificado:** o Maven não rodou localmente; o CI completo decide. Os erros de `git clone` das auditorias 1 e 7 nos logs são cenários de teste de falha, sem evidência de defeito da ferramenta.

## 2026-10-21/22 — agentes em funcionamento, placar e limite do Actions

- **Sentinel Evolution validado de ponta a ponta** com defeitos plantados em branches descartáveis: o agente classificou, corrigiu, abriu PR e disparou o CI; quando o CI reprovou (`PackToolTest` com versão fixa), abriu um segundo PR que corrigiu e deixou o CI verde. Descoberta: sem a opção "Allow GitHub Actions to create and approve pull requests" o agente empurra a branch mas não abre o PR.
- **Sentinel Descoberta criado** (`descoberta.yml`, `scripts/descobrir-repos.sh`, `scripts/relatorio-descoberta.py`, `.github/agente/sentinel-descoberta.md`). Primeira rodada com resultado: 4 repositórios, 4 PRs (#4 ESLint do Angular só com `@angular/*`, #5 JWT com segredo fixo, #6 `@ts-nocheck` em código gerado, #7 relatório). A rodada anterior terminou em 71 s sem PR e sem rastro; passou a existir uma anotação com o resumo final do agente.
- **Merge dos 4 PRs na `main`** a pedido. O CI da `main` depois do merge **não rodou**: os jobs não iniciaram porque a cota gratuita de 2000 min/mês do Actions acabou. Pendente: rodar o CI da `main` quando houver minutos.
- **Placar** movido para `docs/evolucao/PLACAR.md` com critério verificável por eixo e backlog; `docs/evolucao/precisao.md` criado. Média continua 2,0 (todos os eixos em 2), meta 2,5.
- **Custo de Actions reduzido**: calibração só no domingo, sob demanda e em PRs do agente; Evolution só em CI com falha e quarta-feira; Descoberta só na segunda.
- **Limites conhecidos**: a regra de JWT não cobre o segredo padrão passado por função auxiliar (`arg({ default })`); a revisão manual cobriu 10 achados em 4 repositórios; o CI da `main` combinada ainda não foi conferido.

## Descoberta — 2026-10-22 (5 repositórios NestJS)

- **Revisão:** 81 achados lidos em 5 repositórios (`docs/evolucao/relatorios/2026-10-22.md`); `precisao.md` soma 91 achados em 9 repositórios. Eixo 10 continua 2 (faltam ≥ 100 achados em ≥ 10 repositórios).
- **PRs de evolução:** #9 CORS aberto em Node/Nest (falso negativo), #10 segredo/token em log (falso negativo), #11 `isVulnerable` do node-fetch 2.7.0 (falso positivo HIGH).
- **Pendente:** ruído de `no-super-linear-move` (112 em um repositório) e `detect-non-literal-fs-filename` (0/6 útil), aninhamento contando objeto literal, `CleanCodeAnalyzer` lendo `class X extends Base(...)` como função.
- **Não verificado:** CI dos PRs; recall; regras não lidas listadas no relatório.

## Evolução — 2026-10-22 (backlog 10/2: `precisao.md`)

- **Classe:** nenhuma falha. A entrada da execução 39734246964 veio sem anotações de erro nem resultado de jobs; fui ao backlog do `PLACAR.md`.
- **Evidência:** o primeiro item aberto (criar e alimentar `precisao.md`) já estava cumprido: `docs/evolucao/precisao.md` existe com 8 regras e 10 achados revisados em 4 repositórios.
- **O que mudou:** só a marcação `[x]` do item no backlog. Nota do eixo 10 continua 2.
- **Não verificado:** o CI da `main` (jobs sem resultado na entrada, provavelmente por falta de minutos do Actions). Só documentação, nenhum teste rodado.


## 2026-10-22 — Mudança de estratégia: medição independente e universo Java

**Diagnóstico** (a pedido, revisando a forma de trabalho dos agentes): universo estreito (9 repositórios, todos NestJS/Express), revisão
feita pelo mesmo tipo de agente que escreve a regra, amostras de 5 a 8 achados por regra, recall sem medição, regras nascidas de 1 ou 2
repositórios, ruído em regras com 0 verdadeiros positivos, e um Evolution que fechava item de backlog só documentando.

**Medição nova:** `scripts/benchmark-owasp.py` mede TPR, FPR e pontuação (TPR − FPR) por categoria no OWASP Benchmark (2.740 casos Java com
gabarito oficial; o CSV de rótulos está em `audit-rules/calibracao/benchmark/`). Roda no CI (job `benchmark`), com linha de base e gate
de regressão (−0,02). Casos 1–1370 = treino, 1371–2740 = validação.

**Resultado, regras Java de segurança** (média das 11 categorias):

| | Antes | Depois |
|---|---:|---:|
| Treino | 0,00 | 0,48 |
| Validação | 0,00 | 0,42 |
| Todos | 0,00 | 0,44 |

Antes, o Sentinel não detectava criptografia fraca, hash fraco, número aleatório fraco, cookie sem `Secure`, path traversal nem trust boundary
(0 achados nesses 6 casos de categoria) e a regra de XSS marcava quase todo arquivo. Mudanças em `core-security` (1.1.0):
fontes de taint para servlet/Spring (`getParameter`, cabeçalhos, cookies, parâmetros de método), sanitizadores de encoder, regras de criptografia
e hash que aceitam o provedor e variações de caixa (`DESede`, `AES` sem modo, `SHA1`), cookie com `setSecure(false)`, `java.util.Random` em código de servidor,
path traversal, trust boundary e command injection por requisição. As regras de injeção de SQL, LDAP e XPath deixam de tratar a
concatenação de constantes como origem.

**Limites assumidos:** parte dos casos "seguros" do Benchmark esconde o valor constante atrás de código morto (ex.: um ternário que sempre escolhe
a constante) e a análise de taint do Semgrep não avalia isso; por isso o FPR de path traversal, trust boundary e XSS ainda fica em 35–60%.
O treino influenciou as regras; a validação ficou 0,06 abaixo, sinal de pouco sobreajuste, mas existe. O Benchmark mede só Java.
Os números locais usam o Semgrep 1.172.0 dos pacotes; o CI confirma.

**Ruído cortado:** `regexp/no-super-linear-move` e `security/detect-non-literal-fs-filename` desligadas (0/6 verdadeiros positivos cada).
`FunctionScanner`: classe `extends Base(...)` não é mais lida como função; objeto literal e tipo anônimo não contam como aninhamento (testes novos).

**Descoberta:** `descobrir-repos.sh` passa a buscar aplicações (tamanho mínimo, sem starter/boilerplate/demo), com calendário 2:1:1:1 entre
Java/Spring, Angular, Docker/K8s e React. **Agentes:** prompts atualizados (ruído antes de regra nova, benchmark como gate, revisão humana por amostra em
`docs/evolucao/revisao-humana.md`, Evolution só em item de produto com diff). **Placar:** critérios dos eixos 1, 2 e 10 reescritos com medida objetiva; notas mantidas em 2.

## Dashboard 12.3.0 — paridade com o protótipo de telas

Revisão tela a tela do protótipo (8 telas) e implementação do que faltava: navegação superior, ícones de status e de carregamento animados
(respeitam `prefers-reduced-motion`), tela de execução (faixa de fases, cartões por analisador, resultado parcial, eventos, decorrido e estimativa),
tela de resultado (veredito com destaque, ganho por correção, categorias), visão geral, sistemas (lista e painel lateral de edição), detalhe do sistema,
achados (várias severidades, filtros ativos, resumo por sistema, origem), ranking (meta, ordenação por nota, críticos e tendência) e relatórios em 3 passos.
API: `commitSha`, linhas e arquivos lidos, severidades parciais, descrição dos analisadores, novos/resolvidos/variação por auditoria,
`GET /api/v1/reports/portfolio` (todos os sistemas, em HTML, PDF e CSV). Histórico de relatórios segue no navegador.

## Dashboard 12.4.0 — configurações no cadastro de cada sistema

Saíram do `.env` e foram para a tela Sistemas: token Git e hosts do token (cifrado no banco com `SECRETS_KEY`, ou `API_KEY` se vazia; nunca volta pela API),
profundidade do histórico de commits, segredo do webhook de push (por sistema; o webhook só aceita assinatura de um sistema dono do repositório)
e as notificações (URL do Teams/Slack cifrada, formato, queda mínima de nota, severidade mínima, endereço do dashboard; só https público).
`GIT_ALLOWED_HOSTS` deixou de existir: o host de cada repositório é o do endereço cadastrado.
O token que estava em `.env.example` foi removido; **ele continua no histórico do Git e precisa ser revogado**.
