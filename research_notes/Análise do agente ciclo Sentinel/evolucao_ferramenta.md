# Evolução da ferramenta Sentinel causada pelo ciclo (regras e analisadores)

Estado de referência: 2026-11-06. Repositório `/home/claude/sentinel`, somente leitura. Todas as fontes abaixo são caminhos locais ou comandos git; não há URLs.

**Definições usadas (importante para quem escrever o relatório)**
- Início do ciclo = commit `64833dc` "feat: agente Sentinel Ciclo e auditoria completa; remove Evolution e Descoberta (12.45.0) (#124)", 2026-11-05. Linha de base = pai dele, `432d657` (12.44.0). `git log 432d657..origin/main` = 18 commits.
- "Main" = `origin/main` = `a5290c6` ("Ciclo de estabilização 4: voltas 8 a 13 (12.67.0) (#140)"). O branch local `main` está desatualizado (`79cf1c2`). O HEAD atual (`docs/ciclo-medicao`, `ef8f6c5`) é `origin/main` + 1 commit só de guia/gabarito vazio, ainda não mergeado.
- Ambiguidade de nomenclatura: o `docs/evolucao/LOG.md` tem "Volta 1 a 4" de 2026-10-21 (era do agente antigo Evolution, antes do Ciclo) e "Ciclo, volta 4 a 13" (12.51 a 12.67). A numeração de voltas do ciclo começa de fato em 12.47.0 ("Rodada 1 (Juice Shop)"). Usei o commit `64833dc` como marco.
- As 15 regras `sentinel-node-*` (12.42 a 12.44, `df03f00`/`aacd3ba`/`2c5608f`/`432d657`) e as regras do WebGoat são ANTERIORES ao ciclo, mesmo tendo sido feitas no mesmo dia 2026-11-05 (`git show 432d657:audit-rules/packs/core-security/rules/sentinel-node.yml | grep -c '^  - id'` = 15). O ciclo adicionou 8 regras Node.

---

## 1. Inventário: quanto o ciclo mudou e quantas regras o Sentinel tem hoje

### Takeaway
O Sentinel tem hoje 277 regras (ids) em 16 pacotes; o ciclo acrescentou 22 (8,6 % sobre as 255 da linha de base), todas no pacote `core-security`, e alterou 25 existentes, 10 delas só em `metadata` (lado/side). A maior parte do volume do ciclo (e das linhas de código) está fora das regras: heurísticas de bibliotecas de terceiros, escopo back/front e UI do Joern.

