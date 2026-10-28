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

## Dashboard 12.5.0 — Fase 1 do plano Sentinel + MCP: menu e Informações de Auditoria

Menu na nova ordem e com os nomes combinados: Visão geral, Sistemas, **Informações de Auditoria** (antes Findings; a rota `/findings` não mudou, então links e favoritos continuam valendo),
**Ranking de Sistemas** e Relatórios. A tela de achados ganhou o título novo e a barra de abas: "Achados e nota" funciona; "Sugestões do Claude" e "Reauditoria"
aparecem desativadas e entram nas fases 4 e 5. Cada achado mostra de qual analisador veio (Semgrep, SonarQube, Checkstyle, jscpd…), a partir do `toolSource` que a API já devolvia.
Sem mudança na API, no motor de regras nem na nota. Os filtros continuam como eram; filtrar por origem exigiria um parâmetro novo na API e fica para depois.

## Dashboard 12.6.0 — Fase 2 do plano Sentinel + MCP: servidor MCP só de leitura

O Sentinel passou a ser um servidor MCP (`POST /mcp/<sistema>`, JSON-RPC 2.0 sobre HTTP) para o Claude Code consultar o resultado de **um sistema** com a chave **desse** sistema.
Quatro ferramentas, todas de leitura: `status_auditoria`, `buscar_achados`, `diff_auditoria` e `o_que_corrigir_primeiro`, em cima dos mesmos serviços do dashboard (a nota e o ganho de cada correção continuam vindo do `ScoringService`/`FixPlanner`).
Portão por sistema: chave aleatória guardada só como hash (SHA-256), acesso desligado por padrão, ferramentas escolhidas uma a uma, registro de cada chamada (sem o resultado) e três níveis de dados — só números e títulos (padrão), arquivo e linha, sensível (só contagens por regra).
O trecho de código, a descrição e a recomendação de IA não saem em nenhum nível.
Dashboard: aba **Claude (MCP)** no Editar do sistema (ligar, gerar/revogar chave, nível de dados, ferramentas, últimas chamadas). nginx encaminha `/mcp/` sem a API key global. Guia em `docs/integracao/mcp.md`.
Sem MCP no ar a nota não muda: nada nesta fase escreve em auditoria, achado ou regra. O protocolo foi escrito direto (sem extensão do Quarkus) para não depender de uma biblioteca de terceiros ainda não validada na 3.15.
Fora desta fase: ferramentas que escrevem, limite de chamadas e relatório pelo MCP.

## Dashboard 12.7.0 — Fase 3 do plano Sentinel + MCP: motor ajustável por sistema

Cada sistema passou a ter ajustes próprios **por cima do perfil**: pacotes de regras ligados/desligados, limites de clean code, regra a regra (severidade, desligar, "só observar" que não pesa na nota) e quais analisadores rodam. Só o que difere do perfil é guardado (`system_engine_config`, JSON); sem ajustes o sistema audita exatamente como antes.
Motor: `SystemTuning` + `AuditPolicy.withTuning` montam a política do sistema na hora da auditoria (`AuditOrchestrationService.resolvePolicy`). Com ajuste vazio a política devolvida é a mesma instância do perfil, e `SystemTuningTest` compara pacotes, sobrescritas, limites e severidades de todo perfil carregado. Analisador desligado sai antes da escolha por substituição e aparece como `DESLIGADO` ("não verificado" no resumo).
API: `GET/PUT /api/v1/engine-config/{id}` (caminho próprio, como o do MCP) devolve o efetivo, o que vem do perfil, as regras vistas na última auditoria e o número de ajustes; o PUT valida regra, severidade, limite, pacote e analisador. Apagar o sistema apaga os ajustes.
Dashboard: abas **Motor de regras** e **Analisadores** no cadastro, com "N ajustes em relação ao perfil" e "Voltar ao perfil". O cadastro/edição de sistema agora abre em **pop-up** (Esc e clique fora fecham) em vez do painel lateral.
Decisões: a nota continua sendo calculada só pelo motor; o Claude não ajusta nada aqui. Criar regra local do sistema ficou para a fase 4 (junto do funil de promoção). Entre ajuste do sistema e do perfil, a mesma chave do sistema vence; chave exata continua valendo mais que curinga.
Verificado: `vitest` (119), `tsc`, compilação e execução isolada de `SystemTuning`/`TuningJson`. Não verificado aqui: Quarkus/Maven (testes de política com pacotes reais e a tabela nova rodam só no CI).

## Dashboard 12.8.0 — Fase 4 do plano Sentinel + MCP: Revisão do Claude e funil de regras

O Claude passou a poder deixar **sugestões** e **propor regras** para um sistema, sempre fora da nota.
MCP: `registrar_sugestao` e `propor_regra` (as primeiras ferramentas que gravam), **desligadas por padrão** e só liberadas por escolha explícita na aba Claude (MCP). Gravam apenas em tabelas próprias (`review_suggestion`, `rule_proposal`); não criam achado nem mexem na nota.
Funil (`RuleFunnel`, testado sem banco): proposta → revisão por uma pessoa → teste de amostra → observação → promovida/descartada. Promover exige uma auditoria depois do início, 5+ achados e 90%+ de precisão (ajustável por configuração). A precisão sai dos achados da regra e das supressões como falso positivo. Uma regra só vai ao pacote compartilhado depois de promovida numa cópia em outro sistema.
Motor: regras em observação ou promovidas do sistema são gravadas numa pasta temporária e entram no Semgrep; as em observação ficam em modo sombra por ajuste da política (vale mesmo que a regra declare `status: stable`). `RuleDraft` recusa YAML fora do formato, `fix`, mais de uma regra e regex com repetição aninhada.
Dashboard: aba **Revisão do Claude** em Informações de Auditoria (sugestões e regras, com os botões de cada etapa e a evidência da observação).
Limites assumidos: o teste de amostra é registrado por uma pessoa (o Sentinel não roda o Semgrep sobre a amostra); a promoção ao pacote compartilhado é manual no repositório dos pacotes.
Verificado: `vitest` (126), `tsc`, funil e validação de regra executados isoladamente (11 casos). Não verificado aqui: Quarkus/Maven (entidades, serviço, API, integração com o Semgrep rodam só no CI).
## Dashboard 12.7.1 — mensagem de erro da API chega na tela

