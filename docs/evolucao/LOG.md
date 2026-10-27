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