### Cited Findings
- Regras (ids em `audit-rules/packs/*/rules/*.yml`, parse YAML em `432d657` vs `origin/main`): 255 → 277; 22 novas, 0 removidas, 25 com corpo alterado. Todas as 22 novas estão em `core-security` (nenhuma em core-spring, core-frontend, core-vue, core-quality, core-iac...) — script de comparação reproduzível com `git show <rev>:<arquivo>` + `yaml.safe_load`.
- As 22 novas, por arquivo: `sentinel-android.yml` 7 (log-segredo, webview-url-do-intent, webview-javascript, manifest-allow-backup, manifest-cleartext, manifest-exported, modo-mundo); `sentinel-crypto.yml` 2 (chave-fixa-no-codigo, iv-fixo); `sentinel-ejs.yml` 1 (ejs-saida-sem-escape); `sentinel-java-extras.yml` 4 (xstream-qualquer-tipo, ssti-modelo-variavel, fastjson-autotype, classloader-define-classe); `sentinel-node.yml` 8 (exec-dinamico, sql-concatenado, nosql-operador, prototype-pollution, regexp-da-requisicao, xss-refletido, mass-assignment, mass-assignment-campo). Fonte: `docs/evolucao/LOG.md` seções 12.54.0, 12.58.0, 12.60.0, 12.61.0, 12.62.0, 12.65.0.
- Por stack: 9 de 22 (41 %) são Android/mobile (7 Android + 2 crypto de chave/IV fixos nascidas do InsecureBankv2); 8 Node/Express; 4 bibliotecas Java (XStream, Velocity/FreeMarker, Fastjson, ClassLoader, vindas de java-sec-code); 1 EJS. Nenhuma nova regra para Spring, React, Vue, Angular, HTML ou CSS.
- Regras alteradas (25): 10 só com `metadata` (`side`, 12.48.0, `RuleSide`); 15 com mudança de lógica: 5 regras Java com a mesma fonte de taint copiada ("parâmetro `String` de método com `@Get/Post/Put/Delete/Patch/RequestMapping`" em `sentinel-command-injection-requisicao`, `sentinel-api-ssrf`, `sentinel-api-path-traversal`, `sentinel-servlet-path-traversal`, `sentinel-servlet-trust-boundary`); `sentinel-xss-jsp-unescaped-output` (exclui bundle/Encode); `sentinel-boas-java-recurso-sem-try-with-resources` (aceita 2 a 4 recursos); e 8 regras Node que ganharam origem `event` (Lambda), `@Query/@Body/@Param/@Headers` (NestJS), `req.file.originalname`, `Sequelize.literal`, `replaceEntities: true` (`sentinel-node-eval-requisicao`, `-open-redirect`, `-command-injection`, `-path-traversal`, `-ssrf`, `-desserializacao`, `-xxe`, `-sql-injection`; mais `sentinel-command-injection-requisicao`, já contada acima).
- Casos de teste de regras: `ruleid:` 378 → 432 (+54) e `ok:` 365 → 413 (+48) em `audit-rules/packs/*/tests/` (contagem por `git show` em cada revisão; as 22 regras novas contribuem com 54 `ruleid` e 46 `ok`). `opengrep --test` local (Opengrep 1.30.0, `LC_ALL=C.UTF-8 PYTHONUTF8=1`): core-security 79/79; core-boas-praticas 8/8, core-frontend 29/29, core-iac 11/11, core-persistencia 11/11, core-quality 41/41, core-quarkus 7/7, core-spring 15/15, core-vue 8/8, java-cert-sonar 11/11, ts-clean-code 7/7, ts-clean-code-labs42 21/21; core-corporativo 8/13 porque as 5 regras `*-sql.yml` não são avaliadas por `--test` e têm verificador próprio (`audit-rules/scripts/testar-regex-sql.py`, `.github/workflows/ci.yml` linhas ~105-115).
- Remediação: `core-security/remediation/remediation.yml` 59 → 80 entradas (+21), +163 linhas. A resolução é por substring do id da regra, a chave mais longa vence (`audit-api/src/main/java/com/sentinel/audit/pack/RemediationCatalog.java`, `forFinding`). 21 das 22 regras novas têm entrada própria; falta `sentinel-android-webview-javascript` (verificado por script: nenhuma chave casa).
- Versões: `core-security` 1.4.0 → 1.5.0 (`pack.yml`); boas-praticas 1.0.1; spring/persistencia 1.0.2. O `description` do `pack.yml` de core-security não menciona Android, crypto de chave fixa nem EJS, e `languages: [java, javascript, typescript]` (as regras Android são `java` + `generic` XML).
- Código (git diff `432d657..origin/main --numstat`): 115 arquivos, +4.622/-1.056. `audit-api/src/main` +568/-47 (20 arquivos); `audit-api/src/test` +501/-1 (10 arquivos); `audit-dashboard` +569/-82 (15 arquivos); regras YAML +672/-2 (13 arquivos); testes das regras +514 (10 arquivos); `docs` +538/-100; `audit-rules/calibracao` +738; `.github` +73/-462 (remoção dos workflows Evolution/Descoberta); `scripts` +192/-120.
- Mudanças de analisador/Java (todas no `LOG.md`): `ExternalTools` (181 → 361 linhas) com `isVendoredLibrary` por pasta, `hasThirdPartyBanner`, `isOversizedBundle` (400 KB), `vendoredFiles` (limite 1.500 exclusões); `PathRole` reconhece `src/it` como teste; `RuleSide` (novo, +95 linhas) e escopo `disableSides` em `SystemTuning`/`AuditPolicy`; `TrivyAnalyzer` ignora Terraform (`--skip-files **/*.tf,...`); `DependencyExtractor.fromGradle` lê notação de mapa e plugin do Spring Boot; `ProjectConfig.excludedByTool`; frases do Joern no `SarifAnalyzer` (12.60 a 12.62, três PRs seguidos só de texto de tela); gerador de CLAUDE.md (`stacks.yml` ganha `exclude`, `GuideBook`, `GuideCatalog`).
- Tendência de testes Java: `@Test` 659 → 703 (+44) em `audit-api/src/test` (`git grep -h '@Test'`). Novos: `RuleSideTest`, `SystemTuningSidesTest`, `AnalyzerCatalogSideTest`, `ExternalToolsVendoredTest` (+121), `SarifAnalyzerTest` (+56), `GuideBookTest`, `PathRoleTest`, `ProjectConfigTest`, `DependencyExtractorTest`.
- Documentação com números desatualizados: `docs/referencia/pacotes-de-regras.md` linha 31 diz core-security "60 regras, 15 para Node" e core-persistencia 12; o real é 82, 23 e 13 (contagem de `- id:` nos arquivos). `README.md` e `docs/IMPACTO.md` dizem "mais de 230 regras" (real: 277). O ciclo mexeu só em 2 linhas desse doc.
- Registro oficial do ciclo: `docs/evolucao/LOG.md` (886 linhas; 296 linhas novas no ciclo). O LOG de 12.48.0 já registra "255 regras: 142 back, 89 front, 24 comuns".

### Inferences
- O ganho de cobertura por regras é modesto em volume (22 regras) e concentrado: ~100 % em segurança, 41 % em mobile, 36 % em Node. É um acréscimo "lateral" (novas superfícies), não um aprofundamento do núcleo Java/Spring/front que o escopo fixo (12.65.0) declara.
- Grande parte do trabalho "do ciclo" é infraestrutura de auditoria (vendored/bundles, lados, Joern UI), que reduz ruído em repositórios de treino (ex.: JavaSecLab 11.274 → 3.433 achados; WebGoat 3.884 → ~2.363) mas não mede recall de segurança.

### Gaps
- Não consegui rodar `mvn` (sem Maven Central), então não verifiquei os testes Java; só contei `@Test`. O LOG repete "NÃO verificado: JUnit/Maven (só CI)" em quase toda volta.
- Não consultei o estado real do CI do PR #140/#141 (GitHub não consultado).

---

## 2. Generalidade: amostra de 22 regras criadas pelo ciclo

### Takeaway
A maioria são padrões gerais de API/idioma (XStream, Fastjson, mass assignment, SQL por concatenação, WebView/Intent, modo mundo), porém com dependência de convenção de nomes (`req`, `res`, `event`, `db/pool/client`) e de formas sintáticas exatas, e 3 regras mostram falso positivo provável medido com sondas próprias. Quase todas têm caso positivo, negativo e remediação; as exceções estão listadas.

