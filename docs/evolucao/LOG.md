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

## Dashboard 12.33.0 / API — Números mágicos trocados por constantes nomeadas

Fecha o item "Números mágicos" deixado pelo PR da auditoria #46. Refatoração sem mudança de comportamento: cada valor continua o mesmo, só ganhou nome.

- 322 ocorrências do Checkstyle `MagicNumber` em 80 arquivos da API e do CLI viraram constantes com nome do que representam (`MAX_NOT_APPROVED`, `GCM_IV_BYTES`, `PHASE_SECURITY`, `CRITICAL_HIGH_COUNT`…). Onde o número é um padrão, o nome segue o padrão: coeficientes do CVSS 3.1, códigos de erro do JSON-RPC, constantes do FNV-1a, IV e tag do AES-GCM.
- Cortes de texto com reticências passam a usar `LIMITE - ELLIPSIS.length()` em vez de um `497` solto; o resultado é o mesmo.
- Respostas HTTP usam `Response.Status` quando o código existe; 422 e 429 viram constantes locais.
- A nota não muda (o teto por regra já estava no máximo); o ganho é de leitura e de manutenção.

Verificado: Checkstyle com a configuração do projeto sobre `audit-api` e `audit-cli`: 0 `MagicNumber` (antes 322); `javac` sem erro novo em relação à `main` (diferença só em `Response.Status`, que o `javac` local não resolve por faltar o jakarta) e o CLI compila sem erros.
Não verificado: Maven e testes (só o CI); não rodei comparação de saída antes/depois, só revisei cada troca.

## 2026-10-28 — falso positivo — `sentinel-command-injection-requisicao` (pacote core-security 1.1.3)

Classe: falso positivo (eixo 1/2, OWASP Benchmark `cmdi`: TPR 0,92, FPR 0,71, pontuação 0,21 na linha de base).
Causa: a regra de taint só tinha sanitizadores de shell (`encodeForOS`, `quote`…); um valor da requisição convertido para número ou booleano continuava marcado como contaminado.
O que mudou: `Integer.parseInt`, `Long.parseLong`, `Double.parseDouble`, `Boolean.parseBoolean` e `UUID.fromString` passam a ser sanitizadores; testes `cmdiNumeroConvertido` (ok) e `cmdiTextoSemConverter` (ruleid); `version` do pack 1.1.2 → 1.1.3.
Verificado: `semgrep --test` sobre `sentinel-injection.yml` e `sentinel-injection.java`: 7/7 passaram.
NÃO verificado: efeito no job `benchmark` (não li o código do Benchmark, como manda a regra; a queda do FPR só aparece no CI). O backlog 1/2 `cmdi` segue aberto e `baseline.json` não foi alterado; se o CI mostrar `cmdi` acima de 0,209, a linha de base deve subir em PR seguinte.

## Dashboard 12.34.0 / API — Tela de Configurações

Reúne na tela, como último item do menu, as variáveis que ficavam espalhadas em `.env.example`, `application.properties`, `docker-compose.yml` e nos README.

- API: pacote `settings` com o catálogo (54 configurações, 31 editáveis), a tabela `app_setting`, `GET/PUT /api/v1/settings`, `DELETE /api/v1/settings/{chave}` e `POST /api/v1/settings/restart`. Uma fonte de configuração (`DbConfigSource`, ordinal 450) entrega à API, na subida, o que foi salvo; o valor da tela vale por cima da variável de ambiente.
- Segurança: banco, chaves, CORS, login, caminhos do servidor e tudo que executa programas (`PACKS_ALLOW_TOOLS`, repositório de pacotes, `PMD_RULESET`, `SEMGREP_CONFIG`) são só leitura, na tela e na API; segredos saem mascarados. Teste fixa essa lista.
- Dashboard: página `/settings` com seções, busca, validação de faixa, "Voltar ao ambiente", aviso de reinício e botão de reiniciar com confirmação.
- Testes novos: catálogo (inclui conferir propriedades e padrões contra `application.properties`), serviço, fonte de configuração, utilitários e página.

Verificado: `tsc`, `vitest` (233 testes) e `vite build` no dashboard; a API foi compilada com `javac` contra stubs das bibliotecas e a validação do catálogo foi executada. Não verificado: Maven e testes Java (só o CI); o reinício real e a leitura da tabela por JDBC contra um Postgres.

## Dashboard 12.35.0 / API — Motor SAST: Opengrep no lugar do Semgrep

O Sentinel passa a usar o Opengrep (fork LGPL-2.1 do Semgrep CE, v1.30.0 fixada) como motor SAST padrão. O Semgrep continua suportado: a API escolhe `opengrep` e, se não achar, `semgrep` no PATH; `SENTINEL_SAST_ENGINE=semgrep|opengrep` força um. O id e o nome do analisador seguem "Semgrep" para não quebrar dados já gravados.

- API: `SastEngine` (escolha do motor e diferenças de CLI: sem `--metrics`, `LC_ALL=C.UTF-8`); `SemgrepAnalyzer` e `RulePlaygroundService` usam essa classe.
- Regras: `core-spring` 1.0.1 troca um `pattern-not` de `@RequestMapping(..., method = ..., ...)` (o Opengrep não lê) por `pattern-not-regex`; resultado idêntico nos dois motores.
- Infra: `scripts/instalar-opengrep.sh` (versão e SHA-256 fixos), Dockerfile (`WITH_OPENGREP=true` padrão, `WITH_SEMGREP=false`), compose, CI e workflows de evolução/descoberta.
- Paridade: `scripts/paridade-motores.py` roda os dois motores sobre o corpus de testes dos pacotes (menos `*-sql.yml`) e falha se (regra, arquivo, linha) divergir. O CI mantém `semgrep --test` porque o `opengrep test` não lê o layout de testes dos pacotes.

Benchmark OWASP v1.2 (média TPR−FPR): Semgrep 1.172.0 = 0,461 (igual ao `baseline.json`); Opengrep 1.30.0 = 0,513. Treino 0,507 → 0,615; validação 0,422 → 0,422. O ganho está só na metade de treino, então não prova melhor generalização. Opengrep foi mais rápido (53 s contra 66 s). `baseline.json` não foi alterado.

Verificado: execução dos dois motores sobre os pacotes e o Benchmark (números acima); paridade das regras.
NÃO verificado: Maven e testes Java (`SastEngineTest` etc.; sem acesso ao Maven Central aqui), build da imagem Docker, workflows num GitHub real, taint entre arquivos no Opengrep.

## Dashboard 12.36.0 / API — Taint entre arquivos com o Joern (opcional)

Primeira peça do ponto "Profundo" do plano: seguir o dado de um arquivo para outro sem compilar o sistema auditado.

- Ferramenta `joern-taint` no pacote `market-tools` (5.2.0): roda o Joern 4.0.583 (Apache-2.0, versão e SHA-256 fixos) sobre o código-fonte Java com o frontend `javasrc2cpg`, que não precisa de build. Entradas: parâmetros de endpoint Spring MVC e JAX-RS/Quarkus. Pontos perigosos: SQL por texto, comando do sistema e caminho de arquivo (CWE-89, 78 e 22). O caminho do dado vai na mensagem do achado.
- Só relata caminhos que cruzam mais de um arquivo (o Opengrep já cobre dentro do arquivo). Conversão para número, booleano ou UUID corta o fluxo.
- Fica desligada por padrão: a imagem só instala o Joern com `WITH_JOERN=true` (cerca de 2 GB); sem ele a ferramenta é ignorada como as demais. Memória: `SENTINEL_JOERN_XMX` (padrão 4g).
- Nada do que roda hoje muda: o Joern entra ao lado e só deve ficar se a medição mandar.
- CI: job `Joern (taint entre arquivos)` roda os 6 casos de `audit-api/joern/testes/`.

Verificado: Joern 4.0.583 rodado de verdade neste ambiente. Os 6 casos passam (3 vulneráveis acham a classe esperada; 3 seguros não acham nada: versão corrigida com `?`, conversão com `Integer.parseInt` e fluxo dentro de um arquivo só). No Spring PetClinic: 0 achados em 20 s (sem falso alarme). O SARIF real do Joern foi gravado como fixture e é lido pelo `SarifAnalyzer`.
NÃO verificado: Maven e testes Java (`SarifAnalyzerTest`, `PackLoaderTest` alterados; sem acesso ao Maven Central aqui, só o CI); build da imagem com `WITH_JOERN=true`; tempo e memória em sistema grande; acerto em código real (só 6 casos, sem medir falso negativo); o ganho sobre o Opengrep no conjunto de pares do plano (ainda não existe).