Os erros 4xx da API (como "Para guardar o token, defina SECRETS_KEY") saíam com corpo vazio, e o cadastro só mostrava "HTTP 400: Bad Request". `ApiErrorMapper` devolve a mensagem em JSON (só 4xx, nunca 5xx) e o cliente do dashboard também aceita corpo em texto curto. Verificado: `vitest` e `tsc`; o mapeador roda só no CI.

Também na 12.7.1: o token Git digitado na tela não exige mais `SECRETS_KEY` no `.env`. Sem `SECRETS_KEY` nem `API_KEY`, a API cria uma chave aleatória (32 bytes) em `/var/lib/sentinel/secrets.key`, num volume próprio (`audit_secrets`), e a reutiliza. A chave fica fora do banco, então um dump do banco sozinho não abre os tokens; perder o volume exige digitar os tokens de novo. No Helm continua valendo o Secret (`SECRETS_KEY`). Testes novos em `SecretBoxTest` rodam só no CI.

## Dashboard 12.9.0 — Fase 5 do plano Sentinel + MCP: Reauditoria e pedidos com aprovação

Nova aba Reauditoria em Informações de Auditoria: pedidos do Claude esperando aprovação (Aprovar/Recusar), botão Reauditar agora e comparação antes e depois da última auditoria. No MCP entram `iniciar_auditoria` e `suprimir_achado` (escrita, desligadas por padrão) e `status_pedidos` (leitura). O Claude só cria o pedido; a execução acontece quando uma pessoa aprova. Limites: pedido vale 1 hora, 5 pendentes por sistema, supressão só como falso positivo e por 90 dias. Pedidos são apagados junto com o sistema. Testes: `PendingActionRulesTest`, `McpGateServiceTest` (reescrito para 4 ferramentas de escrita) e `ReauditPanel.test.tsx`; o código Quarkus só compila no CI.

## Dashboard 12.10.0 — Fase 6 do plano Sentinel + MCP: limites e medição

Limite de 60 chamadas por minuto por sistema no MCP e de 10 tentativas com chave errada por minuto (429 com `Retry-After`). Novo endpoint e quadro "Quanto o Claude acerta" (sugestões aproveitadas e regras promovidas, segundo as pessoas). Testes novos: `McpRateLimiterTest`, `ReviewMetricsTest` e o quadro no `ClaudeReviewPanel.test.tsx`. Com isso o plano Sentinel + MCP fecha as seis fases; o código Quarkus novo só compila no CI.

## Limpeza dos documentos — arquitetura nova

Todos os `.md` e `.html` passaram a descrever o Sentinel com o MCP: `ARQUITETURA.md` ganhou a seção 3.11 (pacotes `tuning`, `mcp`, `review` e `actions`), as páginas e os painéis novos do dashboard, as rotas novas e a seção de decisões; `docs/integracao/mcp.md` foi reescrito como um guia só (antes era um texto por fase); o README ganhou a estrutura nova, os endpoints e o resumo das seis fases. Juntados: `docs/arquitetura-sentinel.html` (versão antiga e menor) saiu, e o que tinha de único (decisões que moldam o desenho) entrou na seção 10 da arquitetura; `docs/evolucao/relatorios/README.md` virou um parágrafo em `AGENTE.md`; `docs/integracao/README.md` virou o índice da pasta. `ARQUITETURA.html` agora é gerado do `.md` por `scripts/gerar-arquitetura-html.py`, para os dois não divergirem de novo. Mantidos de propósito: `revisao-humana.md` (as pessoas preenchem; os agentes leem e escrevem nele) e `evidencias/LEIA-ME.md` (marca a pasta no git).

## Dashboard 12.11.0 — Claude (MCP) visível durante a auditoria

Na tela de execução da auditoria apareceu a faixa **Claude (MCP)** (desligado, ligado sem consultas ou ligado com as consultas feitas desde o início) e as chamadas do Claude entram no quadro Eventos. Pedidos pendentes do Claude viram link para a Reauditoria do sistema. Links diretos novos: `/systems?edit=ID&tab=mcp` (abre a aba Claude (MCP)) e `/findings?aba=reauditoria&systemId=ID`.
Só leitura: usa `GET /api/v1/mcp-access/{id}`, `/calls` e `/api/v1/pending-actions/{id}`, que já existiam. Nada muda no motor, na nota nem na API. Testes novos: `McpStatusStrip.test.tsx`, `mcpActivity.test.ts` e casos em `AuditRunPage`, `SystemsPage` e `FindingsPage`.

## Dashboard 12.12.0 — Aplicação da avaliação do fluxo

Segue a avaliação do fluxo de uso (relatório "Avaliação do fluxo do Sentinel"). O que mudou:
- **Ajuste de regras**: o painel do motor lista o catálogo das regras dos pacotes (id, texto, severidade, pacote) com busca, filtro por pacote e por "vistas/ajustadas", seleção em lote (só observar, desligar, severidade, limpar) e ganho estimado na nota a partir do plano de correção. Novo `catalog` em `GET /api/v1/engine-config/{id}` (campo novo, os antigos não mudam), lido de `audit-rules` por `RuleCatalog`.
- **Alterações não salvas**: o pop-up do sistema marca as abas alteradas e pede confirmação ao fechar (X, Cancelar, Esc ou clique fora). As abas do motor ficam montadas para não perder o que foi editado.
- **Uma régua de nota**: `scoreBand.ts` concentra as faixas (75 meta, 50 alerta) usadas por resultado, ranking, execução e atenção.
- **Um caminho para iniciar auditoria**: a Visão geral usa o mesmo pop-up da tela do sistema (com a branch editável). O dashboard não manda mais `dashboard-user`; com login a API grava quem está autenticado (`AuditResource.actor`).
- **Pedidos do Claude visíveis**: contagem na Visão geral (com link por sistema) e selo no menu "Informações de Auditoria".
- **Trilha da jornada** também no sistema, na execução e nos achados da auditoria; abas mortas (`ativa`) removidas.
- **Teste de ponta a ponta**: `npm run e2e` (Playwright, API simulada por rota) cobre "iniciar → acompanhar" e "ajustar regra → fechar sem salvar → salvar". Não está no CI: o workflow fica a cargo de quem mantém `.github/`.