### Cited Findings
Cobertura de teste/remediação (script sobre `tests/` e `remediation.yml`; "ok" = anotação `ok:` com o id):
- Sem caso negativo (`ok:`): `sentinel-android-manifest-allow-backup` e `sentinel-android-manifest-cleartext` (`tests/sentinel-android.xml` só tem o positivo; o `ok:` do manifest é só para `exported=false`).
- Sem remediação: `sentinel-android-webview-javascript` (INFO).
- Todas as demais 19 têm pelo menos 1 `ruleid` e 1 `ok` e uma entrada de remediação.
- Nenhum teste automatizado exige remediação para regras de `core-security`: `RemediationCatalogTest` só valida `core-quality` (`todaRegraDeQualidadeDoSemgrepTemOrientacaoDeCorrecao` lê `core-quality/rules`); o requisito do guia (`.claude/agents/sentinel-ciclo.md` passo 4) é só convenção. Globalmente, 20 das 271 regras (excluindo `-sql.yml`) não têm remediação (17 em core-security, 1 em iac, 1 persistencia, 1 spring) e 33 não têm caso `ok` (script em `audit-rules/packs`).

Julgamento por regra (arquivos em `audit-rules/packs/core-security/rules/`):
1. `sentinel-android-modo-mundo` (`sentinel-android.yml`): API Android deprecada (`MODE_WORLD_READABLE/WRITEABLE`), padrão geral. Origem InsecureBankv2, mas vale para qualquer app. Geral.
2. `sentinel-android-webview-url-do-intent`: taint de Intent para `loadUrl`; geral para Android, mas só reconhece `getIntent().getStringExtra`/`$I.getData()`; origem DVBank. Geral, estreita.
3. `sentinel-android-log-segredo`: `Log.$M($TAG, <...$ARG...>)` com regex de nome (`token`, `senha`, `password`...). Geral, com risco de FP por nome (`tokenCount`; sonda própria não casou `"tokenCount=" + 3` porque a regex exclui aspas antes da palavra).
4. `sentinel-android-manifest-exported` (`languages: generic`): casa `android:exported="true"` em qualquer arquivo. Sonda minha (arquivo escrito por mim em pasta do scratchpad, não código auditado): a activity de launcher com `intent-filter MAIN` é marcada WARNING, embora `exported="true"` seja obrigatório nela desde o Android 12. Falso positivo sistemático em todo app. Sem `paths: include` nem filtro por `intent-filter`.
5. `sentinel-android-manifest-allow-backup` / `-cleartext`: padrão de configuração geral e correto (ERROR para cleartext). Sem teste negativo; `allowBackup="false"` não testado.
6. `sentinel-crypto-chave-fixa-no-codigo` (`sentinel-crypto.yml`): segundo ramo `String $K = "...";` com nome contendo key/chave/secret/segredo/passphrase. Sonda: `static final String KEY_USER = "user_id"`, `SECRET_HEADER = "X-Secret"` e `keyboardLayout = "qwerty"` são todas marcadas ERROR (confirmado com `opengrep --config` em arquivo próprio). Qualquer constante de nome de chave de SharedPreferences/cabeçalho vira "chave de criptografia no código". O teste negativo só cobre `nome = "valor qualquer"`. Alta chance de FP; é também a regra que coincide na mesma linha com `sentinel-senha-fixa-no-codigo` (`tests/sentinel-java-extras.java` linhas 79 e 81, dois achados).
7. `sentinel-crypto-iv-fixo`: `new IvParameterSpec(new byte[$N])` ou literal. Geral, bom; limite assumido: IV vindo de campo `byte[] iv = {0,...}` não é pego (LOG 12.60.0).
8. `sentinel-xstream-qualquer-tipo`, `sentinel-fastjson-autotype`, `sentinel-ssti-modelo-variavel` (`sentinel-java-extras.yml`): padrões de API de biblioteca, gerais (CWE-502/1336), com `paths.exclude **/test/**`. A SSTI marca qualquer modelo não literal (WARNING), incluindo modelos lidos de banco confiável: risco de FP.
9. `sentinel-classloader-define-classe`: casa só `ClassLoader.class.getDeclaredMethod("defineClass", ...)`, o trecho exato do java-sec-code (LOG 12.54.0); outras formas de `defineClass` (subclasse de `ClassLoader`, `MethodHandles.Lookup.defineClass`) não casam. Sob medida para o repositório de treino.
10. `sentinel-ejs-saida-sem-escape` (`sentinel-ejs.yml`, `languages: [regex]`, `paths.include: *.ejs`): `<%- valor %>` exceto `body|include|partial|contentFor|layout|JSON.stringify|*escape*(`. As exclusões `body` e `layout` vêm do layout.ejs do vulnerable-node (LOG 12.61.0/12.65.0: "o `<%- body %>` ... é o idioma do layout"); `body` é de fato idioma do express-ejs-layouts, então é geral, mas a lista de exclusão foi ajustada ao alvo. Teste com 4 `ok` e 2 `ruleid`. Mutação (apaguei a lista de exclusão numa cópia) reprovou o teste.
11. `sentinel-node-mass-assignment` / `-campo` (12.65.0): `$M.create(req.body)`, `new $M(req.body)`, `Object.assign($X, req.body)` e campo privilegiado (role/isAdmin/balance/saldo...). Padrão geral (CWE-915), mas depende de `req|request` e de `$M` começar com maiúscula ou conter `model|repo`. Sonda: `new Pessoa(req.body)` e `new Foo(req.body)` casam (esperado). O LOG registra que uma versão com taint foi descartada por FP em leituras de `balance`; o nome de campo fixo (`balance`, `saldo`) é herança do vulnerable-node/Juice Shop (`wallet.ts:27`).
12. `sentinel-node-sql-concatenado`, `sentinel-node-nosql-operador`, `sentinel-node-regexp-da-requisicao`, `sentinel-node-xss-refletido`, `sentinel-node-exec-dinamico` (`sentinel-node.yml`): gerais em intenção, mas casadas por nomes: receptor do banco por regex `(pool|db|client|conn|connection|knex|sequelize|database|pg|mysql|sqlite|trx|tx)`; origem só `req|request`; destino `res|response`. Em NestJS/Fastify (`reply`, `ctx`) ou Express com outro nome de parâmetro não disparam (limitação, não FP).
13. `sentinel-node-prototype-pollution`: casa só função recursiva com forma `function $F($T,$S){... $F($T[$K],$S[$K]) ...}` sem a string `"__proto__"`; é a forma do `model/merge.js` do vulnerable-node. Cobre só o merge caseiro; lodash/`Object.assign` aninhado não. Estreita; também `sentinel-node-prototype-pollution` coincide na mesma linha com `sentinel-ts-any-explicito` no teste (linha 360).
14. Alterações NestJS por decorador (12.67.0): `@Query/@Body/@Param/@Headers` como origem em 5 regras (open-redirect, eval-requisicao, command-injection, path-traversal, ssrf) e `return { url }` dentro de `@Redirect()`. Geral para NestJS (padrão de framework), mas exatamente o que o brokencrystals usa; é a única cobertura de decorador e vale só para Node/Nest (nenhuma para Spring além do `@*Mapping` + `String $P`).
15. Evento Lambda (12.64.0): `event.body|headers|queryStringParameters|pathParameters` como origem só em `sentinel-node-desserializacao` e `sentinel-node-eval-requisicao`; o LOG diz "Não feito: outras regras de injeção (exec, SQL, path) não ganharam a origem `event`". Cobertura parcial, vinda do DVSA (volta 12).
16. Fonte "parâmetro String de `@*Mapping`" (12.54.0): o bloco de 8 linhas é copiado 5 vezes em 3 arquivos (`sentinel-injection.yml`, `sentinel-owasp-api.yml`, `sentinel-taint.yml`); qualquer `String` do método passa a ser origem, incluindo parâmetros que não vêm do usuário. Duplicação de manutenção; a mudança nas regras Java não foi seguida de nova medição do OWASP Benchmark (ver seção 3).
- Escopo: o `docs`/agente fixa (12.65.0) "Java, Spring, JavaScript, TypeScript, React, Vue, Angular, HTML, SCSS e CSS" (`.claude/agents/sentinel-ciclo.md`, seção "Escopo de tecnologias"). O ciclo escolheu dois repositórios Android (DVBank volta 7 e InsecureBankv2 volta 8) e produziu 9 regras Android antes dessa decisão de escopo. Android não está no escopo declarado.