## Dashboard 12.37.0 / API — "Opengrep" no lugar de "Semgrep" na tela, nos relatórios e no histórico

Depois da troca do motor (12.35.0), a tela de auditoria, o relatório e o CSV ainda mostravam "Semgrep". Agora mostram "Opengrep".

- O analisador e os achados novos passam a se chamar "Opengrep" (`SastEngine.TOOL_NAME`). As chaves de configuração (`SEMGREP_*`, `sentinel.audit.semgrep.*`) não mudam, para não quebrar instalações existentes.
- Histórico: na subida, a API troca `tool_source = 'Semgrep'` por `'Opengrep'` nos achados antigos (`SchemaFixups`, idempotente; se falhar, só avisa e o painel ainda mostra "Opengrep" para o nome antigo).
- Cobertura por área, qualidade das regras e deduplicação tratam os dois nomes como o mesmo analisador (`SastEngine.isSastTool` e `sameTool`), inclusive em bancos que ainda não foram atualizados.
- Tela de Configurações e textos do painel citam o Opengrep.
- Quando a variável `SENTINEL_SAST_ENGINE=semgrep` força o Semgrep, o nome mostrado continua "Opengrep": o rótulo é do analisador SAST do Sentinel, não do binário.

Verificado: `tsc` e `vitest` do painel (299 testes; um teste de `FindingsPage` foi ajustado ao novo nome); os arquivos Java alterados compilam sem erro de sintaxe e o novo helper foi executado (`isSastTool`/`sameTool`).
NÃO verificado: Maven e testes Java (só o CI); a migração dos achados antigos contra um PostgreSQL real; relatório PDF/CSV com achados antigos.

## Dashboard 12.38.0 / API — A tela mostra quais ferramentas de comando rodaram

Faltava um jeito de saber se o Joern (e as demais ferramentas de comando do pacote `market-tools`) rodou: quando terminavam sem achar nada, não deixavam rastro, e quando eram ignoradas só havia uma linha de log.

- `AnalyzerResult` ganha `infoNote`: um resumo que aparece na tela de acompanhamento mesmo com o analisador em OK, sem mudar o status.
- O analisador SARIF resume: ferramentas que rodaram (tempo e número de achados), as que rodaram sem gerar resultado, as que falharam, as sem permissão para executar programas e as não instaladas. Ferramentas que não se aplicam ao projeto (sem arquivos do tipo) ficam de fora.
- Log da API: `Fonte SARIF '<id>' rodou em N s: M achado(s)`.
- README do Joern: seção "Como saber que rodou".

Verificado: os arquivos Java alterados não têm erro de sintaxe; testes novos escritos (`SarifAnalyzerTest`).
NÃO verificado: Maven e testes Java (só o CI); a aparência da nota na tela de acompanhamento (o painel não mudou; ele já exibe a nota do analisador).

## Dashboard 12.39.0 / API — Pacote TypeScript clean code (guia labs42io)