Não feito de propósito: ferramenta MCP que cruza sistemas (pede decisão sobre a chave por sistema), limite de MCP compartilhado entre instâncias e registro dos parâmetros das chamadas MCP (código Quarkus que não dá para compilar aqui). Selo do Claude por achado e a divisão do `SystemsPage` ficaram para a próxima volta.
Verificado: `tsc`, `vitest` (182) e os 2 testes e2e em Chromium. Não verificado: o código Java novo (`RuleCatalog`, `EngineConfigResource`, `AuditResource`) só compila no CI.

## Dashboard 12.13.0 — Segunda parte da avaliação do fluxo

- **MCP registra os parâmetros** de cada chamada (`McpCallSummary`: campo e valor curtos, segredos mascarados) na coluna `detail` que já existia, sem migração. A aba Claude (MCP) ganhou a coluna Parâmetros. Decisão do João: o MCP continua enxergando **um sistema só**, então não há ferramenta que cruze sistemas.
- **Selo "Claude sugeriu"** na lista de achados quando há sugestão aberta do Claude para a mesma regra (e, se a sugestão informa, o mesmo arquivo e linha). Usa a mesma consulta da aba Revisão do Claude.
- **`SystemsPage` dividida**: de 686 para cerca de 380 linhas, com `SystemFormFields` (aba de cadastro) e `SystemsTable` (lista). Sem mudança de comportamento; os testes e o e2e passam.
- **Cores**: novo token `--s-primary`; botões primários, destaques e textos de erro mais usados passaram a usar tokens (cores de status em gráficos e barras seguem em hexadecimal).

Não feito: limite de chamadas MCP compartilhado entre instâncias (hoje é em memória por instância; compartilhar exigiria tabela ou cache externo, e só vale com mais de uma instância da API). Botões e selects crus restantes seguem como estão.
Verificado: `tsc`, `vitest` (187), e2e (2) em Chromium. Não verificado: o Java novo (`McpCallSummary` e seu teste) só compila no CI.

## Correção — banco antigo rejeitava achados de PERFORMANCE

Auditoria falhava ao gravar achados da categoria PERFORMANCE com `finding_category_check` violada: bancos criados antes dessa categoria têm uma restrição CHECK que o modo `update` do Hibernate não atualiza. `SchemaFixups` passa a remover `finding_category_check` na subida (idempotente, como já fazia com `audit_run_status_check`; falha vira aviso e não impede a API de subir). Quem não quiser esperar a nova versão pode rodar `ALTER TABLE finding DROP CONSTRAINT finding_category_check;`.
Não verificado: o ajuste roda contra Postgres e só compila no CI; não foi executado num banco antigo aqui.

## Dashboard 12.14.0 — Títulos das telas na mesma altura

Todas as telas passam a usar `PageShell`: mesma margem, mesma largura e uma faixa fixa (34 px) acima do título. A faixa leva a trilha da jornada e/ou o caminho (breadcrumb) e fica vazia onde não há nenhum dos dois, só para manter a altura. Na Visão geral, o título já ficava abaixo da trilha da jornada; as outras telas o colocavam mais alto (56 a 130 px) e agora ficam todas a 114 px, medido no navegador a 1440 px de largura. Nas telas de detalhe (sistema e auditoria) o caminho foi para a direita da trilha, para o título não descer.
Teste: `e2e/titulos-alinhados.spec.ts` abre as 9 telas e exige a mesma altura; sem a mudança ele falha (56, 64, 110, 128 e 130 px).
Verificado: `tsc`, `vitest` (187) e e2e em Chromium. Medido só a 1440 px; em telas estreitas a faixa pode quebrar em duas linhas (o caminho vai para baixo da trilha).

## Triagem da auditoria #33

Conferi no código os achados mais graves da auditoria do próprio Sentinel e registrei o veredito de cada grupo em `docs/evolucao/triagem-auditoria-33.md` (falso positivo, decisão ou real). Corrigido: dois `catch` vazios no `RuleCatalog`, comparações `!= null`, aspas sem escape e ramo duplicado no dashboard. Não verificado: o Java alterado (`RuleCatalog`) só compila no CI.

## Dashboard 12.15.0 — Caminhos ignorados, consultas em lote, Helm e nginx

- **Caminhos ignorados por sistema** (`ignorePaths` no ajuste do motor; chave omitida quando vazia, então o JSON antigo não muda). `AuditPolicy.apply` descarta os achados cujo arquivo casa com algum padrão (`PathGlobs`). Validação: até 50 padrões, 200 caracteres, sem `..` nem caracteres de controle. Tela: Analisadores → "Caminhos ignorados".
- **N+1**: `countBySeverityForRuns`, `findByAuditRunIds`, `findLatestCompletedBySystem` e `findLastStarted` substituem laços de consultas em `FindingLifecycleService.trend`, `FindingResource.overview`, portfólio, `RuleQualityService` e `AuditScheduler`. `statement-batch-size=50`. Não mudei o `allocationSize` da sequência de `Finding` (divergiria da sequência já criada no banco). `SystemResource.toDto` fica como está.
- **Helm**: imagens com tag `12.15.0`, sem `:latest`. **Dashboard**: imagem `nginxinc/nginx-unprivileged`, porta 8080 no container; Service do Helm continua na 80. Quem usa compose precisa reconstruir: `docker compose up -d --build audit-api audit-dashboard`.
Não verificado: o Java novo só compila no CI; Docker e Helm só são exercitados pelo CI.