### Inferences
- Das 22 regras, ~13 são gerais e bem delimitadas (modo-mundo, iv-fixo, xstream, fastjson, mass-assignment, nosql-operador, regexp, sql-concatenado, webview-url, allow-backup, cleartext, ejs, xss-refletido); ~5 têm FP provável por heurística de nome (crypto-chave-fixa, android-manifest-exported, android-log-segredo, ssti-modelo-variavel, node-exec-dinamico por ser WARNING sem origem); ~3 são estreitas/próximas do alvo (classloader-define-classe, prototype-pollution, mass-assignment-campo com nomes fixos). Classificação minha, baseada em leitura e sondas, sem medição em repositórios reais.
- O gabarito de recall não prova generalização: cada regra citada em `gabaritos/cr0hn__vulnerable-node.json` foi escrita depois de ler o próprio repositório (ver seção 3).

### Gaps
- Não medi FP em código real; só sondas sintéticas em `/tmp/.../scratchpad/fp/` (arquivos meus). Falta a "amostra de falso positivo" que o novo guia exige.
- Não verifiquei se a regra `manifest-exported` tem tratamento no `FindingConfidence` (não encontrei).

---

## 3. Qualidade dos testes, paridade e sobreposição

### Takeaway
Os testes de regra são eficazes (3 de 3 mutações matadas) e há paridade Opengrep × Semgrep no CI, mas o ciclo não criou deduplicação entre suas próprias regras sobrepostas (os testes documentam achados duplicados na mesma linha), e a prova de generalização é fraca: recall medido só no treino, gabarito escrito sobre as regras recém-criadas, validação só por contagem (que não mudou).