Novo pacote `ts-clean-code-labs42`, a partir do guia [clean-code-typescript](https://labs42io.github.io/clean-code-typescript/). As regras e as mensagens são do Sentinel (texto do guia não foi copiado).

- 21 regras `sentinel-cct-*`: parâmetros demais, parâmetro booleano, função que altera o argumento, escrita no global, valor padrão com `||`, condição composta, `typeof`/`instanceof` em cadeia, código inalcançável, laço com índice, `throw` sem `Error`, `catch` que só registra, rejeição de promessa ignorada, `then` encadeado, parâmetro `callback`, nome sem significado, número mágico, importação relativa profunda, código comentado, comentário de diário, marcador de posição e teste sem nome descritivo.
- Desligado por padrão. Perfil novo `clean-code-typescript` liga o pacote com severidade baixa.
- `docs/padrao-typescript/clean-code-labs42.md` mostra a tabela guia → regra e lista o que o guia pede e não dá para checar por código (SOLID, coesão, TDD, nomes pronunciáveis), que fica para revisão humana.

Verificado: `semgrep --test` passa nas 21 regras (caso positivo e negativo cada); `scripts/paridade-motores.py` mostra os mesmos achados no Opengrep e no Semgrep sobre todos os pacotes; toda regra tem remediação.
NÃO verificado: Maven e testes Java (só o CI; os testes de contagem de pacotes e o do novo perfil foram atualizados); precisão em repositório real (nenhuma taxa de falso positivo medida: use em sombra antes de pesar na nota).

## Dashboard 12.40.0 / API — Documentação reorganizada em `docs/` e IMPACTO.md reescrito

Toda a documentação do produto passou a viver em `docs/` (índice em `docs/README.md`). O README da raiz ficou curto e aponta para ela.

- `README.md` (900 linhas) dividido em `docs/guia/instalacao.md`, `docs/referencia/pacotes-de-regras.md`, `docs/referencia/auditoria-e-api.md`, `docs/historia.md` e `docs/historico-de-versoes.md`; `IMPACTO.md` foi para `docs/IMPACTO.md`; `audit-api/joern/README.md` e `audit-api/certs/README.md` viraram `docs/integracao/joern.md` e `docs/guia/certificados.md` (as pastas guardam só um aviso curto).
- Corrigido o que estava desatualizado e foi conferido no código: Quarkus 3.33 (era 3.15), Compose como caminho principal, ESLint ligado por padrão na imagem, 41 regras em `core-quality`, 16 em `core-spring`, Opengrep como motor na arquitetura, README sem a história do Bobathon.
- `IMPACTO.md` reescrito: sem estimativas de percentual ou horas; traz o que o Sentinel faz, o que foi medido (com a fonte e o que a medição não prova), o que não está provado e indicadores para medir o ganho num piloto.
- Removidos do repositório os arquivos `javac.*.args` (sobras de compilação) e adicionado ao `.gitignore`.

Verificado: contagens de regras por pacote, versões de Quarkus e das bibliotecas do painel, endpoints (comparados com as classes de recursos), variáveis e build args do Compose e do Dockerfile, links relativos dos arquivos movidos.
NÃO verificado: cada afirmação dos documentos longos (ARQUITETURA.md, auditoria-e-api.md e pacotes-de-regras.md receberam só os ajustes acima, não uma releitura linha a linha); endpoints fora da tabela original da API (`freshness`, `score-explanation`, `check-path`, `local-source`) foram descritos a partir da documentação do código, sem chamada real.

## Dashboard 12.41.0 / API — Correção dos achados da auditoria #95

Auditoria #95 do próprio Sentinel (nota 63,5; 11 HIGH, 11 MEDIUM, 95 LOW). Corrigido:

- Joern: falso positivo de SQL injection em consulta JPQL parametrizada (fluxo passava pelo objeto `this.em`). Agora fluxos com argumento só de texto fixo são descartados; novo caso seguro `seguro-jpql-parametrizado`.
- Dependência: `opentelemetry` 1.67.0 (CVE-2026-118259).
- Código: imports sem uso, números mágicos em `CommitMessages`, métodos longos em `GuideCatalog`, `GuideBook`, `SarifAnalyzer`, `auditHistory.ts`, `AuditHistoryChart.tsx` e `EnginePanel.tsx` divididos em funções menores; fontes do gráfico de auditorias em `rem`.
- `.sentinel.yml`: exclusão de casos de teste do Joern e documentos gerados; supressões com os ids reais do Checkstyle.

Deixado de fora de propósito: CVE do `minimatch` (a correção exige subir a cadeia do eslint; `overrides` deixou a árvore inconsistente), mensagens de commit antigas (histórico), acoplamento PMD/Fan-Out, 4 pares duplicados do jscpd, `import` com `*` do JPA, regra de 15 px do Stylelint.

Verificado: `tsc` limpo, `vitest` 299 testes verdes, suíte Joern 7/7, repro da auditoria com 0 achados, sintaxe Java com `javac`.
NÃO verificado: compilação Maven e testes Java (só no CI); nova execução do Stylelint/PMD; se os ids de supressão novos casam com os da auditoria (só vendo a próxima auditoria).


## Dashboard 12.42.0 / API — Regras de segurança para backend Node/Express (`core-security` 1.3.0)

Ciclo de teste com repositórios vulneráveis (Benchmark, Juice Shop, NodeGoat, DVNA), sem a API: o Maven Central não responde no ambiente de trabalho, então só rodei o Opengrep com os pacotes, como o CI faz.

- **OWASP Benchmark (Java):** média 0,62 no treino, 0,44 na validação, 0,53 no conjunto todo (local, regras `core-security`, `core-persistencia`, `core-spring`, `core-quality`). Os falsos positivos que sobram (60 a 70% em path traversal, SQL, comando e XSS na validação) vêm de armadilhas do Benchmark (switch ou condição constante, mapa com chave diferente). Fechar isso pede análise sensível a caminho; ajustar regra para esses casos só subiria a nota. Não foi feito. A diferença treino/validação pode ser só mistura diferente de armadilhas; não está provado sobreajuste.
- **Lacuna real:** só 7 regras de segurança para JS/TS e nenhuma para backend Node. No Juice Shop (código de aplicação, sem `data/static/codefixes`) o Sentinel não achava SQL injection, eval, redirect, path traversal, SSRF nem MD5.
- **Novo:** 11 regras `sentinel-node-*` em `core-security/rules/sentinel-node.yml`: SQL injection, NoSQL (`$where`), eval (por requisição e dinâmico), redirecionamento aberto, path traversal, SSRF, hash fraco (MD5/SHA-1), XXE, injeção de comando e desserialização. Reconhecem `req.query/params/body/...` e a forma desestruturada `({ params, query }: Request, res)`. Testes positivos e negativos e remediação para cada uma. `core-security` foi para 1.3.0 (56 regras).
- **Medido (Juice Shop, treino):** 0 → 15 achados `sentinel-node-*` no código da aplicação. Há ao menos um falso positivo conhecido (`createWriteStream` com extensão filtrada por lista) e um MD5 de checksum em script de build.
- **Medido (validação, repositórios que ninguém leu; só contagens):** NodeGoat 4 achados (eval ×3, redirect ×1); DVNA 4 (SQL, comando, redirect, XXE). Não conferi falso positivo nesses dois (regra do projeto: validação só com números).
- **Limites conhecidos:** a análise do Opengrep não segue o dado entre funções e arquivos (ex.: `verify(file)` em `fileServer.ts`; `$where` do NodeGoat montado em outro arquivo), nem entende `if (listaPermitida(x))` como saneamento.
- `juice-shop/juice-shop` entrou no conjunto de treino (`conjuntos.json`) porque o código foi lido.

Verificado: `semgrep --test` das 11 regras (todas passam), paridade Opengrep × Semgrep (503 achados iguais), contagens acima.
NÃO verificado: build Maven e testes Java (só no CI); API e dashboard rodando (sem Maven Central nem Docker aqui); precisão das regras novas além do Juice Shop.

## Dashboard 12.43.0 / API — Rodada 2: higiene de sessão e segredos em Node (`core-security` 1.4.0)

- **Correção da rodada 1:** no Juice Shop (sem `data/static/codefixes`) as regras `sentinel-node-*` davam 17 achados, não 15 como escrevi na entrada 12.42.0.
- **Novo:** 4 regras `sentinel-node-*`: `cookie-sem-flags` (cookie de sessão sem `httpOnly`), `jwt-sem-algoritmo`, `jwt-expiracao-ignorada` e `math-random-segredo`. Com testes positivos e negativos e remediação. `core-security` tem 60 regras (15 de Node).
- **Medido:** Juice Shop (treino) 17 → 20 achados `sentinel-node-*`; NodeGoat e DVNA (validação, só contagens) sem mudança (4 e 4): as regras novas não acharam nada neles, então a rodada não mostrou ganho fora do treino.
- **Não feito (e por quê):** nada novo para o Opengrep que siga o dado entre funções e arquivos, nem para guardas `if (listaPermitida(x))`; é limite do motor, e a saída seria o Joern, que só cobre Java.

Verificado: `semgrep --test` (15/15), paridade Opengrep × Semgrep (517 achados iguais).
NÃO verificado: build Maven e testes Java (CI); falsos positivos das regras novas além do Juice Shop; API e dashboard rodando.

## Dashboard 12.44.0 — Rodada 3: validação das regras Node em repositório novo

- **Alvo:** `snyk-labs/nodejs-goof` (nunca lido pelo projeto; só contagens). As 15 regras `sentinel-node-*` deram 2 achados: injeção de comando (1) e `Math.random` em segredo (1). As vulnerabilidades documentadas do Goof são sobretudo dependências com CVE, que cabem ao Trivy/OSV, não ao SAST; esse caminho não foi rodado aqui.
- **Validação acumulada das regras Node (só contagens):** NodeGoat 4, DVNA 4, Goof 2. Treino (Juice Shop): 20. Não conferi falso positivo nos três de validação, então não há taxa de precisão para eles.
- **Conclusão do ciclo (3 rodadas):** o ganho real foi cobrir o backend Node/Express (de 0 para 15 regras). No Benchmark Java os falsos positivos restantes são armadilhas que o Opengrep não resolve (exigem análise sensível a caminho). O motor não segue dado entre funções e arquivos; para Java existe o Joern, para JS/TS não.
- **Registro:** `conjuntos.json` ganhou NodeGoat, DVNA e Goof como validação.
- **Próximo passo possível:** medir precisão (falsos positivos) nos repositórios de validação lendo os achados um a um e movendo os repositórios para treino; e avaliar análise entre arquivos para JS/TS.

Verificado: contagens por regra com o Opengrep local.
NÃO verificado: API e dashboard rodando (sem Maven Central nem Docker no ambiente); precisão nos repositórios de validação; Trivy/OSV sobre o Goof.

## Dashboard 12.45.0 — Agente Sentinel Ciclo no lugar do Evolution e do Descoberta

- **Removido:** os workflows `evolucao.yml` e `descoberta.yml`, as instruções em `.github/agente/` (`sentinel-evolution.md`, `sentinel-descoberta.md`) e os scripts `evolucao-entrada.sh`, `descobrir-repos.sh` e `relatorio-descoberta.py`. Também saiu do `ci.yml` o job `Escopo dos PRs do agente` e do `protecao-main.json` o check obrigatório com esse nome (se a proteção já foi aplicada no GitHub, é preciso tirá-lo de lá também). O histórico dos agentes antigos (LOG, `precisao.md`, `relatorios/`) foi mantido.
- **Novo:** agente `sentinel-ciclo` (`.claude/agents/sentinel-ciclo.md`), do Claude Code, que roda na máquina de quem o chama e conduz o ciclo de auditoria (auditar, analisar, corrigir o Sentinel, repetir 3 rodadas), com as regras de treino e validação. Documentado em `docs/evolucao/AGENTE.md`, `docs/README.md` e `ARQUITETURA.md`.
- **Novo:** workflow `auditoria-completa.yml` e `scripts/auditoria-completa.sh`: sobem a API real com Postgres e todos os analisadores contra um repositório público e publicam achados, relatórios em PDF, SBOM e resumo como artefato. A calibração pesada do CI passa a rodar também em PRs `ciclo/*`.

Verificado: sintaxe do script (`bash -n`) e dos YAML.
NÃO verificado: o workflow `auditoria-completa.yml` e o agente ainda não rodaram; o workflow só pode ser disparado depois de estar na `main`.

## Dashboard 12.46.0 — Auditoria completa: resultado legível pelo log do job

Primeira execução real do workflow `Auditoria completa` (disparada pelo ambiente de nuvem do Claude pela API REST), contra `spring-projects/spring-petclinic` (`JAVA`, sem Joern): concluída com sucesso, nota 44,71 (segurança 0,96, qualidade 4,24, arquitetura 72,61, dependências 100, observabilidade 100, desempenho 90,48), 276 achados (1 CRITICAL, 10 HIGH, 101 MEDIUM, 157 LOW, 7 INFO) de 14 ferramentas (Opengrep, ESLint, Stylelint, Trivy, PMD, jscpd, kube-score, Checkov, Hadolint, kube-linter, Checkstyle, SQLFluff e as de higiene e commit).

Problema achado: o ambiente de nuvem não baixa o artefato (o armazenamento do GitHub devolve 403). Correção: o resumo e uma linha por achado (`achados-compactos.tsv`) passam a sair também no log do job, que a API do GitHub entrega. O artefato continua publicado para quem baixa na própria máquina.

Verificado: disparo, execução completa e leitura do resumo pelo log. NÃO verificado: a nova etapa de log (será exercida na próxima execução); o Joern (`joern=true`); repositórios grandes.

## Dashboard 12.47.0 — Rodada 1 (Juice Shop): pacotes JS/CSS grandes ficam fora da análise

- **Alvo (treino):** `juice-shop/juice-shop`, auditoria completa: nota 47,65, 4.193 achados. 2.379 (57%) vinham de bibliotecas de terceiros copiadas sem nome reconhecível, como `three.js` (853 KB, 2.117 achados `sentinel-ts-var`).
- **Mudança geral:** arquivo `.js/.mjs/.cjs/.css` com 400 KB ou mais é tratado como pacote de terceiros: o Opengrep recebe `--exclude` por arquivo e o CleanCode o ignora. Vale pelo tamanho, não pelo nome (`ExternalTools.isOversizedBundle`/`oversizedBundles`). Não há lista de arquivos do Juice Shop.
- **Risco:** código próprio gerado e muito grande deixa de ser analisado (falso negativo). Limite escolhido por ser raro em código escrito à mão.

Verificado: lógica do helper com um programa de teste via `javac` (7 casos, incluindo pasta ignorada); testes JUnit escritos antes (não compilavam sem os métodos).
NÃO verificado: JUnit/Maven e a compilação de `SemgrepAnalyzer`/`CleanCodeAnalyzer` (só o CI); o ganho real (reauditar o Juice Shop depois do merge); Gitleaks em testes e ruído em `data/static`, ainda por analisar.

## Dashboard 12.48.0 — Lado (back, front, comum) em cada regra e analisador

- **Por quê:** o lado só existia por pacote, no dashboard, e pacotes como segurança e qualidade misturam regras de Java e de TypeScript. Primeira de duas PRs para poder auditar só o back-end ou só o front-end.
- **Mudança:** cada regra tem um lado, em `metadata.side` ou deduzido da linguagem e dos caminhos (`RuleSide`). 18 regras ganharam o campo por serem ambíguas (15 de Node/Express, 2 de configuração Spring/JPA em YAML, 1 de `tsconfig`). O catálogo e a API de configuração do motor devolvem `side` por regra e por analisador (`AnalyzerCatalog.sideOf`).
- **Resultado da marcação (255 regras):** 142 back, 89 front, 24 comuns (21 de Docker/Kubernetes e 3 de qualidade que valem para Java e TypeScript). Entre os analisadores internos só PMD, OWASP-Dependency e Evidências são back, e ESLint e código morto são front; o resto é comum.
- **Versões:** core-security 1.4.1, core-boas-praticas 1.0.1, core-spring e core-persistencia 1.0.2.
- **Não feito:** dividir `sentinel-int-http-sem-tls` em duas regras (mudaria o id de achados e supressões existentes); ela fica comum. Nada muda ainda para quem audita: o desligamento por lado vem na PR seguinte.

Verificado: testes Opengrep de core-security, boas-praticas, persistencia e spring; `RuleSide` com um programa de teste via `javac`; `tsc` do dashboard.
NÃO verificado: JUnit/Maven (só no CI): `RuleSideTest`, `AnalyzerCatalogSideTest` e a compilação do catálogo e da API.

## Dashboard 12.49.0 — Escopo da auditoria: desligar o back-end ou o front-end de um sistema

- **Por quê:** um projeto só de Java não tem front, e um só de interface não tem back. Segunda de duas PRs (a primeira, 12.48.0, deu um lado a cada regra e analisador).
- **Ajuste do sistema:** novo campo `disableSides` (`back` e/ou `front`; os dois juntos são recusados). Fica no JSON do ajuste; sistemas antigos continuam iguais.
- **O que um lado desligado faz:** as regras do lado saem como se estivessem desligadas uma a uma; os analisadores do lado não rodam (PMD, OWASP-Dependency e Evidências no back; ESLint e código morto no front) e aparecem como DESLIGADO com o motivo; CleanCode e Duplicação, que olham Java e TypeScript juntos, perdem só os achados dos arquivos do lado desligado. Docker, Kubernetes, segredos e dependências (comuns) continuam.
- **Tela (aba Motor de regras):** bloco "Escopo da auditoria" com atalhos Full-stack, Só back-end e Só front-end e cartões com interruptor; pacotes por lado (um pacote misto aparece nos dois com a contagem de cada lado); abas Todas, Back-end, Front-end, Infra e comum; coluna Lado; regra de lado desligado fica apagada e sem edição. Aba Analisadores agrupada por lado, com os do lado desligado travados.
- **Não feito:** o Opengrep ainda lê as regras dos dois lados e os achados do lado desligado são filtrados depois (corretos, mas sem ganho de tempo); as ferramentas externas dos pacotes (Stylelint, dependency-cruiser, Checkstyle) ainda não têm lado e continuam no "comum".

Verificado: 312 testes do dashboard, `tsc` e `vite build`; programa de teste via `javac` para `SystemTuning` e `RuleSide`.
NÃO verificado: JUnit/Maven (só no CI): `SystemTuningSidesTest` (política, JSON, analisadores); a tela num navegador real.

## Dashboard 12.50.0 — Remove `auditar-frequency.ps1`

- **Removido:** `auditar-frequency.ps1` da raiz: script PowerShell que cadastrava e auditava só o repositório Frequency, com valores fixos. Nada no projeto o usava; a tela Sistemas, o workflow `auditoria-completa.yml` e o agente `sentinel-ciclo` fazem o mesmo para qualquer repositório.
- **Mantidos:** `settings-local.xml` (usado pelo guia de instalação), `evidencias/` e `docs/evidencias/*` (usados pelo compose e pelo código).

Verificado: nenhuma referência ao arquivo restou (busca no repositório).

## Dashboard 12.51.0 — Ciclo, rodada 2 (WebGoat): bibliotecas de terceiros em `static/plugins` e `static/js/libs`

- **Medido (WebGoat, treino):** nota 23,38 e 3.884 achados; 2.447 em `src/main/resources`, quase todos de bibliotecas copiadas (`wysihtml5-0.3.0.js` 750, `asciidoctor-default.css` 212, `ace.js` 155, `animate.css` 136).
- **Causa:** a detecção de biblioteca só olhava o nome (jquery, bootstrap, `*.min.*`); `plugins/` e `js/libs/` passavam.
- **Mudança:** `ExternalTools.isVendoredLibrary` passa a tratar JS/CSS dentro de `plugins`, `libs`, `vendor`, `vendors`, `third_party` quando há uma pasta de ativos web acima (`static`, `public`, `webapp`, `assets`, `resources`, `www`). Fora dos ativos ou de outro tipo de arquivo (Java, TS em `src/plugins`) não conta. O Semgrep ganhou `static/plugins`, `public/plugins`, `webapp/plugins`.
- **Não feito:** os achados Java (Checkstyle `magic.number` 569, PMD) não foram mexidos nesta volta.

Verificado: programa `javac` com os caminhos do WebGoat e os casos negativos. NÃO verificado: JUnit/Maven (só no CI); nova auditoria do WebGoat (depois do merge).

## Dashboard 12.52.0 — Ciclo, rodada 2 (continuação): biblioteca reconhecida pelo cabeçalho

- **Medido (WebGoat após 12.51.0):** 3.083 achados; `asciidoctor-default.css` (208) e `animate.css` (134) seguiam no Stylelint porque ficam em `static/css`, sem nada no caminho.
- **Mudança:** `ExternalTools.hasThirdPartyBanner`: o arquivo JS/CSS começa (depois de `@charset`) com um comentário que cita licença (MIT, Apache, BSD, GPL, `@license`) e um sinal de origem externa (endereço, versão, `/*!`). Aplicado no Stylelint e demais ferramentas por arquivo (`SarifAnalyzer`), no ESLint e no CleanCode.
- **Limite:** aviso de licença da própria empresa com endereço no topo do arquivo também seria tratado como biblioteca; licença sem endereço/versão não conta.

Verificado: programa `javac` com os cabeçalhos do WebGoat e casos negativos. NÃO verificado: JUnit/Maven (só CI); nova auditoria do WebGoat.

## Dashboard 12.53.0 — O que o `exclude` do `.sentinel.yml` descartou, por ferramenta

- **Problema visto em uso:** ao auditar o próprio Sentinel com `WITH_JOERN=true`, o Joern rodou (59 s, 2 achados nos logs) mas nenhum achado dele apareceu: ficavam em `audit-api/joern/testes/**`, que o `.sentinel.yml` exclui. Nada na tela dizia isso.
- **Mudança:** `ProjectConfig.Result` ganha `excludedByTool`; o resumo da fase de pontuação passa a dizer, por exemplo, "3 ignorados por exclude (Joern (taint entre arquivos) 2, Checkov 1)".
- **Não feito:** linha de progresso própria para cada ferramenta de pacote (continuam dentro da linha SARIF).

Verificado: nada além da leitura do código. NÃO verificado: JUnit/Maven (só CI), a tela.

## Dashboard 12.54.0 — Ciclo (laço até estabilizar): WebGoat, java-sec-code e SecurityShepherd

Branch única `ciclo/estabilizacao`; as auditorias rodaram a partir dela. Validação (dvja, NodeGoat) só por contagem.

- **Volta 1 (WebGoat):** `src/it` (testes de integração do Maven) fora do lint e das métricas; tamanhos comuns (8, 16, 32, 64, 255…) deixam de ser "número mágico" no Checkstyle. 2.741 → 2.363 achados.
- **Volta 2 (java-sec-code, treino novo):** parâmetro de handler Spring sem anotação conta como entrada do usuário (command injection, SSRF, path traversal, taint); regras novas `sentinel-xstream-qualquer-tipo`, `sentinel-ssti-modelo-variavel`, `sentinel-fastjson-autotype`, `sentinel-classloader-define-classe` (core-security 1.5.0). 663 → 671 achados, 8 problemas reais a mais; dvja e NodeGoat inalterados (176 e 328).
- **Volta 3 (SecurityShepherd, treino novo):** `PathRole` reconhece `src/it` como teste; achado de qualidade em teste ou pasta ignorada não entra (jscpd lia o projeto inteiro): 5.088 → 4.763. `<%= bundle.getString(...) %>` e saída já codificada deixam de ser XSS em JSP.
- **Não feito:** o Opengrep ainda lê testes de propósito (regras de segurança); `rs.getString` em JSP continua sinalizado (é dado do banco).

Verificado: testes das regras (opengrep --test, 61 casos), programas `javac` para a detecção por caminho. NÃO verificado: JUnit/Maven (só CI), medição do XSS em JSP.

## Ciclo, volta 4 (branch `ciclo/estabilizacao-2`) — OWASP/wrongsecrets (treino)

- **Medido:** nota 31,55 e 969 achados (CRITICAL 19, HIGH 216). Gitleaks 165, Trivy 156, Opengrep 119, Checkov 115, kube-score 81, Checkstyle 79, Stylelint 77.
- **Análise:** o projeto existe para expor segredos (CTF), então Gitleaks, Trivy `DS-0031` e `sentinel-spring-senha-em-properties` são acertos. `application.properties:9` (`password=...`) foi achado; as linhas com `if_you_see_this_please_use_...` são marcadores de lugar de propósito, não segredo. Nenhuma lacuna geral encontrada no código Java dos desafios (os segredos vêm de `@Value`, Dockerfile ou Kubernetes, que já têm regra).
- **Mudança de código:** nenhuma. **Volta sem ganho** (conta para a estabilização: 1 de 2).
- **Fora do escopo declarado:** 112 achados de Terraform (`aws/`, `azure/`, `gcp/`) via Checkov e Trivy; decidir se o Sentinel deve ignorar `.tf` (hoje o escopo do projeto exclui Terraform, mas as ferramentas ainda leem).

Verificado: leitura do log da auditoria e do código do repositório de treino. NÃO verificado: nada além disso.

## Dashboard 12.55.0 — Ciclo, volta 5 (branch `ciclo/estabilizacao-2`): Juice Shop reauditado, estável

- **Medido (Juice Shop, treino):** nota 47,7 e 1.958 achados (CRITICAL 20, HIGH 118). Na rodada 1 (12.47.0) eram 4.193 achados e nota 47,65. A nota quase não mexe porque a dimensão segurança já está em 0.
- **Análise:** os CRITICAL são todos de Terraform (Trivy). Os HIGH de Opengrep são acertos do projeto (SQL injection, path traversal, `$where`, `eval`, JWT fixo, `bypassSecurityTrust*`), parte repetida em `data/static/codefixes` (variantes de correção do desafio). Os 57 do Gitleaks são HIGH, mas 4 em cada grande arquivo de `test/` já saem com confiança BAIXA no `FindingConfidence` (caminho de teste): não há lacuna ali.
- **Mudança de código:** nenhuma. **Volta sem ganho** (2 de 2 seguidas, com a volta 4): o laço está estável.
- **Decisão pendente (não tomada, muda a política de escopo):** ignorar `.tf` (Terraform) em Trivy e Checkov, que hoje leem arquivos que o escopo do projeto exclui (Juice Shop: ~34 CRITICAL; wrongsecrets: 112 achados). Também não escrevi regra para `data/static/codefixes`, por ser um padrão específico deste repositório.
- **Validação (dvja, NodeGoat):** sem mudança de código nas voltas 4 e 5, então as contagens (176 e 328) não têm motivo para mudar; não reauditei.

Verificado: log da auditoria completa (execução 42148534934) e leitura de `GitleaksAnalyzer` e `FindingConfidence`. NÃO verificado: nova medição de dvja/NodeGoat; JUnit/Maven (nada de Java mudou).

## Dashboard 12.56.0 — Ciclo, volta 6 (branch `ciclo/estabilizacao-3`): JavaSecLab (treino novo), bibliotecas embutidas no Opengrep

- **Repositório novo:** whgojp/JavaSecLab (branch `i18n-bilingual`, JAVA, 888 estrelas, app Java de treino de segurança), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 30,52 e 11.274 achados (CRITICAL 8, HIGH 221). Opengrep 8.929, Stylelint 1.000. Os maiores arquivos de achados do Opengrep eram `static/js/codemirror.js` (835), `static/lib/layui/layui.js` (374, duas cópias), `ueditor/third-party/codemirror` (310), `chunk-*.57e3ed6f.js` (159), `canvas-tools*.js` (296): bibliotecas de terceiros. Só `sentinel-ts-igualdade-frouxa` e `sentinel-ts-var` somam ~7.400.
- **Causa:** o Opengrep só excluía por nome/pasta fixos e por tamanho (>400 KB). A detecção por pasta, cabeçalho de licença e nome (12.51/12.52) valia só para lint e métricas.
- **Mudança:** `ExternalTools.vendoredFiles` (usado pelo Opengrep) junta tamanho, `isVendoredLibrary` e cabeçalho de terceiros (limite de 1.500 exclusões). `isVendoredLibrary` agora também reconhece JS/CSS em `lib` e `third-party` sob pasta de ativos web e saída de build com hash no nome (`chunk-xxx.57e3ed6f.js`) sob pasta de ativos. Cabeçalho de terceiros vale também com comentários de linha `//` no topo (licença + endereço/versão).
- **Limite:** `lib` sob `static`/`public`/`webapp`/`resources` conta como terceiros mesmo se o projeto guardar código próprio ali.
- **Não feito:** achados reais do código Java do JavaSecLab (SQLi, XSS etc.) ainda não analisados um a um; fica para a próxima leitura.

Verificado: programa `javac` com os caminhos do JavaSecLab, casos negativos e leitura de diretório temporário. NÃO verificado: JUnit/Maven (só CI); nova auditoria do JavaSecLab; contagem de dvja e NodeGoat depois da mudança (a conferir pelo CI).

### Medições da volta 6 e 12.57.0

- **JavaSecLab depois de 12.56.0:** nota 30,93 e 3.433 achados (era 30,52 e 11.274). Opengrep 8.929 → 1.843. A nota quase não mexe porque segurança já estava em ~0,8. ESLint foi de 27 para 493: o JavaSecLab ainda tem `lay-module/echarts/echarts.js` (cabeçalho Apache da ASF não era reconhecido); sem causa conclusiva para o resto da alta (a lista compacta do log é cortada, não dá para comparar arquivo a arquivo).
- **12.57.0:** `hasThirdPartyBanner` reconhece "Licensed to the Apache Software Foundation" e olha 1.200 caracteres do topo (antes 600). Verificado por `javac` (cabeçalho ASF positivo; "Licensed to Acme" negativo).
- **Validação (números do CI, sem ler código):** NodeGoat 328 achados (nota 46,86) e dvja 176 (nota 48,95), iguais às voltas anteriores (12.56.0 e 12.57.0, respectivamente). Treinos anteriores não reauditados.
- **Branch `ciclo/estabilizacao-3`:** 1 volta com mudança; sem PR ainda.
- NÃO verificado: JUnit/Maven (só CI, no PR); JavaSecLab com 12.57.0; achados reais de Java do JavaSecLab.

## Dashboard 12.58.0 — Ciclo, volta 7 (branch `ciclo/estabilizacao-3`): Damn-Vulnerable-Bank (treino novo), pacote Android

- **Repositório novo:** rewanthtammana/Damn-Vulnerable-Bank (branch `master`, JAVA, 767 estrelas, app Android Java + backend Node de treino), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 44,76 e 591 achados (CRITICAL 8, HIGH 47). Opengrep 169, ESLint 201, Trivy 82, PMD 41, Checkstyle 34. Todos os CRITICAL/HIGH de Trivy são CVE de dependência do `package-lock.json` (acertos).
- **Análise (código lido, treino):** o Sentinel não tinha nenhuma regra de Android. Falsos negativos reais: URL do Intent carregada no WebView (`CurrencyRates.java`), token no logcat (`Log.d("accesstoken", accessToken)`), `allowBackup`/`usesCleartextTraffic`/`exported` no manifesto, `Sequelize.literal` com valor da requisição (`Balance/transfer.js`). Falso positivo/ruído: 129 `no-require-imports` do ESLint em JavaScript CommonJS.
- **Mudança:** `core-security/rules/sentinel-android.yml` (6 regras gerais, testes e remediação); `Sequelize.literal` como ponto de risco de `sentinel-node-sql-injection`; ESLint padrão deixa de aplicar `no-require-imports` a `.js/.cjs/.jsx` (continua em `.ts`). Nos arquivos do próprio repositório de treino, as regras novas acham 7 pontos e 2 em `transfer.js`, nenhum falso positivo.
- **Não feito:** cifra XOR caseira (`EncryptDecrypt`) e `jwt.verify` com segredo fixo curto não ganharam regra nova (o segundo já sai por `sentinel-api-jwt-chave-fixa-js`); achados de qualidade Java/PMD não mexidos.

Verificado: testes das regras com Semgrep (core-security) e Opengrep, paridade dos dois motores (542 = 542), varredura do código de treino, ESLint rodado nos dois tipos de arquivo. NÃO verificado: nova auditoria do Damn-Vulnerable-Bank com 12.58.0; dvja e NodeGoat com 12.58.0 (a conferir pelo CI); JUnit/Maven (nada de Java mudou).

### Validação da volta 7 (números do CI, sem ler código)

- **dvja:** 176 achados, nota 48,95: igual às voltas anteriores. **NodeGoat:** 278 achados, nota 47,14 (era 328 e 46,86); a queda de 50 vem do ESLint (`no-require-imports` em CommonJS), não de regra de segurança. Sem leitura do código para confirmar que nenhum achado de segurança sumiu; severidades CRITICAL 13, HIGH 56 conferidas só como contagem.
- **Branch `ciclo/estabilizacao-3`:** 2 voltas, ambas com mudança; sem PR ainda (fecha com 6 voltas ou 2 sem mudança).

## Dashboard 12.59.0 — Terraform fora do Trivy (decisão aprovada pelo João em 2026-11-06)

- **Decisão:** ignorar Terraform (`.tf`) no Trivy e no Checkov, pois o escopo do projeto já o exclui.
- **Mudança:** `TrivyAnalyzer` passa `--skip-files **/*.tf,**/*.tf.json,**/*.tfvars`. O Checkov já rodava só com `--framework dockerfile kubernetes github_actions` (`market-tools/tools/infra.yml`), sem Terraform; nada a mudar ali. O Gitleaks continua lendo `.tf` (segredo em Terraform segue sendo achado).
- **Efeito esperado:** Juice Shop (~34 CRITICAL) e wrongsecrets (~112 achados) perdem os achados de Terraform do Trivy; o restante não muda.

Verificado: teste do Trivy atualizado (`ExternalScannersTest`). NÃO verificado: JUnit/Maven (só CI); nome exato da flag em `trivy fs` na versão da imagem; reauditoria de Juice Shop e wrongsecrets.

## Dashboard 12.60.0 — Ciclo, volta 8 (branch `ciclo/estabilizacao-4`): Android-InsecureBankv2 (treino novo), armazenamento e chave fixa

- **Repositório novo:** dineshshetty/Android-InsecureBankv2 (branch `master`, JAVA, 1.488 estrelas, app Android Java + servidor Python de treino), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 57,16 e 293 achados (HIGH 5, MEDIUM 49). Opengrep 136, PMD 85, Checkstyle 42. As regras Android da 12.58.0 acharam: URL do Intent no WebView (`ViewStatement`), log de senha (`DoLogin`), `exported` (7), `allowBackup` (3).
- **Análise (código lido, treino):** falsos negativos: `MODE_WORLD_READABLE` em `getSharedPreferences` (`MyBroadCastReceiver`, que lê a senha cifrada) e chave AES escrita no código (`CryptoClass.key`). Falso positivo claro: nenhum (`sortOrder == ""` é bug real). Ruído de qualidade: `TODO` gerados pelo IDE (71 × `sentinel-comentario-pendencia`).
- **Mudança:** `sentinel-android-modo-mundo` (MODE_WORLD_* em preferências, arquivos, diretório e banco), `sentinel-crypto-chave-fixa-no-codigo` (`SecretKeySpec` com literal e campo `String` de nome key/chave/secret/segredo com literal) e `sentinel-crypto-iv-fixo` (`new byte[n]` ou literal). Testes e remediação incluídos. No treino, a regra de modo-mundo acha 1 ponto e a de chave fixa 1, sem falso positivo.
- **Não feito:** IV zerado vindo de campo `byte[] iv = {0,...}` (o motor não liga o campo ao `IvParameterSpec`); SQL injection por `selection` no ContentProvider exportado; escrita do extrato em armazenamento externo.

Verificado: `semgrep --test` (Android 4/4, crypto 6/6), paridade Opengrep × Semgrep (553 = 553), varredura do código de treino. NÃO verificado: nova auditoria do InsecureBankv2 com 12.60.0; dvja e NodeGoat com 12.60.0 (a conferir pelo CI); JUnit/Maven (nada de Java mudou).

### Medições da volta 8 com 12.60.0 (números do CI)

- **InsecureBankv2 depois:** nota 53,56 e 296 achados (era 57,16 e 293): +3 HIGH novos e verdadeiros (`modo-mundo` em `MyBroadCastReceiver:29`; `chave-fixa` em `CryptoClass:25` e em `wip-attackercode/ExploitAES/.../MainActivity:39`). A nota cai porque a auditoria passou a ver falhas reais; não é piora de qualidade das regras.
- **Validação (só números):** dvja 176 achados, nota 48,95; NodeGoat 278 achados, nota 47,14: ambos iguais à volta 7. Treinos anteriores não reauditados.
- **Branch `ciclo/estabilizacao-4`:** 1 volta com mudança; sem PR ainda.
- NÃO verificado: JUnit/Maven (só no PR); treinos anteriores com 12.60.0.

## Dashboard 12.61.0 — Ciclo, volta 9 (branch `ciclo/estabilizacao-4`): cr0hn/vulnerable-node (treino novo), regras Node de camada de dados

- **Repositório novo:** cr0hn/vulnerable-node (branch `master`, JAVASCRIPT, 493 estrelas, loja Express/Postgres/Mongo de treino), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 48,24 e 252 achados (CRITICAL 1, HIGH 19). Opengrep 134 (a maioria qualidade/CSS). Acertos de segurança: path traversal, command injection, SSRF, open redirect, JWT/segredo padrão, eval, Gitleaks.
- **Análise (código lido, treino):** falsos negativos reais: SQL montado por concatenação/template em `model/db.js` (a falha fica numa função que recebe o dado, longe da rota, e o motor não segue o dado entre funções); filtro Mongo com `req.body`/`req.query` cru (`routes/shop.js`); merge recursivo sem bloqueio de `__proto__` (`model/merge.js`); `new RegExp(req.body.pattern)` (`routes/tools.js`); XSS refletido em `res.send('<h1>' + dado)` (só nas sementes `sast/seeds`). Falso positivo claro: nenhum novo (o `<%- body %>` do njsscan em `layout.ejs` é o idioma do layout, não corrigido).
- **Mudança:** 5 regras em `sentinel-node.yml`, com testes e remediação: `sentinel-node-sql-concatenado` (consulta com `+`/template no `query/execute/raw` de objeto de banco, fora de funções que recebem `req`, para não duplicar `sentinel-node-sql-injection`), `sentinel-node-nosql-operador` (req.body/req.query no filtro de find/update/delete, sem `where:` do Sequelize nem `String()`), `sentinel-node-prototype-pollution`, `sentinel-node-regexp-da-requisicao` e `sentinel-node-xss-refletido` (HTML literal + dado da requisição em `res.send`).
- **Medido (depois, CI):** nota 47,65 e 264 achados (era 48,24 e 252): +12, todos conferidos no código (6 SQL, 2 NoSQL, 2 prototype pollution, 1 RegExp, 1 XSS). A nota cai porque a auditoria passou a ver falhas reais.
- **Fora do repositório alvo:** nas fontes do Juice Shop (treino) as regras novas acham 2 pontos (`likeProductReviews.ts:25,43`, `findOne({_id: req.body.id})`), ambos verdadeiros; nenhum falso positivo.
- **Validação (só números do CI):** NodeGoat 278 achados, nota 47,14 e dvja 176, nota 48,95: iguais à volta 8. As regras novas não acharam nada no NodeGoat (não dá para dizer, sem ler o código, se faltou ou não havia o que achar).
- **Não feito:** função de dados que recebe o dado por parâmetro (`findOne(body)`, `new RegExp(pattern)`, `fetch(url)` nas sementes `sast/seeds`) continua sem alerta (limite do motor entre funções); `<%- %>` em EJS (precisa de regra `generic` com filtro de `body`/`include`); JWT de `config.js` já saía pela regra de segredo padrão.

Verificado: `opengrep --test` e `semgrep --test` (75/75), paridade Opengrep × Semgrep (588 = 588), varredura de vulnerable-node e Juice Shop, auditorias no CI (vulnerable-node antes e depois, NodeGoat, dvja). NÃO verificado: JUnit/Maven (nada de Java mudou; roda no PR); Opengrep local 1.33.1 e não o 1.30.0 fixado no CI; treinos anteriores (WebGoat etc.) não reauditados; recall em código Node que ninguém leu além dos treinos.
- **Branch `ciclo/estabilizacao-4`:** 2 voltas, ambas com mudança; sem PR ainda (fecha com 6 voltas ou 2 sem mudança).

## Dashboard 12.62.0 — Ciclo, volta 10 (branch `ciclo/estabilizacao-4`): skills/secure-code-game (treino novo), XXE, upload e exec dinâmico

- **Repositório novo:** skills/secure-code-game (branch `main`, MIXED, 2.857 estrelas, jogo de treino de código seguro em JS, Python, Go e C), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 52,05 e 898 achados (CRITICAL 4, HIGH 35). Opengrep 704 (671 `ts-console-log`), ESLint 89, Trivy 53 (CVE de `package-lock.json`, acertos), Gitleaks 4 (`password.txt` de treino), njsscan 2 (Squirrelly sem escape, acerto). Python, Go e C ficam fora do escopo das regras (stack JAVA/JS).
- **Análise (código JS lido, treino):** `Season-2/Level-3/code.js` tinha três falhas sem alerta de segurança: `libxmljs.parseXml(..., {replaceEntities: true})` (XXE), `path.join(__dirname, req.file.originalname)` gravado com `writeFileSync` (nome do arquivo enviado) e `exec(command)` com texto vindo do XML. Falso positivo claro de segurança: nenhum.
- **Mudança:** `sentinel-node-xxe` reconhece `replaceEntities: true`; `sentinel-node-path-traversal` ganha `req.file.originalname` (upload multer) como origem; `sentinel-node-exec-dinamico` (WARNING, nova): `exec`/`execSync` com argumento que não é texto fixo, fora do que a regra de injeção já cobre. Testes e remediação incluídos.
- **No treino:** secure-code-game acha XXE (1), upload (1) e 6 `exec` dinâmicos (1 em `Level-3/code.js`, 5 em `Season-4/bin/prodbot.js`, com `${porta}`/`${pid}` em `lsof`/`kill`: baixo risco, severidade WARNING); vulnerable-node acha mais 1 `exec` dinâmico real (`routes/extras.js:33`).
- **Não feito:** achados de Python, Go e C do repositório; `console.log` em massa (671) é ruído de qualidade do conteúdo de treino, sem regra nova.

Verificado: `opengrep --test` (76/76, v1.30.0), paridade Opengrep × Semgrep (602 = 602), varredura de secure-code-game e vulnerable-node. NÃO verificado: nova auditoria no CI com 12.62.0, dvja e NodeGoat com 12.62.0, JUnit/Maven (nada de Java mudou).

### Medições da volta 10 com 12.62.0 (números do CI)

- **secure-code-game depois:** nota 48,45 e 906 achados (era 52,05 e 898): +8, todos conferidos no código de treino (1 XXE, 1 upload, 6 `exec` dinâmico). A nota cai porque a auditoria passou a ver falhas reais.
- **Validação (só números, sem ler código):** dvja 176 achados, nota 48,95 (igual). NodeGoat 279 achados, nota 46,87 (era 278 e 47,14): o +1 é um `sentinel-node-exec-dinamico` MEDIUM em `Gruntfile.js:165`; não li o arquivo, então não sei se é falso positivo (arquivo de build, WARNING). Nenhuma outra contagem mudou.
- **Branch `ciclo/estabilizacao-4`:** 3 voltas, todas com mudança; sem PR ainda (fecha com 6 voltas ou 2 sem mudança).
- NÃO verificado: JUnit/Maven (só no PR); treinos anteriores com 12.62.0.

## Dashboard 12.63.0 — Ciclo, volta 11 (branch `ciclo/estabilizacao-4`): christophetd/log4shell-vulnerable-app (treino novo), Gradle

- **Repositório novo:** christophetd/log4shell-vulnerable-app (branch `main`, JAVA, 1.142 estrelas, app Spring Boot 2.6.1 com Log4j2 de propósito vulnerável), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 90,73 e 9 achados (HIGH 1: Trivy `DS-0002`, sem USER no Dockerfile). Acertos: `sentinel-log-injection` em `MainController.java:18` (a rota do log com o cabeçalho, o ponto do Log4Shell), Dockerfile sem USER/HEALTHCHECK.
- **Falso negativo principal (sem correção):** nenhum CVE de dependência. O projeto é Gradle sem lockfile, o Log4j-core (CVE-2021-44228) entra só como dependência transitiva de `spring-boot-starter-log4j2`, e o Trivy só lê Gradle com `gradle.lockfile`. Dimensão dependências ficou em 100, o que é enganoso. No pipeline padrão o Trivy substitui o `OWASPDependencyAnalyzer`, então a leitura de `build.gradle` por OSV não entra nos achados.
- **Mudança (pequena, geral):** `DependencyExtractor.fromGradle` passa a ler a notação de mapa (`group: 'x', name: 'y', version: 'z'`) e a versão fixa do plugin `org.springframework.boot` (como `org.springframework.boot:spring-boot`). Vale para SBOM e checagens de componente (usam o extrator); versões por variável continuam ignoradas. Teste novo em `DependencyExtractorTest`.
- **Medido (depois, CI):** log4shell igual (nota 90,73, 9 achados): sem ganho de detecção, como esperado, porque o Trivy responde pelos CVEs.
- **Não feito:** resolver dependência transitiva de Gradle sem lockfile (exigiria rodar o Gradle sobre código de terceiros, que não fazemos, ou um mapa BOM do Spring Boot, que seria sob medida); consultar OSV para manifestos que o Trivy não resolve (muda o desenho do pipeline). Fica como decisão futura.
- **Validação (só números do CI):** dvja 176 achados, nota 48,95 e NodeGoat 279, nota 46,87: ambos iguais à volta 10.

Verificado: programa `javac` com o extrator (4 coordenadas esperadas; versão por variável ignorada), auditoria do log4shell antes e depois no CI, dvja e NodeGoat no CI. NÃO verificado: JUnit/Maven (só no PR); SBOM do log4shell com a mudança.
- **Branch `ciclo/estabilizacao-4`:** 4 voltas, todas com mudança; sem PR ainda (fecha com 6 voltas ou 2 sem mudança).

## Dashboard 12.64.0 — Ciclo, volta 12 (branch `ciclo/estabilizacao-4`): OWASP/DVSA (treino novo), evento do AWS Lambda como origem

- **Repositório novo:** OWASP/DVSA (branch `master`, JAVASCRIPT, 547 estrelas, app serverless de treino na AWS, Lambdas em Node e Python), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 41,99 e 727 achados (CRITICAL 25, HIGH 172). Trivy 349, ESLint 220, Opengrep 111 (quase tudo qualidade), Gitleaks 8 (chaves de exemplo em `template.yml`/`sls.yml`/bundle: acertos de padrão, sem lacuna). Python das Lambdas fica fora do escopo das regras.
- **Falso negativo (código lido, treino):** `order-manager.js` faz `serialize.unserialize(event.body)` e `event.headers` (node-serialize, RCE) sem alerta; `admin_shell.js` faz `eval(cmd)` com `event.body.cmd` e só saiu como `eval-dinamico` (WARNING). As regras de desserialização e `eval` só viam `req`/`request` como origem, não o `event` do handler Lambda.
- **Mudança:** `event.body`, `event.headers`, `event.queryStringParameters` e `event.pathParameters` (variável chamada `event`) passam a ser origem em `sentinel-node-desserializacao` e `sentinel-node-eval-requisicao`. Testes novos (positivo, seguro e `config.body` como negativo). No código do DVSA: `unserialize` (2 pontos) e `eval(cmd)` agora como ERROR.
- **Não feito:** outras regras de injeção (exec, SQL, path) não ganharam a origem `event`; Python (Lambdas) sem regra.

Verificado: `semgrep --test` (21/21; Semgrep 1.184, não o Opengrep) e varredura do código de treino. NÃO verificado: Opengrep nesta volta; nova auditoria do DVSA com 12.64.0 e dvja/NodeGoat (a conferir pelo CI); JUnit/Maven (nada de Java mudou).
- **Branch `ciclo/estabilizacao-4`:** 5 voltas, todas com mudança; sem PR ainda.

### Medições da volta 12 com 12.64.0 (números do CI)

- **DVSA depois:** nota 41,7 e 730 achados (era 41,99 e 727): +3 HIGH de Opengrep (2 `sentinel-node-desserializacao` e 1 `sentinel-node-eval-requisicao`), conferidos no código de treino.
- **Validação (só números, sem ler código):** dvja 176 achados, nota 48,95 (igual); NodeGoat 279, nota 46,87 (igual).
- NÃO verificado: treinos anteriores com 12.64.0; JUnit/Maven (só no PR).

## Dashboard 12.65.0 — Melhorias do laço (pedido do João): medição, gabarito e escopo fixo

- **Escopo fixo (decisão do João):** Java, Spring, JavaScript, TypeScript, React, Vue, Angular, HTML, SCSS e CSS. Nada de Python, PHP, Go. Registrado em `.claude/agents/sentinel-ciclo.md`, com a regra de alternar a stack entre voltas.
- **Medição automática:** `scripts/medir-volta.py` (`extrair`, `recall`, `comparar`) lê a lista completa de achados no log do job. `audit-rules/calibracao/gabaritos/` guarda as falhas conhecidas por repositório de treino; `baselines/` guarda as contagens por regra para detectar regressão (total +25 % ou regra que dobra). O critério de parada passa a ser ganho medido (recall que sobe ou falso positivo conferido que cai).
- **Primeiro gabarito (cr0hn/vulnerable-node, 29 falhas lidas no código e em `sast/ground-truth.yaml`):** recall 22/29 (76 %) com 12.64.0; 25/29 (86 %) com 12.65.0.
- **Regras novas (Node/EJS):** `sentinel-ejs-saida-sem-escape` (`<%- valor %>` em `*.ejs`, sem `body`, `include`, `partial`, `JSON.stringify`), `sentinel-node-mass-assignment` (corpo inteiro em `create/update/new Model`) e `sentinel-node-mass-assignment-campo` (papel/saldo/permissão recebendo `req.body.x`). Nas fontes do Juice Shop só `wallet.ts:27` (saldo vindo do corpo) acerta; uma versão com taint (descartada) gerava falso positivo em leituras de `balance`.
- **Medido (CI, vulnerable-node):** 265 → 268 achados, nota 47,61 → 47,44; +2 EJS e +1 mass assignment, todos conferidos.
- **Ainda perdidas (assumidas):** INSERT com `fields.role` em array de valores (`auth.js:55`) e três sementes que passam o dado por parâmetro de função (NoSQL, SSRF, ReDoS): limite do motor entre funções.
- **Decisões pendentes (não feitas por tocarem em `.github/` ou por exigirem avaliação):** (1) permitir lista de repositórios no workflow `auditoria-completa.yml` para rodar treino, validação e regressão em paralelo; (2) volta dedicada a avaliar o Joern para JS/TS (dado entre funções) antes de decidir se compensa.
- **Regressão de treinos anteriores:** ainda sem baseline (só vulnerable-node tem). Cada volta nova grava o baseline do repositório auditado; reauditar dois treinos antigos por mudança de regra passou a ser passo obrigatório do guia.

Verificado: `semgrep --test` (79/79), paridade Opengrep × Semgrep (624 = 624), auditoria do vulnerable-node antes e depois no CI, recall pelo script. NÃO verificado: JUnit/Maven (nada de Java mudou); Opengrep local 1.33.1 e não o 1.30.0 do CI; regras novas em NodeGoat e dvja (a conferir pelo CI na próxima volta).

## Dashboard 12.66.0 — Ciclo, volta 13 (branch `ciclo/estabilizacao-4`): BrightSec/brokencrystals (treino novo), decoradores do NestJS

- **Repositório novo:** BrightSec/brokencrystals (branch `stable`, MIXED, 209 estrelas, NestJS + front-end React de treino), escolhido em 2026-11-06; ninguém o tinha lido nem auditado.
- **Medido (antes):** nota 38,3 e 918 achados (CRITICAL 14, HIGH 184). Trivy 293 (CVE de dependência), Stylelint 230, Opengrep 192 (a maioria qualidade/CSS), Gitleaks 23.
- **Falso negativo (código lido, treino):** as regras Node só viam `req`/`request`/`event` como origem; no NestJS o dado entra por `@Query()`/`@Body()`/`@Param()`. Ficaram sem alerta: redirecionamento aberto (`@Redirect()` devolvendo `{ url }`), `eval` do corpo em `/process_numbers` (saiu só como `eval-dinamico`, MEDIUM) e gravação de arquivo no caminho de `@Query('path')`.
- **Mudança:** parâmetro com `@Query/@Body/@Param/@Headers` vira origem em `open-redirect`, `eval-requisicao`, `command-injection`, `path-traversal` e `ssrf`; `return { url }` em método `@Redirect()` é destino; `spawn/spawnSync/execFile/execFileSync` com o executável vindo da requisição entram como destino de `command-injection`. Testes novos (positivos, seguros e parâmetro sem decorador de origem).
- **Medido (CI, depois):** nota 38,26 e 921 achados (+3: open-redirect, eval-requisicao, path-traversal, todos conferidos no código). Recall do gabarito (`gabaritos/BrightSec__brokencrystals.json`, amostra de 7 falhas lidas): 4/7 → 6/7. Baseline gravado.
- **Não feito (lacunas assumidas):** `GET /spawn` (comando entra no controller e é usado em `AppService.launchCommand`, outro arquivo: limite do motor); SQL do `kid` do JWT (template string num arquivo, consulta em outro); LDAP injection; doT/SSTI; SQL e SSRF dentro de serviços Nest.
- **Regressão (números do CI):** vulnerable-node 268 achados, nota 47,44, recall 25/29: iguais ao baseline. dvja 176 (nota 48,95) e NodeGoat 279 (nota 46,87): iguais à volta 12.
- **Branch `ciclo/estabilizacao-4`:** 6 voltas (8 a 13), todas com mudança; PR aberto para main nesta volta.

Verificado: `opengrep --test` (79/79, Opengrep 1.30.0), paridade Opengrep × Semgrep (641 = 641), varredura do código de treino (3 achados novos, todos verdadeiros), auditorias no CI. NÃO verificado: JUnit/Maven (nada de Java mudou; só no CI do PR); treinos que não são vulnerable-node com a 12.66.0; taint entre arquivos de serviços Nest.