## Dashboard 12.16.0 — Menu na lateral esquerda, sem trilha de passos

O menu principal saiu do cabeçalho e virou barra fixa à esquerda (`SideNav` do Carbon, sempre aberta); o cabeçalho ficou só com o nome, a versão e o tema. A trilha "Escolher sistema › Iniciar auditoria › Acompanhar › Entender o resultado › Corrigir" foi removida de todas as telas (componente `JourneySteps` apagado). A faixa fixa acima do título continua, só com o caminho (breadcrumb), para manter os títulos na mesma altura.
Verificado: `tsc`, `vitest` (189), e2e (3) e captura de tela a 1440 px. Não verificado: telas estreitas (a barra lateral fica fixa em 16 rem).

## Dashboard 12.17.0 — Título compacto, caminho em toda tela e cantos arredondados

- **Caminho (breadcrumb) em todas as telas**, sempre começando em "Início" (`PageShell` agora exige `crumbs`; `Crumbs` e a faixa vazia saíram). Informações de Auditoria aberta a partir de uma auditoria mostra Início / Sistemas / sistema / Auditoria #n / Achados.
- **Espaçamento**: 24 px do cabeçalho ao caminho, 8 px até o título, 24 px do título ao conteúdo. Título em 26 px. O teste `titulos-alinhados` agora também exige o caminho visível, o link Início e o caminho a 72 px do topo em todas as 9 telas.
- **Cantos de 8 px** em cards, painéis, campos, botões e janelas, por CSS global (`--s-radius` em `theme.css`), já que os cards usam estilo inline: o raio vale para qualquer elemento com `border: 1px solid` nos tons do tema, mais campos e botões do Carbon. Um `borderRadius` inline continua valendo (círculos, chips).
Verificado: `tsc`, `vitest` (189), e2e (3) e captura das 9 telas a 1440 px. Não verificado: telas estreitas e o tema escuro com os novos cantos.

## Dashboard 12.18.0 — Logo, recuo menor e divisória do menu no tema escuro