### Cited Findings
- Mutação (cópia em scratchpad, `opengrep --test`): (a) trocar o filtro de nome de `sentinel-crypto-chave-fixa-no-codigo` por `.*`; (b) trocar o filtro de campos de `sentinel-node-mass-assignment-campo` por `.*`; (c) remover a lista de exclusão de `sentinel-ejs-saida-sem-escape` → resultado 76/79 com as três regras reprovadas. Os `ok:` pegam afrouxamento de regra.
- Limite do teste: ele só acusa o que está nos casos. As sondas que fiz (`KEY_USER`, launcher `exported`) mostram FP que passam no 79/79.
- Paridade: `scripts/paridade-motores.py` (criado em `ad35bd9`, antes do ciclo, 12.35.0) roda Opengrep e Semgrep sobre os `tests/` de cada pacote e compara o conjunto (regra, arquivo, linha); chamado no job `regras` de `.github/workflows/ci.yml` (passo "Opengrep e Semgrep devolvem os mesmos achados"). O CI usa `semgrep --test` dos pacotes e a paridade. O ciclo usou o script nas voltas: 542=542 (12.58.0), 553=553 (12.60.0), 588=588 (12.61.0), 602=602 (12.62.0), 624=624 (12.65.0), 641=641 (12.67.0) (`docs/evolucao/LOG.md`). Exceção: volta 12 foi verificada só com `semgrep --test` 21/21 ("Semgrep 1.184, não o Opengrep"); e Opengrep local 1.33.1 ≠ 1.30.0 fixado no CI (LOG 12.61.0/12.65.0). Eu rodei `opengrep --test` 1.30.0 e funcionou nos 13 pacotes, o que contradiz o comentário do CI "`opengrep test` não lê o formato de teste dos pacotes".
- Sobreposição/achados duplicados: rodando o Opengrep sobre todos os `tests/` (671 achados) há 54 linhas com ≥ 2 regras. Dentro do ciclo: `sentinel-node-eval-dinamico` + `sentinel-node-eval-requisicao` (`tests/sentinel-node.ts` linhas 241 e 524, e o teste declara `// ruleid: sentinel-node-eval-requisicao, sentinel-node-eval-dinamico`), `sentinel-node-command-injection` + `sentinel-node-exec-dinamico` (linha 536, também declarado), `sentinel-crypto-chave-fixa-no-codigo` + `sentinel-senha-fixa-no-codigo` (`tests/sentinel-java-extras.java` 79/81), `sentinel-crypto-iv-fixo` + `sentinel-cert-charset-implicito`, `sentinel-classloader-define-classe` + `sentinel-quarkus-reflexao-nativa` (208), `sentinel-node-prototype-pollution` + `sentinel-ts-any-explicito`, `sentinel-node-ssrf` + `sentinel-int-fetch-sem-timeout`. Também pré-existentes (ex.: `sentinel-sql-injection` + `sentinel-sql-select-estrela`).
- A deduplicação existe (`FindingDeduplicator`, tópicos em `dedup.yml`), mas só em `core-frontend`, `core-iac` e `core-quality` e é entre ferramentas (PMD/ESLint/Trivy vs regra Sentinel). `git diff 432d657 origin/main -- 'audit-rules/packs/*/dedup.yml'` = vazio: o ciclo não adicionou nenhum tópico. O `ruleid: A, B` dos testes assume dois achados. O guia diz explicitamente "errar para o lado de manter duplicata" (javadoc de `FindingDeduplicator`).
- No LOG, o ciclo percebe o efeito: "`eval` do corpo ... saiu só como `eval-dinamico` (MEDIUM)" (12.64.0/12.67.0), e depois mantém as duas regras, que passam a disparar juntas.
- Duplicação com regras anteriores: `sentinel-node-sql-concatenado` foi desenhada para "não duplicar `sentinel-node-sql-injection`" excluindo funções com `req`; `sentinel-node-exec-dinamico` exclui `req` mas não NestJS/Lambda (o teste mostra a sobreposição em NestJS com `@Query`).
- Medição de generalização do ciclo: o recall do gabarito é do TREINO. `audit-rules/calibracao/gabaritos/cr0hn__vulnerable-node.json`: 29 falhas, "regras aceitas" são as regras criadas para esse mesmo repositório (ex.: `sentinel-node-sql-concatenado`, `sentinel-ejs-saida-sem-escape`); 12 das 29 são "sementes do próprio repositório (`sast/ground-truth.yaml`)". Recall 22/29 (76 %) → 25/29 (86 %) (LOG 12.65.0) é esse número. `gabaritos/BrightSec__brokencrystals.json`: 7 falhas "amostra lida", recall 4/7 → 6/7 (LOG 12.67.0); `src/users/users.controller.ts:608` está no gabarito como "achado do Sentinel nesta linha; rota não conferida".
- Validação: `audit-rules/calibracao/gabaritos/validacao/` contém só `LEIAME.json` com `"repo": "(preencher)", "falhas": []` (adicionado em `ef8f6c5`, não mergeado). O guia obrigatório "Prova de generalização" também é só do `ef8f6c5`. Contagens de validação ao longo das voltas 7 a 13: dvja 176 (nota 48,95) sempre igual; NodeGoat 328 → 278 (queda explicada por ESLint `no-require-imports`) → 279 (+1 `sentinel-node-exec-dinamico` MEDIUM em `Gruntfile.js:165`, "não li o arquivo, não sei se é falso positivo") (LOG 12.58.0 a 12.67.0). As regras Node novas "não acharam nada no NodeGoat" (LOG 12.61.0). A validação não foi lida (política treino/validação em `audit-rules/calibracao/conjuntos.json`).
- Benchmark OWASP (Java): `audit-rules/calibracao/benchmark/baseline.json` média 0,461, último commit 2026-10-28 (`fd085f9`, antes do ciclo). Nenhuma entrada do LOG do ciclo (após 12.45.0) cita nova medição do Benchmark, embora 5 regras Java tenham ganhado nova fonte de taint e o XSS-JSP tenha mudado. O guia manda rodar o Benchmark se a volta mudar regra Java; isso só foi escrito em `ef8f6c5`.
- Amostra de FP: o LOG do ciclo não traz amostra de achados lidos um a um por regra com contagem verdadeiro/falso; as "conferências" são dos achados novos (ex.: "+12, todos conferidos") e de achados de treino. `docs/evolucao/precisao.md` não foi tocado pelo ciclo (só 2 linhas de cabeçalho).
- Tempo: `opengrep` com as 271 regras sobre a pasta de casos de teste dos pacotes levou 9,0 s de parede (minha medição, `--time`; não é medida comparável a repositório real). O LOG do ciclo não registra nenhuma medição de tempo de varredura depois de 12.45.0; a referência anterior é 28 s para 158 regras em 1.212 arquivos (LOG, Volta 3, 2026-10-21). Medidas de ganho de tempo vieram só da exclusão de arquivos (ex.: Opengrep 8.929 → 1.843 achados no JavaSecLab, 12.56.0).

