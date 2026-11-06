# Ganho medido pelas voltas do ciclo do Sentinel (estado de referência: 2026-11-06)

Convenções: "LOG" = `/home/claude/sentinel/docs/evolucao/LOG.md` (a seção é citada pelo título; os números de linha são do arquivo atual). "Gabarito" = `/home/claude/sentinel/audit-rules/calibracao/gabaritos/`. "Baseline" = `/home/claude/sentinel/audit-rules/calibracao/baselines/`. Somente leitura: nada foi executado nem clonado. Percentuais que eu calculei estão marcados como "(calc.)". Não há URLs, porque todas as fontes são locais.

Observações sobre a numeração (importam para quem for citar):
- "Volta" tem dois usos no LOG. As "Volta 1 a 4" das linhas 5 a 70 são do placar antigo por eixo (2026-10-21) e não são do ciclo. As voltas do ciclo começam na entrada 12.54.0 (linha 672: voltas 1 a 3), seguem em 12.55.0 (4 e 5, na linha 683), 12.56.0 (6), 12.58.0 (7), 12.60.0 a 12.67.0 (8 a 13).
- A versão 12.66.0 não tem seção no LOG. Ela só aparece como "regras novas em 12.65.0 / medida na 12.66.0" no texto da volta 13 (linha 847). O cabeçalho de 12.65.0 é "Melhorias do laço", sem número de volta.
- O ciclo teve uma fase anterior às voltas numeradas: as "rodadas" 1 a 3 das regras Node (12.42.0 a 12.44.0, linhas 556 a 590), a "Rodada 1 (Juice Shop)" de 12.47.0 e a "rodada 2 (WebGoat)" de 12.51.0 e 12.52.0. Entram abaixo como contexto.
- Todo o ciclo ocorreu em 2 dias: voltas 1 a 3 em 2026-11-05 (commit 79cf1c2, #133), as demais em 2026-11-06 (`git log`, datas dos commits 78be8ed, 5dcbb25, a5290c6).

---

## 1. Tabela por volta: o que cada volta mediu antes e depois

### Takeaway
Das 13 voltas, 2 (voltas 4 e 5) não mudaram código, e 1 (volta 11) mudou código sem ganho de detecção. As outras 10 mudaram regras ou analisadores. Dessas, as voltas 1, 3 e 6 (mais o ajuste de ESLint da 7) reduziram achados por limpeza de ruído, e as voltas 2, 7 a 10, 12 e 13 acrescentaram regras de segurança que acharam 3 a 12 achados novos cada, sempre no próprio repositório de treino que as inspirou. Nenhuma volta mediu recall na validação nem precisão com amostra.

### Cited Findings

Tabela (antes → depois; "nota" é a nota geral da auditoria; "NÃO verif." resume o campo `NÃO verificado` do LOG):

| Volta | Repositório novo (treino) / stack | Nota antes → depois | Achados antes → depois | Mudança feita | Recall | NÃO verificado (resumo) |
|---|---|---|---|---|---|---|
| 1 | WebGoat (já era treino desde 2026-11-05; não é repositório novo) / JAVA | não informada | 2.741 → 2.363 | `src/it` fora do lint e das métricas; tamanhos comuns (8, 16, 32, 64, 255) deixam de ser número mágico no Checkstyle | sem gabarito | JUnit/Maven; medição do XSS em JSP |
| 2 | java-sec-code / JAVA (Spring) | não informada | 663 → 671 ("8 problemas reais a mais") | parâmetro de handler Spring sem anotação conta como entrada; 4 regras novas (`xstream-qualquer-tipo`, `ssti-modelo-variavel`, `fastjson-autotype`, `classloader-define-classe`); core-security 1.5.0 | sem gabarito | JUnit/Maven; medição do XSS em JSP (mesmo texto da volta 3) |
| 3 | SecurityShepherd / JAVA | não informada | 5.088 → 4.763 | `PathRole` reconhece `src/it` como teste; achado de qualidade em teste/pasta ignorada não entra (jscpd); `<%= bundle.getString(...) %>` deixa de ser XSS em JSP | sem gabarito | JUnit/Maven; medição do XSS em JSP |
| 4 | OWASP/wrongsecrets / JAVA | 31,55 (única medida) | 969 (única medida) | nenhuma ("Volta sem ganho 1 de 2") | n/a | nada além da leitura do log |
| 5 | Juice Shop reauditado (já treino) / JS | 47,65 (rodada 1, 12.47.0) → 47,7 | 4.193 → 1.958 | nenhuma nesta volta ("Volta sem ganho 2 de 2"); a queda vem das mudanças de 12.47 a 12.54 | n/a | nova medição de dvja/NodeGoat |
| 6 | whgojp/JavaSecLab / JAVA | 30,52 → 30,93 | 11.274 → 3.433 (Opengrep 8.929 → 1.843) | `ExternalTools.vendoredFiles`: exclui bibliotecas de terceiros no Opengrep por tamanho, pasta (`lib`, `third-party`), saída de build com hash e cabeçalho de licença (12.56.0); cabeçalho Apache ASF e janela de 1.200 caracteres (12.57.0) | sem gabarito | JUnit/Maven; JavaSecLab com 12.57.0; achados reais de Java do JavaSecLab "ainda não analisados um a um" |
| 7 | rewanthtammana/Damn-Vulnerable-Bank / JAVA + Node (Android) | 44,76 → (sem medição depois) | 591 → (sem medição depois) | `sentinel-android.yml` com 6 regras; `Sequelize.literal` em `sentinel-node-sql-injection`; ESLint deixa de aplicar `no-require-imports` em `.js/.cjs/.jsx` | sem gabarito | nova auditoria do DVB com 12.58.0; dvja/NodeGoat com 12.58.0 |
| 8 | dineshshetty/Android-InsecureBankv2 / JAVA | 57,16 → 53,56 | 293 → 296 | `sentinel-android-modo-mundo`, `sentinel-crypto-chave-fixa-no-codigo`, `sentinel-crypto-iv-fixo`; +3 HIGH novos e "verdadeiros" | sem gabarito | treinos anteriores com 12.60.0; JUnit/Maven |
| 9 | cr0hn/vulnerable-node / JS | 48,24 → 47,65 | 252 → 264 (+12, "todos conferidos no código") | 5 regras Node: `sql-concatenado`, `nosql-operador`, `prototype-pollution`, `regexp-da-requisicao`, `xss-refletido` | recall não medido na época (gabarito só nasce em 12.65.0) | recall em código Node que ninguém leu além dos treinos; treinos antigos; Opengrep local 1.33.1 e não o 1.30.0 do CI |
| 10 | skills/secure-code-game / MIXED (JS, Python, Go, C) | 52,05 → 48,45 | 898 → 906 (+8: 1 XXE, 1 upload, 6 `exec` dinâmico) | `sentinel-node-xxe` com `replaceEntities`; `req.file.originalname` como origem; regra nova `sentinel-node-exec-dinamico` (WARNING) | sem gabarito | nova auditoria de dvja/NodeGoat (depois medidas); JUnit/Maven |
| 11 | christophetd/log4shell-vulnerable-app / JAVA (Gradle) | 90,73 → 90,73 | 9 → 9 | `DependencyExtractor.fromGradle` lê a notação de mapa e a versão do plugin Spring Boot; sem ganho de detecção, "como esperado" | sem gabarito | SBOM do log4shell com a mudança; JUnit/Maven |
| 12 | OWASP/DVSA / JS (Lambda; Python fora do escopo) | 41,99 → 41,7 | 727 → 730 (+3 HIGH) | `event.body/headers/queryStringParameters/pathParameters` como origem em `sentinel-node-desserializacao` e `sentinel-node-eval-requisicao` | sem gabarito | Opengrep nesta volta (testado só com Semgrep 1.184); nova auditoria do DVSA e dvja/NodeGoat (depois medidas) |
| (12.65.0) | vulnerable-node reusado | 47,61 → 47,44 | 265 → 268 (+3: 2 EJS, 1 mass assignment) | `sentinel-ejs-saida-sem-escape`, `sentinel-node-mass-assignment`, `sentinel-node-mass-assignment-campo` | 22/29 (76%) com 12.64.0 → 25/29 (86%) com 12.65.0 | regras novas em NodeGoat e dvja; JUnit/Maven |
| 13 | BrightSec/brokencrystals / MIXED (NestJS + React) | 38,3 → 38,26 | 918 → 921 (+3) | `@Query/@Body/@Param/@Headers` viram origem em 5 regras; `return { url }` em `@Redirect()`; `spawn/execFile` como destino | 4/7 → 6/7 (amostra de 7, "não é o gabarito completo") | treinos que não são vulnerable-node com a 12.66.0; taint entre arquivos de serviços Nest |

Fontes de cada linha: LOG, seção 12.54.0 (linhas 672 a 681) para as voltas 1 a 3; seção "Ciclo, volta 4" (683 a 690); 12.55.0 (692 a 700); 12.56.0 e "Medições da volta 6 e 12.57.0" (702 a 719); 12.58.0 e "Validação da volta 7" (721 a 734); 12.60.0 e "Medições da volta 8" (744 a 759); 12.61.0 (761 a 773); 12.62.0 e "Medições da volta 10" (775 a 791); 12.63.0 (793 a 804); 12.64.0 e "Medições da volta 12" (806 a 821); 12.65.0 (823 a 834); 12.67.0 (836 a 847).

Pontos de atenção sobre a tabela:
- A volta 7 não registra nota/achados "depois" do repositório novo (Damn-Vulnerable-Bank): o campo "NÃO verificado" diz "nova auditoria do Damn-Vulnerable-Bank com 12.58.0". O ganho das regras Android é afirmado só por varredura local do código de treino ("as regras novas acham 7 pontos e 2 em `transfer.js`, nenhum falso positivo") — LOG, 12.58.0.
- A volta 9 foi a única de 8 a 13 que mediu antes/depois no CI e conferiu os +12 achados "no código" (LOG, 12.61.0): 6 SQL, 2 NoSQL, 2 prototype pollution, 1 RegExp, 1 XSS. A conferência foi feita pelo mesmo agente que escreveu as regras, no repositório que inspirou as regras.
- Números de achados de WebGoat não batem entre entradas: 12.51.0 diz "nota 23,38 e 3.884 achados" e depois 3.083 (12.52.0); a volta 1 (12.54.0) diz "2.741 → 2.363" para o mesmo repositório. O LOG não reconcilia (versão, escopo ou fonte da contagem diferentes). Tratar como não comparável.
- Contagem de achados entre voltas diferentes de repositórios diferentes não é comparável (cada volta mede um repositório novo).
- Pré-ciclo (contexto): Juice Shop 0 → 17 → 20 achados `sentinel-node-*` (12.42.0 e 12.43.0, LOG linhas 563 e 575; a rodada 2 corrigiu "15" para "17"); 12.47.0 mediu nota 47,65 e 4.193 achados no Juice Shop, 57% (2.379) de bibliotecas de terceiros (LOG linha 611).
- Redução por limpeza de ruído (calc., sobre números do LOG): volta 1 −378 achados (−13,8%); volta 3 −325 (−6,4%); volta 6 −7.841 (−69,5%) com nota +0,41; Juice Shop 4.193 → 1.958 (−53,3%) com nota +0,05 (47,65 → 47,7).
- Escopo declarado: o LOG da 12.65.0 fixa o escopo (Java, Spring, JS, TS, React, Vue, Angular, HTML, SCSS, CSS; "nada de Python, PHP, Go"), mas as voltas 7, 10 e 12 já tinham escolhido alvos com Python/Go/C ou Lambdas Python, e o próprio LOG registra Python "fora do escopo das regras" nesses casos.

### Inferences
- O ganho de detecção de segurança concentra-se em regras de Node/Express (voltas 2, 9, 10, 12, 13 e 12.65.0), Android (7, 8) e criptografia fixa (8). Só a volta 2 mexe em Java/Spring de segurança.
- A volta 11 confirma que "repositório novo" não garante regra nova: o gargalo foi o desenho do pipeline (Trivy substitui o OWASPDependencyAnalyzer; Gradle sem lockfile), tratado como "decisão futura".

### Gaps
- Nota e contagem de achados "antes/depois" das voltas 1 a 3: o LOG só informa contagens totais (sem nota, sem repositório de validação com número).
- Nenhuma das voltas 1 a 3, 6 e 7 tem gabarito, então recall não existe para elas.
- Não encontrei, no LOG, número de recall em repositório de validação para nenhuma volta.

---

## 2. Validação (dvja, NodeGoat e demais): série de contagens e notas

### Takeaway
A série de validação é plana: dvja ficou em 176 achados e nota 48,95 de ponta a ponta, e NodeGoat teve duas mudanças pequenas (328 → 278 por ESLint; 278 → 279 por uma regra nova de `exec`). Nenhuma mudança de segurança confirmada: a única queda é ruído de lint, e o único +1 não teve falso positivo verificado. As regras de segurança escritas nas voltas 9, 12, 13 e 12.65.0 não acharam nada nos repositórios de validação.

### Cited Findings
Série (LOG, trechos de validação de cada volta):

| Ponto | dvja (achados / nota) | NodeGoat (achados / nota) | Fonte |
|---|---|---|---|
| Voltas 2 (java-sec-code) | 176 | 328 | LOG 12.54.0, linha 677 ("dvja e NodeGoat inalterados (176 e 328)") |
| Volta 6 (12.56.0/12.57.0) | 176 / 48,95 | 328 / 46,86 | LOG linha 717 |
| Volta 7 (12.58.0) | 176 / 48,95 | 278 / 47,14 | LOG linha 733 |
| Volta 8 (12.60.0) | 176 / 48,95 | 278 / 47,14 | LOG linha 757 |
| Volta 9 (12.61.0) | 176 / 48,95 | 278 / 47,14 | LOG linha 769 |
| Volta 10 (12.62.0) | 176 / 48,95 | 279 / 46,87 | LOG linha 789 |
| Volta 11 (12.63.0) | 176 / 48,95 | 279 / 46,87 | LOG linha 801 |
| Volta 12 (12.64.0) | 176 / 48,95 | 279 / 46,87 | LOG linha 820 |
| Volta 13 (12.67.0) | 176 / 48,95 | 279 / 46,87 | LOG linha 844 |

- A queda NodeGoat 328 → 278 (−50) na volta 7 "vem do ESLint (`no-require-imports` em CommonJS), não de regra de segurança. Sem leitura do código para confirmar que nenhum achado de segurança sumiu" — LOG 12.58.0, "Validação da volta 7". A mudança que a causou foi desligar `no-require-imports` para `.js/.cjs/.jsx` (LOG 12.58.0, linha 726). A nota subiu só 0,28 (46,86 → 47,14).
- A queda de 50 achados é, no repositório de treino da mesma volta, o mesmo tipo de ruído apontado como "129 `no-require-imports` do ESLint" no Damn-Vulnerable-Bank (LOG linha 725). Ou seja, foi uma correção de falso positivo de lint motivada pelo treino, e a validação só confirmou que ela vale em outro repositório CommonJS (efeito real, mas de qualidade, não de segurança).
- NodeGoat +1 na volta 10 (278 → 279, nota 47,14 → 46,87): "um `sentinel-node-exec-dinamico` MEDIUM em `Gruntfile.js:165`; não li o arquivo, então não sei se é falso positivo (arquivo de build, WARNING)" — LOG 12.62.0, "Medições da volta 10". A nota caiu 0,27 por este único achado.
- As regras de segurança novas não acharam nada nos repositórios de validação: "as regras novas não acharam nada no NodeGoat (não dá para dizer, sem ler o código, se faltou ou não havia o que achar)" — LOG 12.61.0, linha 769. Nas voltas 12 e 13 o LOG apenas informa "iguais" (linhas 820 e 844).
- Pré-ciclo: validação das 15 regras `sentinel-node-*`: NodeGoat 4, DVNA 4, Goof 2 achados (só contagens, sem conferir falso positivo): LOG 12.42.0 (linha 564), 12.43.0 (linha 575: "a rodada não mostrou ganho fora do treino") e 12.44.0 (linha 584).
- O Sentinel cita que o segundo critério de cada volta é a validação, mas "treinos anteriores não reauditados" aparece em quase todas as voltas (LOG linhas 717, 757, 759, 772, 791, 821). A regressão sobre treinos antigos só passou a ser "passo obrigatório" em 12.65.0 (LOG linha 832).
- Regressão com baseline: só existem 2 baselines (vulnerable-node e brokencrystals). Na volta 13: vulnerable-node 268 achados, nota 47,44, recall 25/29 "iguais ao baseline" (LOG linha 844). Nenhum dos outros 8 treinos tem baseline.
- OWASP Benchmark, que é a medição independente de Java: PLACAR.md linha 16 informa média 0,44 (treino 0,48, validação 0,42) e LOG 12.42.0 (linha 560) informa 0,62 treino, 0,44 validação, 0,53 total em medição local. As voltas 1 a 13 só mexeram em Java de forma marginal e nenhuma entrada registra nova medição do Benchmark. A exigência de rodá-lo só chega no commit ef8f6c5 (`.claude/agents/sentinel-ciclo.md`, "Prova de generalização", itens 1 a 3), depois das 13 voltas.

### Inferences
- Pelo critério do próprio projeto ("validação só com números", `conjuntos.json`), a série da validação mostra: nenhuma regressão de contagem e nenhum ganho de segurança mensurável. Isso é compatível com "regra boa que não se aplica ao repositório de validação" e com "regra sobreajustada" — os dados não distinguem os dois, porque falta gabarito de validação.
- O "ganho" medido na validação é um ruído de lint corrigido (−50 em NodeGoat), não segurança. Ele é real como redução de falso positivo de qualidade, mas não deveria entrar na conta de ganho de detecção.

### Gaps
- Não há verdade-terreno (gabarito) para dvja, NodeGoat, dvna ou Goof; não sei se as regras deixaram de achar algo que existe. O diretório `gabaritos/validacao/` só tem `LEIAME.json` com `"falhas": []` e `"repo": "(preencher)"` (commit ef8f6c5, 2026-11-06).
- Contagens de dvna e Goof não aparecem em nenhuma volta (só nos pré-ciclo).
- Não sei se o +1 de NodeGoat (`Gruntfile.js:165`) é falso positivo: o LOG diz que não leu.

---

## 3. Gabaritos e baselines: quantas falhas, quem escreveu, risco de sobreajuste

### Takeaway
Existem só 2 gabaritos (36 falhas no total: 29 em vulnerable-node e 7 em brokencrystals) e 2 baselines, todos escritos pelo mesmo agente que escreveu as regras, a partir do código lido no repositório de treino. O recall é medido no mesmo código que inspirou as regras, então é um indicador de "a regra pega o que foi escrito para pegar", não de generalização. Há evidências concretas de regras desenhadas em torno de padrões do próprio repositório.

### Cited Findings
Contagens (calculadas por mim com `python3` sobre os JSON):
- `gabaritos/cr0hn__vulnerable-node.json`: 29 falhas, 12 delas "sementes do próprio repositório (sast/ground-truth.yaml)" e 17 lidas no código; 0 entradas sem regra no arquivo (os 4 itens "perdidos" aparecem porque nenhuma regra os alcança hoje, mas a lista de `regras` aceitas não está vazia).
- `gabaritos/BrightSec__brokencrystals.json`: 7 falhas, amostra declarada ("amostra de 7, não é o gabarito completo"); 2 entradas com `"regras": []` (`GET /spawn` e SQL do `kid` do JWT), que são "lacunas assumidas".
- `baselines/cr0hn__vulnerable-node.json`: nota 47,44, total 268, 67 chaves em `porRegra`. `baselines/BrightSec__brokencrystals.json`: nota 38,26, total 921, 294 chaves em `porRegra`, severidades CRITICAL 14, HIGH 186, MEDIUM 231, LOW 476, INFO 14. Baseline é contagem por regra para detectar regressão, não gabarito (`scripts/medir-volta.py`, docstring: "falha se uma regra dobra ou o total cresce mais de 25%").
- `gabaritos/validacao/LEIAME.json`: vazio (`"falhas": []`).

Autoria:
- Os dois gabaritos e os dois baselines entraram no commit a5290c6 (2026-11-06, #140), cuja conta de autor é `joaopaulosistemas` e cujo trailer é `Co-Authored-By: Claude Sonnet 5.5` (`git log`). O LOG descreve a criação como feita pelo agente: "29 falhas lidas no código e em `sast/ground-truth.yaml`" (LOG 12.65.0, linha 827); o `_comentario` do JSON diz "lidas no código (treino)". Conclusão: mesmo agente que escreveu as regras, na mesma sessão de trabalho. `git log` mostra só dois autores de commit: `joaopaulosistemas` (51 commits) e `Claude` (2), sem outro agente independente.
- A entrada do gabarito do brokencrystals na linha 608 de `users.controller.ts` diz "achado do Sentinel nesta linha; rota não conferida um a um": ou seja, uma falha do gabarito foi tirada da saída do próprio Sentinel, o que torna o acerto circular.
- A regra de `scripts/medir-volta.py recall` considera acerto se o achado está no arquivo (sufixo) e, quando há linha, a ±3 linhas por padrão, e se o `ruleId` termina em uma das regras aceitas (linhas 35 a 49 do script). O gabarito lista as regras aceitas, escritas pelo mesmo agente: ele pode escolher que regras valem.

Recall é medido no mesmo código que inspirou as regras:
- vulnerable-node: a volta 9 escreveu as 5 regras a partir desse repositório e depois o gabarito (12.65.0) foi montado com o mesmo código. Contagem minha sobre as listas de `regras` do gabarito: 9 das 29 entradas só podem ser acertadas por regras criadas nas voltas 9 e 12.65.0 (`prototype-pollution`, `regexp-da-requisicao`, `ejs-saida-sem-escape` ×2, `mass-assignment*` ×2, e as sementes de XSS, ReDoS e prototype pollution); outras 7 aceitam regra antiga ou nova (SQL ×3 + semente, NoSQL ×2 + semente), e o LOG da volta 9 diz que o SQL de `model/db.js` era "falso negativo real" antes da regra nova (linha 765). Logo, ao menos 9 (e provavelmente 16) das 29 entradas dependem de regras escritas olhando este repositório.
- O recall 22/29 → 25/29 (LOG 12.65.0, linha 827) mede, portanto, a diferença entre duas versões de regras escritas para este repositório. Os +3 acertos são EJS ×2 e mass assignment ×1 (LOG linha 829), regras criadas na mesma volta e no mesmo código.
- brokencrystals: recall 4/7 → 6/7 (LOG linha 842) mede 3 achados novos (open-redirect, eval-requisicao, path-traversal) escritos a partir do mesmo repositório. Aviso de coerência: o LOG informa +3 achados verdadeiros mas o recall sobe só +2 (4 → 6); o LOG não explica a diferença.
- O próprio projeto reconhece o limite: "Recall do treino mede só o que as regras foram escritas para achar" (`.claude/agents/sentinel-ciclo.md`, seção "Prova de generalização", commit ef8f6c5, escrito depois das 13 voltas).

Evidências de sobreajuste (regras desenhadas em torno de um ponto do repositório de treino):
- `sentinel-node-mass-assignment-campo` filtra o nome do campo por `^(role|roles|isAdmin|is_admin|admin|permissions|balance|saldo|isStaff|is_staff)$` (`audit-rules/packs/core-security/rules/sentinel-node.yml`, linhas 838 a 840). O `balance` e o `saldo` vêm de `wallet.ts:27` no Juice Shop e da loja do vulnerable-node. O LOG descreve que "uma versão com taint (descartada) gerava falso positivo em leituras de `balance`" (LOG 12.65.0, linha 828): a regra final foi estreitada por lista de nomes depois de falhar num caso do próprio treino.
- Os testes da regra de SQL usam `Sequelize.literal(\`balance - ${valor}\`)` (`tests/sentinel-node.ts`, linhas 33 e 39), do Damn-Vulnerable-Bank (`Balance/transfer.js`, LOG linha 725).
- `sentinel-node-exec-dinamico` foi criada na volta 10 para uma falha de `Season-2/Level-3/code.js` e produziu 5 achados `prodbot.js` de "baixo risco" (LOG linha 781), que viraram +8 achados e queda de nota de 3,6 pontos no próprio treino.
- `sentinel-node-path-traversal` ganha `req.file.originalname` (`sentinel-node.yml` linha 239) por causa de um arquivo do secure-code-game; regras de origem `event` (Lambda) nascem do DVSA; decoradores `@Query/@Body` nascem do brokencrystals. Cada origem nova foi escrita a partir de 1 repositório e aplicada a 2 a 5 regras (LOG linhas 811 e 841).
- As regras Android (volta 7) "nos arquivos do próprio repositório de treino acham 7 pontos e 2 em `transfer.js`, nenhum falso positivo" (LOG linha 726): a verificação é a varredura do mesmo código que as originou.
- Como contrapeso: o LOG registra duas regras novas acertando fora do repositório que as inspirou: "nas fontes do Juice Shop (treino) as regras novas acham 2 pontos (`likeProductReviews.ts:25,43`), ambos verdadeiros" (volta 9) e `wallet.ts:27` (12.65.0). Mas o Juice Shop é treino lido pelo mesmo agente em 2026-11-05 (conjuntos.json, `_nota_juice_shop`), então não é validação cega.

### Inferences
- O recall de 76% → 86% e 4/7 → 6/7 é recall de treino. Dada a autoria, a regra de aceitação (`regras` escolhidas pelo mesmo agente) e as 7 entradas deixadas como "lacuna assumida", ele é, no máximo, um teste de regressão ("a regra continua pegando o que pegava"), não prova de ganho de detecção em código novo.
- O mecanismo de sobreajuste é estrutural, não deliberado: cada volta lê um repositório, escreve regras para as falhas lidas, e mede o mesmo repositório. As regras são gerais em forma (padrões de CWE) mas calibradas por nomes de variáveis e de campos vistos naquele código.
- Os dois "baselines" não podem contar como validação: medem só estabilidade de contagem.

### Gaps
- Não consegui verificar se as 7 entradas do gabarito do brokencrystals foram escolhidas antes ou depois da regra (o LOG diz "lidas no código", sem ordem temporal).
- Não há gabarito independente (de outro agente, de pessoa, ou de fonte externa como OWASP Benchmark) para nenhum repositório de Node, exceto a parte "sementes" do vulnerable-node (12 entradas), cujo gabarito vem do `sast/ground-truth.yaml` do repositório (que eu não li, pois não clonei).
- Não verifiquei se `revisao-humana.md` tem veredito humano: `precisao.md` (cabeçalho) diz "0 de 60 linhas" preenchidas até 2026-10-28.

---

## 4. A nota (score) é bom indicador de ganho?

### Takeaway
Não. Nas voltas com ganho de detecção a nota caiu ou ficou igual (a auditoria "passa a ver falhas reais"); nas voltas com enorme redução de achados a nota quase não mexeu. A nota respondeu mais à contagem/severidade do que à correção.

### Cited Findings
Melhorar a detecção piorou a nota (todos no próprio treino):
- Volta 8, InsecureBankv2: +3 HIGH verdadeiros, nota 57,16 → 53,56 (−3,60), 293 → 296 achados (LOG 12.60.0, linha 756: "A nota cai porque a auditoria passou a ver falhas reais").
- Volta 9, vulnerable-node: +12 achados verdadeiros, nota 48,24 → 47,65 (−0,59) (LOG linha 767).
- Volta 10, secure-code-game: +8 achados, nota 52,05 → 48,45 (−3,60). Seis dos 8 são `exec-dinamico` de severidade WARNING e "baixo risco" (LOG linhas 781 e 788).
- Volta 12, DVSA: +3 HIGH, nota 41,99 → 41,7 (−0,29) (LOG linha 819).
- 12.65.0, vulnerable-node: +3 achados, nota 47,61 → 47,44 (−0,17) (LOG linha 829).
- Volta 13, brokencrystals: +3, nota 38,3 → 38,26 (−0,04) (LOG linha 842).
- Em validação, o +1 achado de NodeGoat custou 0,27 de nota (47,14 → 46,87), LOG linha 789.

Melhorar (reduzir ruído) e a nota quase igual:
- Volta 6: 11.274 → 3.433 achados (−69,5% calc.), nota 30,52 → 30,93 (+0,41). Explicação do LOG: "a nota quase não mexe porque segurança já estava em ~0,8" (linha 715).
- Juice Shop: 4.193 → 1.958 achados, nota 47,65 → 47,7, "a nota quase não mexe porque a dimensão segurança já está em 0" (LOG linha 694).
- NodeGoat: −50 achados de ESLint, nota +0,28 (46,86 → 47,14).
- `docs/evolucao/calibracao.md`, "Terceira rodada": em petclinic, 1.498 → 290 achados e nota 42,6 → 42,6 ("a nota ficou em 42,6 porque o cálculo já limita a penalidade de severidade baixa por regra (20 pontos)"). Antecedente do mesmo efeito, anterior ao ciclo.
- Volta 11: detecção igual e nota igual (90,73), mas a dimensão "dependências" ficou em 100, "o que é enganoso" por falso negativo de CVE (LOG linha 797): a nota alta escondia a lacuna.
- Volta 4 e volta 5 não mexeram em nada, e a nota não é usada como critério de parada: `.claude/agents/sentinel-ciclo.md` linha 51 define ganho como "recall do gabarito que sobe ou falso positivo conferido que cai".
- O placar por eixo (`PLACAR.md`) também não se moveu: "Hoje: 2,0 (todos os eixos em 2; nenhum em 3 ainda)" (linha 4). Nenhuma das 13 voltas elevou um eixo.

### Inferences
- A nota é uma função decrescente da contagem ponderada por severidade, limitada por regra. Por isso ela funciona como indicador de "estado do repositório auditado", e não como indicador de qualidade do Sentinel. A direção "nota cai quando o Sentinel melhora" é esperada nos treinos e é a leitura do próprio LOG.
- Para medir ganho do Sentinel, os critérios que sobram são recall (viciado, ver item 3), precisão por amostra (nunca medida no ciclo) e OWASP Benchmark (não reexecutado).

### Gaps
- Não li o código do cálculo de nota; as relações acima vêm só do LOG.

---

## 5. Ganhos sólidos e ganhos frágeis

### Takeaway
Sólidos: filtros de ruído de bibliotecas de terceiros (reproduzidos em 3 repositórios e com testes `javac`), correção do ESLint em CommonJS (confirmada em outro repositório) e as regras de origem/padrão que acharam pontos verdadeiros em repositório de treino diferente do que as inspirou. Frágeis: tudo o que só foi medido no repositório que inspirou a regra, regras de baixo valor/WARNING que inflaram achados, e itens com NÃO verificado crítico.

### Cited Findings
Sólidos (evidência além do repositório inspirador, ou testes):
- Detecção de bibliotecas de terceiros (`ExternalTools.isVendoredLibrary`, `hasThirdPartyBanner`, `vendoredFiles`; 12.47 a 12.57): atua por tamanho, pasta e cabeçalho (não por lista de arquivos; "Não há lista de arquivos do Juice Shop", LOG linha 612) e reduziu achados em 3 repositórios diferentes (WebGoat, Juice Shop, JavaSecLab). Verificado só por programas `javac`, sem JUnit (LOG: "NÃO verificado: JUnit/Maven"); mesmo assim vale como regra geral. Risco: "código próprio gerado e muito grande deixa de ser analisado" (linha 613) e `lib` sob `static` "conta como terceiros mesmo se o projeto guardar código próprio ali" (linha 708).
- `no-require-imports` em `.js/.cjs/.jsx` desligado: motivado pelo Damn-Vulnerable-Bank e confirmado em NodeGoat (−50). Efeito de qualidade, não de segurança.
- Regras de CWE com origem em padrões genéricos e achados verdadeiros conferidos: Juice Shop (treino diferente do inspirador) recebeu 2 acertos de `nosql-operador` (`likeProductReviews.ts:25,43`) e 1 de `mass-assignment-campo` (`wallet.ts:27`) (LOG linhas 768 e 828).
- 79/79 testes `opengrep --test` com positivo e negativo por regra e paridade Opengrep × Semgrep (641 = 641) na volta 13 (LOG linha 847).
- Terraform fora do Trivy (12.59.0) é decisão de escopo, aprovada pelo João, não ganho de detecção.

Frágeis:
- Todos os "+N achados verdadeiros" das voltas 8, 9, 10, 12 e 13: conferidos pelo mesmo agente, no mesmo repositório que inspirou a regra (3 a 12 achados por volta).
- Voltas 2 e 7: não há "depois" no CI para a volta 7; na volta 2, "8 problemas reais a mais" sem lista.
- Regra `sentinel-node-exec-dinamico` (volta 10): 6 achados no treino e 1 no NodeGoat (validação) em `Gruntfile.js:165`, sem leitura do arquivo; é severidade WARNING e apareceu em arquivo de build.
- O ESLint do JavaSecLab foi de 27 a 493 depois da volta 6 "sem causa conclusiva" (LOG linha 715): a mudança de exclusão do Opengrep pode ter deslocado achados para outra ferramenta, ou o ESLint ficou mais ruidoso; o LOG não resolve.
- Regras dependentes de nomes: `mass-assignment-campo` (lista de nomes), `crypto-chave-fixa-no-codigo` (campo `String` de nome key/chave/secret/segredo com literal; LOG linha 749), `Sequelize.literal`.
- Limites do motor assumidos, sem ganho: SQL/NoSQL/SSRF/ReDoS que passam o dado por parâmetro de função (LOG linhas 770, 830, 843). O LOG repete que o Opengrep não segue dado entre arquivos e deixa o Joern para JS/TS como "volta dedicada" pendente (linha 831).
- Precisão: nenhuma volta atualizou `precisao.md`; a última rodada de precisão com 450 achados e 22 repositórios é de 2026-10-28, antes do ciclo (cabeçalho de `precisao.md`).

### Inferences
- Ganho sólido ≈ redução de ruído geral e plumbing (escopo e vendored libs); ganho de segurança do ciclo é plausível mas não demonstrado fora do treino.

### Gaps
- Não há contagem de falsos positivos conferidos por volta (o LOG diz "nenhum falso positivo" ou "nenhum novo" para o repositório de treino, sem amostra).
- Sem JUnit/Maven rodado nas voltas: nenhuma entrada do LOG contém resultado de build Java local; só do CI do PR #140, cujo resultado não li.

---

## 6. Voltas que terminaram com "nada a corrigir" e saturação

### Takeaway
Foram 2 de 13 (voltas 4 e 5, nas duas o LOG escreve "Mudança de código: nenhuma. Volta sem ganho"), mais a volta 11, que mudou só plumbing sem ganho de detecção. Depois da volta 5, as 8 voltas seguintes (6 a 13) mudaram regra ou analisador. Não há sinal de saturação: cada repositório novo revelou lacunas novas, o que é típico de cobertura construída caso a caso.

### Cited Findings
- Volta 4 (wrongsecrets): "Nenhuma lacuna geral encontrada... Mudança de código: nenhuma. Volta sem ganho (conta para a estabilização: 1 de 2)" — LOG linhas 686 e 687.
- Volta 5 (Juice Shop reauditado): "Mudança de código: nenhuma. Volta sem ganho (2 de 2 seguidas)... o laço está estável" — LOG linha 696. O critério de parada do agente (duas sem ganho) foi atingido aqui, em `sentinel-ciclo.md` linha 51 (e `AGENTE.md`, "Para após duas rodadas seguidas sem ganho medido").
- Depois disso o laço continuou em novas branches (`ciclo/estabilizacao-3`, `-4`): voltas 6 a 13, todas com mudança, até o PR #140. O LOG da volta 7 dá o critério de fechamento do PR: "fecha com 6 voltas ou 2 sem mudança" (linha 734).
- Os dois "sem ganho" foram em repositórios onde o escopo (Terraform) era o problema dominante: 112 achados de Terraform (wrongsecrets) e ~34 CRITICAL no Juice Shop; a decisão pendente da volta 5 virou a 12.59.0 (Terraform fora do Trivy) depois da aprovação do João (LOG linhas 688, 697, 738).
- A volta 4 só concluiu que o Sentinel já cobria as falhas do alvo porque as falhas eram segredos (Gitleaks, Trivy `DS-0031`, regra de senha em properties); a análise do Java dos desafios não achou "lacuna geral" (linha 686).
- A volta 11 (log4shell) é a mais próxima de um terceiro "sem ganho": nota e achados idênticos (90,73 e 9), com o principal falso negativo (CVE do Log4j via dependência transitiva de Gradle) deixado "sem correção" (linha 797) e a mudança de 18 linhas de `DependencyExtractor` com efeito só em SBOM (linha 798).
- Alvos: 13 voltas, 13 repositórios distintos (WebGoat, java-sec-code, SecurityShepherd, wrongsecrets, Juice Shop, JavaSecLab, DVB, InsecureBankv2, vulnerable-node, secure-code-game, log4shell, DVSA, brokencrystals).
- vulnerable-node recebeu 2 passagens de regra (volta 9 e 12.65.0) e foi usado como "regressão" na volta 13. O guia da volta (`sentinel-ciclo.md` item 5b) manda reauditar "pelo menos dois treinos anteriores" depois de mudar regra, mas só 2 baselines existem e o LOG da 13 só reauditou vulnerable-node + dvja + NodeGoat.

### Inferences
- A saturação aparente nas voltas 4 e 5 vem de escolher repositórios cujas falhas já estavam cobertas ou fora de escopo (segredos e Terraform), não de o Sentinel ter esgotado as lacunas.
- Nas 8 voltas seguintes o ritmo de "nova lacuna por repositório novo" não diminuiu (3 a 12 achados novos por volta); isso indica que o conjunto de regras está longe de saturar em Node, e que o critério de parada "duas sem ganho" só dispara quando o alvo é mal escolhido.
- Um sinal contrário à saturação, mas também ao ganho de generalização: quanto mais regras nascem de um repositório novo, mais o recall precisa ser medido em código que ninguém leu (e isso ainda não existe).

### Gaps
- O LOG não tem o PR #140 com a contagem de testes Java do CI; não li o CI.
- Não encontrei motivo para a volta 11 não ser contada como "sem ganho" (o texto fala em "Mudança (pequena, geral)").

---

## Resumo para o redator (síntese transversal)

1. Ganho real e reutilizável: redução de ruído de bibliotecas embutidas (voltas 1, 3, 6 e o ESLint da 7), confirmada por contagem em 3 treinos e em 1 validação (NodeGoat −50).
2. Ganho de detecção de segurança: existe no treino (+3 a +12 achados "verdadeiros" por volta, recall 76% → 86% em vulnerable-node, 4/7 → 6/7 em brokencrystals), mas é medido no mesmo repositório que inspirou as regras, por gabarito escrito pelo mesmo agente. Ganho em validação: zero medido (dvja 176 sempre; NodeGoat 328 → 279 sem achado de segurança verdadeiro novo confirmado).
3. Nota: piora quando a detecção melhora e não responde a limpeza de ruído; não serve como critério. O placar por eixo não subiu (2,0).
4. Voltas sem mudança: 2 (4 e 5); saturação não demonstrada.
5. O projeto reconheceu a lacuna depois da volta 13: commit ef8f6c5 (2026-11-06) exige recall na validação, tendência do Benchmark e amostra de falso positivo a cada 3 voltas, e criou `gabaritos/validacao/LEIAME.json` vazio. Ou seja, no fim das 13 voltas, nenhum desses três números existe.

Fatos de memória do usuário: nenhum usado.