- **Logo** (escudo azul com um olho, `SentinelLogo` em `AppShell.tsx`) antes de "Sentinel Audit Tool" no cabeçalho.
- **Recuo entre o menu e o conteúdo**: de 64 px para 32 px. Eram dois recuos somados (o do `Content` do Carbon e o da tela); o do `Content` foi zerado e o `PageShell` passou a dar o espaço de cima (72 px do topo da página = 24 px abaixo do cabeçalho de 48 px).
- **Tema escuro**: linha de 1 px (#525252) entre o menu e o conteúdo; antes os dois tinham a mesma cor.
Verificado: `tsc`, `vitest` (189), e2e (3) e capturas das 9 telas nos temas claro e escuro a 1440 px. Não verificado: telas estreitas. No tema escuro, a notificação do Carbon e as caixas de "Resultado parcial" seguem claras (já era assim).

## Dashboard 12.19.0 — Paletas para cansar menos a vista

Caixa **Paleta de cores** no cabeçalho (lembra a escolha por navegador) com três opções, valendo para o tema claro e o escuro:
- **Clássico**: as cores de antes (contraste máximo).
- **Suave** (novo padrão): sem branco nem preto puros, texto de corpo a 12–14:1 em vez de 16–18:1, cores de status dessaturadas no escuro, menu e cabeçalho em tons do mesmo matiz (claro azul-acinzentado; escuro azulado).
- **Alternativo**: claro em tom de papel (quente) e escuro em cinza neutro.
As variáveis `--s-*` e os tokens do Carbon (campos, abas, tabelas, modais) seguem a paleta (`styles/paletas.css`). Contraste calculado: texto/card ≥ 11,6:1, texto secundário/card ≥ 6,4:1, link/card ≥ 5,6:1 (todas acima do mínimo AA de 4,5:1).
Verificado: `tsc`, `vitest`, e2e e capturas de Visão geral, Sistemas e Informações nas 6 combinações. Não verificado: as demais telas em cada paleta (algumas cores fixas, como as caixas de "Resultado parcial" e o aviso do Carbon, não acompanham a paleta) e telas estreitas.

## Dashboard 12.19.1 — Conteúdo colado ao menu em qualquer tela

Em telas maiores que 1696 px o conteúdo ficava centralizado (largura máxima de 1440 px com margem automática) e se afastava do menu. `PageShell` passa a alinhar à esquerda: o conteúdo começa sempre 32 px depois do menu (288 px da borda da tela), em qualquer largura. A largura máxima continua em 1440 px; em telas muito largas sobra espaço vazio à direita, não entre o menu e o conteúdo.
Teste novo `e2e/conteudo-perto-do-menu.spec.ts`: o título começa em 288 px com a tela em 1100, 1440, 1920 e 2560 px.
Verificado: `tsc`, `vitest`, e2e e captura a 2560 px.

## Dashboard 12.19.2 — CSS do conteúdo definido pelo João

`PageShell` passa a usar exatamente `padding: 70px 40px; margin: 0; gap: 24px`, sem largura máxima (o conteúdo ocupa toda a largura disponível). O caminho fica a 70 px do topo da página (22 px abaixo do cabeçalho) e o conteúdo começa a 296 px da borda (40 px depois do menu). Testes e2e ajustados para 70 e 296 px.

## Dashboard 12.20.0 / API — Achados mais assertivos (confiança)

Cada achado passa a ter **confiança** (alta, média, baixa) e o motivo: baixa em testes, documentação, exemplos e código gerado, em segredo com valor de exemplo (`changeme`, `your_`, `xxxx`…) e em regra que o time marcou como falso positivo em 40% ou mais dos casos (mínimo de 5); alta quando há CVE ou quando outra ferramenta confirma o mesmo ponto (mesma categoria, até 2 linhas). A tela de achados esconde os de baixa confiança por padrão, com a contagem e uma caixa para mostrá-los; ao suprimir, dá para suprimir junto os parecidos (mesma regra) da lista. O MCP devolve `confianca` e filtra por ela. Relatórios HTML e PDF mostram o commit auditado. "Só novos" já existia como situação "Novos desde a última auditoria".
O peso da confiança na nota é opcional e vem **desligado**: `sentinel.audit.score.confidence-weighting.enabled=true` faz achados de baixa confiança pesarem 40% (crítico nunca é reduzido).
Verificado: `tsc`, `vitest`. Java (confiança, `PathRole`, nota) só no CI. Não implementado: aviso de auditoria desatualizada (commit auditado vs. ponta da branch) e regras que reconhecem filtro global de autorização.

## Dashboard 12.21.0 / API — Aviso de auditoria desatualizada

Na tela de achados de uma auditoria, o Sentinel compara o commit auditado com a ponta atual da branch no remoto (`git ls-remote`, sem clonar, com o token do sistema) pelo novo `GET /api/v1/audits/{id}/freshness`. Se a branch andou, aparece o aviso "Auditoria desatualizada" com os dois commits. Se o remoto não responde ou a auditoria é antiga (sem commit), nada aparece.
Verificado: `tsc`. Java (endpoint e `parseRefSha`) só no CI. Não verificado contra um remoto real; sem teste de tela para o aviso.

## Dashboard 12.22.0 / API — Relatório com confiança

Os relatórios HTML e PDF passam a separar os achados de **baixa confiança** em uma seção própria ao fim ("Baixa confiança (N)"), com o motivo, em vez de misturá-los aos demais; o título "Findings (N)" conta só os de confiança normal e o resumo continua com o total. O `PathRole` trata arquivos `*.example`, `*.sample` e `*.template` (ex.: `.env.example`) como exemplo. O teste de confiança deixa de ter uma chave literal que o Gitleaks apontava como segredo (auditoria #34).
Verificado: `tsc` não se aplica a esta mudança (só Java e docs). Java só no CI. Não feito: reconhecer `remediation.yml` como exemplo (específico do pacote de regras), deduplicar achados entre ferramentas, regra de controle de acesso ciente do filtro global, nota que não sature em 0.

## Dashboard 12.23.0 / API — Insights sem ruído e duplicata PMD/Semgrep

Os insights (áreas, dívida priorizada, parecer) e o resumo do relatório passam a ignorar achados de baixa confiança: prioridade não é mais puxada por chave de exemplo em teste. A nota e o plano de correção continuam com todos os achados (a nota só muda se `sentinel.audit.score.confidence-weighting.enabled` for ligado).
Novo tópico de deduplicação no pacote `core-quality`: `pmd-ReturnEmptyCollectionRatherThanNull` + `sentinel-java-retorna-null-colecao` (até 10 linhas, porque o Semgrep marca o método e o PMD marca o `return null`) viram um achado só, com a maior severidade. Outras regras repetidas entre ferramentas (God Object, complexidade) não foram mapeadas por falta de ids equivalentes confirmados.
Verificado: nada além de revisão (Java e regras só no CI).

## Dashboard 12.23.1 — Tela de achados dividida em componentes

A auditoria #34 apontava `FindingsPage` com complexidade 103 e 323 linhas. Os filtros e o rodapé de paginação saíram para `FindingsFilters.tsx` (`FindingsFilters`, `PaginationFooter`); a página perde cerca de 50 linhas e a função principal deixa de conter o JSX dos filtros. Comportamento igual.
Verificado: `tsc`, `vitest` da tela, e2e da tela. Não verificado: o novo valor de complexidade (só medido numa próxima auditoria).
Nota sobre `Versions.parse`, `Yamls.map` e `Yamls.list`: o PMD e o Semgrep pedem coleção vazia no lugar de `null`, mas nesses métodos `null` significa "valor inválido / não é um mapa", distinto de vazio. Não foram alterados; o correto é suprimir como falso positivo.

## Dashboard 12.24.0 / API — Quatro analisadores novos e autorização global

Quatro analisadores próprios, sem executar nada do projeto auditado (só leem arquivos e o `git log`):
- **Ciclos de dependência** (`sentinel-arq-ciclo-pacotes`, `sentinel-arq-ciclo-arquivos`): pacotes Java e arquivos TypeScript/JavaScript que se importam em círculo (componentes fortemente conexas, Tarjan sem recursão). `import type` não conta. Ciclo com 8 nós ou mais sobe para alta.
- **Duplicação** (`sentinel-dup-bloco`): blocos de 15 linhas significativas iguais ou mais, só em produção. Ignora comentários (cabeçalho de licença), imports, getters e setters, e trechos repetitivos; é o que faltava ao `jscpd` opcional, que acusava licença, `pom.xml` e HTML no relatório de 22/10. Limites: `dup-window`, `dup-min-lines`, `max-findings`.
- **Pontos quentes** (`sentinel-hotspot`): arquivos com 5 mudanças ou mais nos últimos commits e função de complexidade acima de 15, ordenados por mudanças × complexidade. Exige 20 commits no clone (o padrão é 100); sem histórico não aponta nada. Limites: `hotspot-min-commits`, `hotspot-min-churn`, `hotspot-min-complexity`, `hotspot-max`.
- **Código morto** (`sentinel-morto-export`, baixa; `sentinel-morto-export-interno`, informativa): exportações TypeScript/JavaScript que nenhum arquivo menciona (testes contam como uso). Se o item é usado no próprio arquivo, basta tirar o `export`. Bibliotecas publicadas (`package.json` com entrada e sem `"private": true`) são ignoradas.
Autorização global: o Sentinel detecta filtro JAX-RS que rejeita (401/403), Spring Security com `anyRequest().authenticated()` e a política HTTP do Quarkus. Com isso, a regra "endpoint sem anotação de autorização" (`sentinel-owasp-a01-endpoint-sem-autorizacao`) passa a ter confiança baixa, com o motivo; ela continua na lista de baixa confiança para conferir se há rota pública.
Rodando a lógica contra o próprio repositório do Sentinel: 1 ciclo de 6 pacotes Java, nenhum ciclo no dashboard, nenhuma duplicação (após ignorar getters e setters), 31 exports sem uso fora do arquivo (2 totalmente mortos: `useUpdateProfile`, `useUpdateSchedule`) e `ApiKeyFilter` reconhecido como barreira global.
Verificado: a lógica (classes em `analyzer/structure`) compilada e testada com `javac` no repositório; `tsc` não se aplica. Os analisadores, a ligação com a orquestração e os testes JUnit só no CI. Não verificado: desempenho em repositórios grandes (limites: 20 mil arquivos, 1 MB por arquivo, 1,5 milhão de janelas de duplicação); o ArchUnit ficou de fora por decisão.

## Dashboard 12.24.1 — Command Injection sem falso positivo de lista de argumentos

Na auditoria #34 havia 1 alto e 9 médios de "Possível Command Injection" que eram `ProcessBuilder` com lista de argumentos (sem shell). Duas correções em `sentinel-injection.yml`:
- `sentinel-command-injection` (alto): o sorvedouro passa a ser só o executável (1º argumento, `focus-metavariable`). Concatenar um argumento da lista (`"refs/heads/" + branch`) deixa de contar; `new ProcessBuilder("sh -c cat " + arquivo)` continua apontado.
- `sentinel-command-injection-variable` (médio): `new ProcessBuilder(List<String>)`, `new ProcessBuilder(String[])` e `exec(String[])` ficam de fora; texto único em variável continua apontado.
Testes novos no pacote (`comandoComListaDeArgumentos`). Rodando só as regras de injeção contra `audit-api/src/main/java` do próprio Sentinel: 10 achados de command injection antes, 0 depois; `semgrep --test` passa (6/6).
Verificado: `semgrep --test` e a comparação acima. Não verificado: o recall no OWASP Benchmark (só o CI, que compara com a linha de base).

## Dashboard 12.25.0 — Curva suave opcional para a nota

Com muitos achados a nota de várias dimensões chega a 0 e deixa de distinguir "ruim" de "péssimo" (na auditoria #34 isso escondia melhoras). Nova opção `sentinel.audit.score.curve`:
- `linear` (padrão, nada muda): 100 menos a penalidade, piso em 0.
- `suave`: 100 × e^(−penalidade/100). Para penalidade pequena fica quase igual (10 pontos → 90,5 em vez de 90); para penalidade média fica mais branda (50 → 60,7); nunca chega a 0 (140 → 24,7; 250 → 8,2; 400 → 1,8).
Nada muda sem ligar a opção; ligá-la muda as notas de todos os sistemas e quebra a comparação com auditorias antigas, por isso é decisão de quem opera. Testes novos em `ScoringServiceTest`.
Verificado: os valores da curva (calculados à parte). Não verificado localmente: compilação e testes Java (só o CI compila a API).

## Dashboard 12.26.0 / API — Melhorias da auditoria #68 (nota 25,6)

Em resposta ao relatório da auditoria #68 (commit a3a74a5). Reproduzi localmente os achados que dá para reproduzir e tratei a causa, não o sintoma.
- **`.sentinel.yml` por projeto** (`ProjectConfig`, guia em `docs/integracao/sentinel-yml.md`): `exclude` e `suppress` com motivo obrigatório e validade opcional, aplicados antes da confiança e da nota. Suprimidos continuam visíveis. O Sentinel passa a usar o próprio arquivo (regras de teste, remediação e relatórios deixam de ser auditados como produto).
- **PMD menos ruidoso**: 7 regras de estilo saem do conjunto padrão (`LiteralsFirstInComparisons`, `UseVarargs`, `AvoidLiteralsInIfCondition`, `AvoidFieldNameMatchingMethodName`, `CognitiveComplexity`, `GodClass`, `UseObjectForClearerAPI`); eram cerca de 190 achados de estilo. Três tópicos novos no `dedup.yml` (`catch Throwable`, `System.exit`, chave de lista por índice), `core-quality` 1.0.3.
- **Arquitetura**: `SystemCatalog`, `RunCatalog` e `FindingCatalog` isolam o acesso a dados; 9 controllers deixam de importar repositório. Respostas 404 padronizadas.
- **Dashboard**: ESLint do Sentinel sobre o dashboard de 37 para 0 (regexes de validação sem backtracking, `eqeqeq` aceitando `== null`, acessibilidade de rótulos). Configuração do ESLint alinhada e lockfiles das regras criados para a instalação ser reprodutível.
- **Complexidade**: todas as funções com complexidade ciclomática acima de 20 (12 no dashboard, 11 na API, 2 no CLI) foram divididas; nenhuma passa de 20 na medição do próprio analisador. Comportamento igual: parsers puros (`Csv`, `SchemaParser`, `SourceSanitizer`, `PackLoader`, `ProfileLoader`, `TuningJson`, `ProjectProfiler`, `AuditCli.parse`) conferidos contra a versão anterior por teste diferencial (centenas de milhares de entradas aleatórias e pacotes reais, 0 diferenças). Ajustes do motor viram funções puras em `utils/engineTuning.ts`.
- `Locale.ROOT` em 18 pontos de formatação/comparação de texto.
Verificado: `tsc`, `vitest` (205), `vite build`, ESLint do Sentinel (0), teste diferencial acima; classes Java dos pacotes e do CLI compiladas com `javac` contra os jars disponíveis. Não verificado: a compilação e os testes JUnit completos da API (Maven não alcança o repositório de artefatos neste ambiente; só o CI compila a API inteira). O achado de segredo em `.env.example:15` do relatório não foi reproduzido localmente.


## Confiabilidade do motor de regras — 2026-10-28

PR único com seis frentes (feito a pedido da pessoa que mantém o projeto, fora dos agentes `evolucao/*`).

| Frente | O que mudou | Evidência |
|---|---|---|
| 1 Configuração sem ligação | `application.properties` volta a ligar `PACKS_*`, `TRIVY_*`, `GITLEAKS_*`, `PMD_*`, `ESLINT_*`, `CLEANCODE_*`, evidências, laboratório e OIDC (removidos por engano em `cefaf1e`) | `EnvBindingTest` confere que toda variável do compose e do Helm tem `${VAR:padrão}`; simulação em Python: 33/33 do compose e 15/15 do Helm |
| 2 Status honesto | `AnalyzerResult.partialNote` e status `PARCIAL` (timeout, saída anormal, erros do Semgrep) no orquestrador, andamento, relatório de insights e dashboard; Semgrep passa a declarar `isAvailable`; fim do stderr vira nota; Semgrep fixado em 1.172.0 no Dockerfile | `AnalyzerHonestStatusTest`; dashboard: `tsc` e vitest verdes |
| 3 Portão por categoria | `benchmark-owasp.py --tolerancia-categoria` (padrão 0,10) além da média (0,02) | rodado contra o baseline: passa |
| 4 Taint | propagação por `list.add`/`map.put`, `getHeaderNames()` como fonte e variáveis de ambiente de `Runtime.exec` em sqli, cmdi e pathtraver | Benchmark com Semgrep 1.172.0: média 0,444 → 0,461 (treino 0,486 → 0,507; validação 0,410 → 0,422); cmdi 0,053 → 0,209, pathtraver 0,129 → 0,161, sqli 0,244 → 0,248; FPR dessas categorias subiu |
| 5 Peso só com precisão medida | `eslint-regexp/no-super-linear-move`, `eslint-security/detect-non-literal-fs-filename` (0/6) e `jscpd-codigo-duplicado` (1/9) em sombra nos perfis; `scripts/precisao-regras.py --verifica` exige a evidência do `precisao.md` | script verde; `PackRegistryTest` cobre as sobrescritas |
| 6 Veredito humano | `scripts/concordancia-revisao.py` calcula a concordância quando a coluna "Veredito humano" for preenchida | hoje 0 de 20 preenchidas: sem número |

Não verificado: Maven, Quarkus e Docker não rodaram aqui (Maven Central inacessível). As mudanças em Java foram checadas só por `javac` sobre as fontes (nenhum erro novo além dos de dependência ausente) e por um teste de fumaça de `ExternalTools`; o CI decide. O veredito humano (frente 6) continua dependendo de uma pessoa.

## Dashboard 12.30.0 / API — Melhorias da auditoria #46 (nota 40,0)

Em resposta ao relatório da auditoria #46 (commit 365cfbb; a nota subira de 25,6 para 40,0 com o PR anterior). Duas causas pesavam mais que os achados em si:
- **Erro meu no `.sentinel.yml`:** `pmd-systemprintln` não casava com `pmd-SystemPrintln` porque `ProjectConfig.ruleMatches` diferenciava maiúsculas; os ~39 avisos do CLI continuavam pesando. A comparação passou a ignorar a caixa (teste novo) e entraram `pmd-DoNotTerminateVM` (CLI) e `sentinel-system-exit` (`PackTool`), com motivo.
- **Teto por regra faz Qualidade e Arquitetura saturarem em 0:** MEDIUM/LOW/INFO valem no máximo 20 pontos por regra, então corrigir parte das ocorrências não muda a nota e a penalidade bruta fica várias vezes acima de 100. A curva `suave` mostra o progresso; passou a ser o padrão (`sentinel.audit.score.curve=linear` volta ao antigo). **A primeira auditoria depois da atualização compara a nota nova com a linear anterior; o delta dessa auditoria não é confiável.**

O que mudou:
- **HIGH → 0:** 2 `RuntimeException` do CLI viraram `IllegalStateException`; `runAnalyzers` (complexidade 21) foi dividida em `candidateAnalyzers`, `skippedBeforeRun`, `skipReason`, `startAnalyzer` e `collectOutcomes`.
- **Ruído de configuração:** `color-function-alias-notation` não existe no Stylelint 16.12.0 fixado (5 avisos "Unknown rule") e saiu; `declaration-block-single-line-max-declarations` (preferência de estilo) desligada; `!important` que vence estilo inline do React ficou com `stylelint-disable` e motivo; o selo do menu deixou de precisar de `!important` (classe em vez de estilo inline). Dockerfile: `curl | gpg` sem pipe, `npm --prefix` no lugar de `cd`, `apt-get` sem versão fixa documentado com `hadolint ignore=DL3008`.
- **PMD:** `NullAssignment` (7), `EmptyCatchBlock` (8, parâmetro `ignored`) e `AssignmentInOperand` (9) zerados.
- **Deduplicação entre ferramentas:** `dedup.yml` ganhou `prefer: <Ferramenta>` (antes só o Semgrep podia ficar). A complexidade ciclomática do CleanCode absorve a do Checkstyle/PMD na mesma função (até 3 linhas de distância), com a maior severidade. Números mágicos **não** foram unidos: o Checkstyle acha literais pequenos que o analisador de arquitetura (só > 100) nunca acha.
- **Nota explicável:** `GET /api/v1/audits/{id}/score-explanation` e o painel "Por que esta nota?" na tela de resultado mostram, por dimensão, quanto cada regra tirou e onde o teto cortou. `?without=regra1,regra2` simula a nota sem elas (botão "E se…"); o plano de correção já estimava o ganho por regra, mas com a curva linear o ganho era 0 nas dimensões saturadas.
- **Catraca:** `--fail-on-score-drop <pontos>` no CLI reprova se a nota cair mais que isso desde a auditoria anterior. A "baseline" já existia como comparação com a auditoria anterior (`--fail-on-new`); não criei arquivo de baseline separado.
Verificado: `tsc`, `vitest` (218), `vite build`, ESLint do Sentinel nos arquivos novos, Stylelint com a configuração nova (0 erros), `javac` sobre API e CLI (mesmos erros de pacote ausente de antes, nenhum novo), `ScoringService` real com stubs (explicação igual ao cálculo em 20 mil casos aleatórios), `lcov` contra a versão anterior (0 diferenças em 200 mil entradas), CLI contra API simulada. Não verificado: Maven e testes Java (só o CI compila a API e roda os testes novos); Docker; o painel num navegador real.


## Dashboard 12.31.0 / API — Resto das pendências da auditoria #46

Continuação do PR anterior (12.30.0). Tudo abaixo é refatoração ou ajuste de configuração; nenhuma rota, formato de resposta ou regra de pontuação mudou.

O que mudou:
- **God Objects (tamanho):** `AuditOrchestrationService` 778 → 470 linhas (e 28 → 12 `@Inject`), `IngestionService` 628 → 461, `PackLoader` 683 → 515, `ArchitecturePolicyAnalyzer` 558 → 478, abaixo do limite de 500 do analisador de arquitetura. Extraídos, sem mudar a lógica: `AnalyzerSet` (lista dos analisadores), `AnalyzerRunner` (dispara e espera os analisadores), `AuditPolicyResolver` (perfil + ajustes + regras locais), `RunText` (cortes de texto e causa raiz do erro), `RemoteProbe` (verificação do repositório remoto, sem clonar), `ArchitectureYaml` (leitura do `architecture.yml`) e `ComponentPolicyChecks` (política de componentes).
- **God Objects (injeções):** `AuditResource` 13 → 7 `@Inject` e `SystemResource` 13 → 6, com `AuditReports`, `AuditStatusViews`, `SystemViews` e `SystemCleanup`. As rotas ficaram nas mesmas classes, então as URLs não mudam. **`AuditOrchestrationService` ainda tem 12 `@Inject`** (o limite da regra é 8): continua apontado; ver "Não feito".
- **N+1:** o agendador buscava a última auditoria de cada sistema numa consulta por sistema; agora é uma consulta só (`findLastStartedBySystem`). `InsightsService`, `FindingLifecycleService.trend` e a gravação em lote do orquestrador **não** tinham o problema (conferido na leitura: uma consulta, laço só monta objetos; lote de 50 via `statement-batch-size`); entraram no `.sentinel.yml` como falso positivo, com o motivo.
- **PMD:** `PreserveStackTrace` (todas as exceções relançadas agora levam a causa, 24 pontos), `AvoidReassigningLoopVariables` (8, laços reescritos com `while`/passo de 2) e `UnusedAssignment` (`commitSha` declarado onde nasce; construtor compacto de `SystemTuning` suprimido, com motivo, porque reatribuir o parâmetro é a forma de normalizar o campo de um record).
- **Números mágicos:** constantes nomeadas em `PgStatChecks` (limites de consulta lenta/tagarela, tamanho de bloco do PostgreSQL). Novo tópico de deduplicação `numeros-magicos` (`prefer: Checkstyle`): o achado "Magic numbers" do analisador de arquitetura é um resumo por arquivo, na primeira ocorrência > 100, que o Checkstyle já aponta na linha exata; passa a ficar só o do Checkstyle (o de arquitetura só sobra se o Checkstyle não rodou). O LOG anterior dizia o contrário ("não foram unidos"); estava com o raciocínio invertido: o conjunto da arquitetura é subconjunto do Checkstyle.
- **Correção de um deslize meu durante o trabalho:** criei uma classe com o nome `AnalyzerCatalog`, que já existia (descrições dos analisadores); percebi pelo `git status` (arquivo "modificado" em vez de novo), restaurei a original e renomeei a minha para `AnalyzerSet`.

Verificado: `javac` sobre API e CLI (só erros de pacote ausente, iguais aos de antes nos arquivos antigos; nada além de bibliotecas faltando nos novos); teste diferencial contra a versão anterior, com entradas aleatórias, de `Csv.parse`, `SchemaParser.balanced/splitTopLevel`, `FunctionScanner.countParams`, `PathGlobs.toRegex` e `ProjectConfig.compileGlob` (1,8 milhão de comparações, 0 diferenças) e de `AuditCli.parse` (200 mil, 0 diferenças).

Não feito / não verificado:
- **Números mágicos:** ~330 ocorrências do Checkstyle em 78 arquivos continuam; só `PgStatChecks` foi limpo. A nota não muda com isso (o teto por regra já está no máximo: 10 ocorrências bastam para chegar a 20 pontos), então não troquei números por nomes artificiais como `LIMITE_200`.
- **`AuditOrchestrationService` com 12 `@Inject`:** baixar de 8 pediria separar também a persistência (`persistResultsAndComplete`, `markFailed`, `storeContext`) em outra classe; os testes atuais montam o serviço atribuindo esses campos, então ficou para um PR próprio.
- Maven e os testes Java (só o CI compila e roda); a consulta HQL nova do agendador; as rotas de `AuditResource`/`SystemResource` em execução.

## Dashboard 12.32.0 / API — Persistência da auditoria separada do orquestrador

Fecha o que o PR anterior deixou como "não feito": `AuditOrchestrationService` tinha 12 `@Inject` (a regra aponta 8 ou mais). Refatoração sem mudança de comportamento.

- Novo `AuditRunStore` (6 `@Inject`) com tudo que lê ou grava a auditoria no banco: marcar como rodando (com a espera pelo commit do registro), cancelada e falha, gravar achados e resultado, guardar SBOM e contexto, carregar a base de comparação e a qualidade das regras, e avisar o fim. Os métodos transacionais foram movidos como estavam.
- `AuditOrchestrationService`: 12 → 7 `@Inject` e 470 → 278 linhas; fica só a ordem das etapas.
- Testes: `AuditOrchestrationServiceTest` passa a atribuir `service.store.<campo>` (e `service.store.tryMarkRunning`) no lugar dos campos antigos.

Verificado: `javac` sobre a API (só erros de pacote ausente; nenhum erro novo comparado à `main`; o orquestrador tem menos erros porque perdeu as dependências). Não verificado: Maven e testes (só o CI); os testes Java alterados não foram compilados aqui.