### Inferences
- A suíte de regras detecta regressão da regra (mutação), mas não FP de uso real; faltam casos negativos "difíceis" (constantes `KEY_*`, launcher `exported`).
- A paridade garante equivalência dos motores, não qualidade. Sem `dedup.yml` próprio, o usuário final vê 2 achados na mesma linha para eval, command-injection/exec e chave/senha fixa, o que infla contagem e pesa na nota.
- O ganho "medido" (recall 76 → 86 %) é treino-sobre-treino; a única evidência fora do treino é "dvja/NodeGoat sem mudança", que é compatível com "nada a achar" ou "não generalizou" (o LOG admite: "não dá para dizer, sem ler o código, se faltou ou não havia o que achar").

### Gaps
- Não rodei o Semgrep nem `paridade-motores.py` (só Opengrep, conforme restrição). Os números 542…641 são citações do LOG.
- Não executei `scripts/benchmark-owasp.py` (exigiria o Benchmark; fora do escopo permitido).

---

## 4. Limites do motor: documentados e priorizados?

### Takeaway
O ciclo registra os limites (taint entre funções/arquivos, JS/TS sem Joern) com honestidade em quase toda volta, mas não os ataca: a decisão de avaliar o Joern para JS/TS ficou "pendente" e o esforço vai para regras de padrão local, o que aproxima o teto de ganho do que o motor consegue ver numa função.

### Cited Findings
- Documentado: `.claude/agents/sentinel-ciclo.md` seção "Medidas de referência": "o Opengrep não segue o dado entre funções e arquivos. Para Java existe o Joern (opcional); para JS/TS não". `docs/evolucao/AGENTE.md` "Limites conhecidos" e `docs/integracao/joern.md`: Joern usa `javasrc2cpg`, só Java, desligado por padrão (`WITH_JOERN=false`, ~2 GB).
- LOG do ciclo registra o limite em: 12.61.0 ("função de dados que recebe o dado por parâmetro (`findOne(body)`, `new RegExp(pattern)`, `fetch(url)`) ... limite do motor entre funções"), 12.65.0 ("INSERT com `fields.role` ... três sementes que passam o dado por parâmetro de função: limite do motor"), 12.67.0 (`GET /spawn` controller → `AppService.launchCommand`; SQL do `kid` do JWT; "taint entre arquivos de serviços Nest" NÃO verificado), 12.63.0 (Gradle sem lockfile).
- Decisões pendentes (12.65.0): "(2) volta dedicada a avaliar o Joern para JS/TS (dado entre funções) antes de decidir se compensa" — não foi feita nas voltas 12/13 nem no guia seguinte. A outra pendência (rodar treino, validação e regressão em paralelo) também ficou aberta. Nenhum relatório de custo/benefício do Joern foi escrito.
- Do lado Java, o Joern foi adicionado ANTES do ciclo (12.36.0, `0a8f122`, "só 6 casos, sem medir falso negativo", "ganho sobre o Opengrep ... ainda não existe"). Durante o ciclo o Joern só recebeu texto de tela (12.60.0 a 12.62.0, três PRs) e a descoberta em 12.53.0 de que seus achados ficavam em `audit-api/joern/testes/**` excluído pelo `.sentinel.yml`.
- Contornos locais criados: `sentinel-node-sql-concatenado` (SQL montado em `model/db.js`, função que recebe o dado "longe da rota"), `@Query/@Body` como origem (o parâmetro vira origem na própria função), `event.*` para Lambda. Cada um "move" a fonte para dentro do arquivo em vez de seguir o fluxo; achados que dependem de chamada entre arquivos permanecem como lacuna (12.61.0, 12.65.0, 12.67.0: `GET /spawn` e SQL do `kid` seguem sem regra, `regras: []` no gabarito do brokencrystals).
- Casos fora de escopo registrados e descartados: Python, Go, C, PHP (decisão do João, 12.65.0).

### Inferences
- Se a maior parte das falhas restantes dos treinos é "dado entre funções/arquivos" (todas as lacunas assumidas citadas acima), o teto para JS/TS com padrão local é o recall já medido (~86 % no vulnerable-node porque o gabarito foi montado só com falhas visíveis e 12/29 são sementes isoladas). A próxima camada de ganho exigiria taint interprocedural para JS/TS (Joern/CodeQL-like), que o ciclo identifica mas não prioriza. Inferência minha, não há medição do ganho do Joern em JS/TS.
- Dada a regra de parada do guia ("duas rodadas sem ganho medido: pare"), o ciclo pode estagnar justamente quando só restam lacunas de taint; o registro correto é "teto do motor", e o LOG o faz nas notas, mas não como item priorizado de backlog (`docs/evolucao/PLACAR.md` não foi atualizado com isso).

### Gaps
- Não encontrei medição do Joern em repositório JS/TS nem estimativa de esforço para um frontend `jssrc2cpg`; não consultei documentação externa do Joern (fora do escopo do repositório).

---

## 5. Dívida e riscos

### Takeaway
Os riscos principais são: FP provável em 3 a 5 regras novas, viés de treino (10 repositórios de treino vulneráveis de propósito e 22 regras derivadas deles), crescimento de superfície fora do escopo declarado (Android) com zero avanço em React/Vue/Angular/HTML/CSS/Spring, heurísticas de exclusão que escondem código próprio (falso negativo) e manutenção duplicada.

### Cited Findings
- FP provável (verificado por leitura ou sonda própria; nenhuma medida em repositório real): `sentinel-crypto-chave-fixa-no-codigo` (ERROR em `KEY_USER = "user_id"`, `SECRET_HEADER`, `keyboardLayout`), `sentinel-android-manifest-exported` (launcher activity), `sentinel-android-manifest-cleartext` e `-allow-backup` sem teste negativo, `sentinel-ssti-modelo-variavel` (qualquer modelo não literal), `sentinel-node-exec-dinamico` (+1 achado desconhecido no NodeGoat `Gruntfile.js:165`, build script, LOG 12.62.0; no secure-code-game 5 dos 6 achados eram `lsof`/`kill` com `${porta}`/`${pid}`, "baixo risco").
- Sobreajuste ao treino: `conjuntos.json` lista 10 treinos vulneráveis de propósito (WebGoat, juice-shop, JavaSecLab, DVBank, InsecureBankv2, vulnerable-node, secure-code-game, log4shell, DVSA, brokencrystals). Todo FN corrigido vem desses (cada volta 7 a 13 criou ou alterou regra a partir do repositório novo lido naquela volta). Validação (7 repositórios fixos) sem leitura e sem gabarito. Nota de `conjuntos.json`: "treino influenciou as regras". O repositório de treino é também a fonte do gabarito (circular).
- A "nota" cai quando a ferramenta acha mais coisas ("A nota cai porque a auditoria passou a ver falhas reais", LOG 12.60.0, 12.61.0, 12.62.0): ok para treinos, mas a métrica de nota não serve de sinal de melhoria.
- Escopo: nenhum novo pacote nem regra para Spring, React, Vue, Angular, HTML, CSS (`git diff --name-status` em `packs/` só traz `core-security` com regras novas; core-frontend/core-vue/core-spring só têm `metadata.side` ou 1 linha). 9 de 22 regras novas (Android) estão fora do escopo declarado em 12.65.0. Python/PHP/Go corretamente evitados. EJS/Node é JS (no escopo).
- Heurísticas de exclusão com risco de falso negativo (todas no `LOG.md` como "Risco/Limite"): arquivo JS/CSS ≥ 400 KB nunca é analisado (`ExternalTools.OVERSIZED_BUNDLE_BYTES`, "código próprio gerado e muito grande deixa de ser analisado"); `lib` sob `static|public|webapp|resources` conta como terceiros "mesmo se o projeto guardar código próprio ali" (12.56.0); cabeçalho com licença+endereço no topo de arquivo próprio é tratado como biblioteca (12.52.0); o regex `LICENSE_WORD` inclui `\bmit\b` solto; `MAX_VENDORED_EXCLUDES = 1500` trunca em silêncio; `src/it` ignorado.
- Código morto/duplicado: `ExternalTools.oversizedBundles` não é chamado em produção (só em `ExternalToolsVendoredTest`, linhas 66-67) depois de `vendoredFiles` (que repete o mesmo `walkFileTree`). `ExternalTools` 181 → 361 linhas; `SarifAnalyzer` 506 linhas e a própria autoauditoria #105 aponta "God Object `SarifAnalyzer`" e complexidade 18/14/11 sem refatorar (LOG 12.63.0 "Não feito").
- Duplicação de regra: a fonte de taint "String de `@*Mapping`" está copiada 5 vezes; as listas de nomes `req|request` aparecem em ~20 regras Node; a lista de campos privilegiados está fixa em um regex.
- Documentação desatualizada (seção 1): contagens de regras no `docs/referencia/pacotes-de-regras.md`, `README.md`, `docs/IMPACTO.md`; `pack.yml` descrição de core-security sem Android/EJS/crypto.
- Tempo de varredura: sem medição no ciclo. A superfície cresceu +22 regras (+8,6 %) mais 5 fontes de taint por `pattern-inside`; o ganho de tempo documentado vem de excluir arquivos de terceiros, mas o 12.49.0 declara "o Opengrep ainda lê as regras dos dois lados e os achados do lado desligado são filtrados depois (sem ganho de tempo)". Minha medição: 9,0 s para 271 regras sobre os casos de teste (corpus pequeno, não comparável).
- Verificação incompleta registrada em quase toda entrada ("NÃO verificado: JUnit/Maven (só CI)"; `SarifAnalyzerTest` e outros novos "não rodaram"); testes Java do ciclo foram escritos sem execução local.
- Falhas de processo: volta 12 verificada com Semgrep 1.184 e não com Opengrep ("NÃO verificado: Opengrep nesta volta"); validação sem recall; guia de generalização só criado depois da volta 13, em commit não mergeado (`ef8f6c5`).

### Inferences
- Risco principal de produto: FP em regras de severidade ERROR (chave fixa, cleartext) e WARNING sistemático (exported) em apps Android reais, que fica oculto porque os treinos são projetos com falhas de propósito.
- O ciclo sem alvo de validação medida tende a otimizar contagem de achados novos nos treinos (ganho "visível") sem prova de que o Sentinel melhorou em código comum.

### Gaps
- Sem medição real de FP/escala/tempo; sem acesso ao CI.

---

## 6. Bugs que o ciclo introduziu ou deixou passar

### Takeaway
Um bug do autoauditado (#105) veio de regra anterior ao ciclo, não do ciclo; o defeito do gerador de CLAUDE.md foi introduzido pelo ciclo (regras Android) e só apareceu seis voltas depois; outros defeitos menores e riscos não corrigidos estão registrados.

### Cited Findings
- Regra `sentinel-boas-java-recurso-sem-try-with-resources` com FP: foi criada em `e4ecfbb` (2026-10-29, pré-ciclo; `git log -S`), e a autoauditoria #105 (LOG 12.63.0, commit `3f5a279`, 2026-11-06) achou o FP em `DbConfigSource.java:92`. A correção (`audit-rules/packs/core-boas-praticas/rules/sentinel-boas-java.yml`) aceita cabeçalhos com 2, 3 e 4 recursos, copiando 3 blocos `pattern-not-inside`; 5+ recursos ainda geram FP (o comentário do próprio YAML: "o Opengrep não aceita reticências na lista de recursos"). Teste novo em `tests/sentinel-boas-java.java` (+16 linhas). Bug pré-existente, descoberto pelo processo de autoauditoria do ciclo, correção parcial.
- Gerador de CLAUDE.md (LOG 12.64.0, `3f5a279`): a regra `sentinel-android-webview-url-do-intent` aparecia em "Sem exceção" do guia de Spring porque as regras Android são `languages: [java]` e nenhuma tecnologia as reivindicava. Introduzido pelas regras Android criadas em 12.58.0 (voltas 7), corrigido em `stacks.yml` com `exclude: sentinel-android-*` para java, spring e quarkus. O mesmo commit corrige título de regra cortado no `: ` dentro de crases e item duplicado (actuator YAML/properties). Os dois guias de referência (`docs/referencia/claude-md-spring-boot.md`, `claude-md-typescript.md`) foram "montados à mão a partir do texto colado: não vieram do gerador novo". Classe de bug: nova regra altera saída de outro componente por herança de linguagem e nenhum teste do ciclo cobria o efeito no gerador. `GuideBookTest.java:136` agora verifica que não há `sentinel-android-` em Java/Spring/Quarkus.
- Em aberto no mesmo registro: o guia diz "até 5 parâmetros" e `sentinel-cct` (ts-clean-code-labs42) aponta mais de 2 (decisão do João).
- Reverso: aviso de que ao introduzir o escopo por lado (12.48.0/12.49.0) o ciclo marcou 18 regras ambíguas à mão e deixou `sentinel-int-http-sem-tls` como "comum" para não mudar ids (decisão consciente, registrada). 12.49.0: "o Opengrep ainda lê as regras dos dois lados" (sem ganho de tempo).
- NodeGoat +1 achado `sentinel-node-exec-dinamico` em `Gruntfile.js:165` (12.62.0) e NodeGoat 328 → 278 (a queda atribuída a `no-require-imports` do ESLint, sem verificar que nenhum achado de segurança sumiu: "Sem leitura do código para confirmar", 12.58.0). Possível regressão silenciosa na validação, não conferida.
- Regra que deveria ter sido retirada/limitada e permanece: `sentinel-crypto-chave-fixa-no-codigo` (FP confirmado por sonda), `sentinel-android-webview-javascript` sem remediação, remediação não exigida por teste fora de `core-quality`.
- Verificação trocada: 12.63.0 reporta Log4Shell "sem ganho de detecção" (nenhum CVE de dependência; a dimensão dependências fica em 100, "enganoso", LOG 12.63.0) e marca como decisão futura consultar OSV para manifestos que o Trivy não resolve; o falso senso de segurança permanece.
- Dedup: `sentinel-node-eval-*` duplicado (seção 3) registrado em teste, não corrigido.
- Processo: 12.54.0 e outros dependem de `javac` ad hoc para validar helpers Java; testes JUnit só no CI; o LOG admite em quase todas as entradas.

### Inferences
- O processo de "autoauditoria + correção" funciona como rede de segurança (achou o FP do try-with-resources e os defeitos do CLAUDE.md), mas pegou os defeitos tarde e só os que apareceram numa leitura manual de saída.

### Gaps
- Não busquei issues/PRs no GitHub para outros defeitos reabertos depois das voltas; não verifiquei se CI do PR #140 ficou verde.

---

## Resposta direta: o Sentinel realmente evoluiu?

Evolução real e verificável: +22 regras de segurança (13 delas bem gerais), 21 remediações, 54 `ruleid` e 48 `ok` de teste com mutação mostrando que os testes pegam regressão, filtro de bibliotecas de terceiros reduzindo ruído (JavaSecLab 11.274 → 3.433, Opengrep 8.929 → 1.843), escopo back/front, paridade Opengrep × Semgrep ativa. Evolução NÃO provada: generalização (validação sem gabarito, recall só no treino, Benchmark sem remedição, FP sem amostra), qualidade das regras de heurística de nome, e o teto imposto pelo motor (JS/TS sem taint entre funções, Joern só Java) que o ciclo registra mas não prioriza.

## Itens de memória/contexto
- Nada foi usado do `user_memory`; não li arquivos de memória. Todos os fatos vêm do repositório local e de execuções (opengrep 1.30.0 em `/root/.local/bin/opengrep`) feitas apenas sobre os casos de teste dos pacotes e arquivos de sonda criados no scratchpad.
