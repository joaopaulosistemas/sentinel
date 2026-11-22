# Amostra para revisao humana (item E)

## Volta 52 (2026-11-22): veracode/verademo, regras 12.119.0 (antes da correcao desta volta), branch `ciclo/estabilizacao-16`

Sorteio: `random.seed(5202)` sobre os 106 achados de regras de seguranca do Opengrep da auditoria 44733964974 (excluidos qualidade, estilo, log de erro, CSS, front-end, camadas e docker-sem-user), 20 sorteados e ordenados por arquivo e linha. **Classificacao por subagente independente do Claude** (contexto novo, so a lista e o codigo do verademo; nao viu diff, gabarito nem LOG). Nao e revisao externa: o Joao pode sobrescrever qualquer linha e a dele prevalece. Resultado: 12 VERDADEIRO, 3 BAIXO VALOR, 5 FALSO (estrito 12/20 = 60 %, Wilson 39 a 78 %; largo 15/20 = 75 %, Wilson 53 a 89 %). O verademo tem 47 de 106 achados da regra XSS-JSP, entao a amostra e dominada por ela; estimativa, nao medida.

| # | Regra | Arquivo | Linha | Classificacao |
|---|---|---|---|---|
| 1 | sentinel-spring-print-stack-trace | app/src/main/java/com/veracode/verademo/commands/ListenCommand.java | 51 | BAIXO VALOR: printStackTrace so em stderr |
| 2 | sentinel-sql-injection | app/src/main/java/com/veracode/verademo/commands/RemoveAccountCommand.java | 49 | VERDADEIRO: INSERT concatenado com blabberUsername de requisicao |
| 3 | sentinel-log-injection | app/src/main/java/com/veracode/verademo/controller/BlabController.java | 278 | VERDADEIRO: User-Agent gravado sem sanitizar (forja de log) |
| 4 | sentinel-sql-injection | app/src/main/java/com/veracode/verademo/controller/BlabController.java | 464 | VERDADEIRO: ORDER BY com sort de @RequestParam |
| 5 | sentinel-crypto-random-servidor | app/src/main/java/com/veracode/verademo/controller/ResetController.java | 90 | FALSO: new Random() so gera dados de demonstracao no reset do banco |
| 6 | sentinel-log-injection | app/src/main/java/com/veracode/verademo/controller/UserController.java | 115 | VERDADEIRO: target e username de requisicao no log |
| 7 | sentinel-servlet-path-traversal | app/src/main/java/com/veracode/verademo/controller/UserController.java | 619 | FALSO: oldImage vem de listFiles/getName (nome base); o risco real esta na linha seguinte |
| 8 | sentinel-crypto-hash-fraco | app/src/main/java/com/veracode/verademo/controller/UserController.java | 908 | VERDADEIRO: MD5 sem salt para senha |
| 9 | sentinel-crypto-hash-fraco | app/src/main/java/com/veracode/verademo/utils/User.java | 103 | VERDADEIRO: MD5 sem salt na classe User |
| 10 | sentinel-spring-print-stack-trace | app/src/main/java/com/veracode/verademo/utils/User.java | 109 | BAIXO VALOR: printStackTrace de MD5 inexistente |
| 11 | sentinel-spring-print-stack-trace | app/src/main/java/com/veracode/verademo/utils/UserFactory.java | 50 | BAIXO VALOR: printStackTrace na desserializacao do cookie |
| 12 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/blab.jsp | 108 | VERDADEIRO: comentario armazenado impresso sem escape |
| 13 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/blabbers.jsp | 103 | VERDADEIRO: blab_name armazenado sem escape |
| 14 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/blabbers.jsp | 119 | FALSO: saida e literal constante (Ignore/Listen) |
| 15 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/feed.jsp | 102 | FALSO: post.getId() e int |
| 16 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/feed.jsp | 102 | FALSO: post.getCommentCount() e int |
| 17 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/login.jsp | 59 | VERDADEIRO: error com mensagem SQL com entrada do usuario |
| 18 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/login.jsp | 78 | VERDADEIRO: target de requisicao dentro de atributo |
| 19 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/profile.jsp | 101 | VERDADEIRO: realName armazenado em atributo |
| 20 | sentinel-xss-jsp-unescaped-output | app/src/main/webapp/WEB-INF/views/profile.jsp | 119 | VERDADEIRO: username escolhido pelo usuario |

---

## Volta 49 (2026-11-19): appsecco/VyAPI, regras 12.118.0 (depois), branch `ciclo/estabilizacao-16`

O alvo so tem **17** achados de regras de seguranca do Opengrep (descartados estilo, qualidade, comentarios, PMD, SCA); todos listados, **sem sorteio (semente nao se aplica)**. **Classificacao feita por subagente independente do Claude** (contexto novo, so a lista e o codigo do VyAPI; nao viu diff, gabarito nem LOG). Nao e revisao externa: o Joao pode sobrescrever qualquer linha e a dele prevalece. Resultado: 14 VERDADEIRO, 2 BAIXO VALOR, 1 FALSO em 17 (estrito 14/17 = 82 %; largo 16/17 = 94 %). 5 dos 17 sao a mesma falha vista por varias linhas (receiver e provider), e 12 dos 17 sao de regras criadas ou ajustadas nesta propria volta ou de manifesto: o numero e estimativa, nao medida.

| # | Regra | Arquivo | Linha | Classificacao |
|---|---|---|---|---|
| 1 | sentinel-int-http-sem-tls | app/src/androidTest/java/com/appsecco/vyapi/ExampleInstrumentedTest.java | 16 | FALSO: linha 16 e comentario Javadoc (href), sem requisicao; a regra casava `href="http://` (corrigido nesta volta) |
| 2 | sentinel-android-manifest-allow-backup | app/src/main/AndroidManifest.xml | 16 | VERDADEIRO: allowBackup="true" permite extrair o banco por adb backup |
| 3 | sentinel-android-manifest-exported | app/src/main/AndroidManifest.xml | 26 | VERDADEIRO: receiver exportado sem permissao |
| 4 | sentinel-android-manifest-exported | app/src/main/AndroidManifest.xml | 36 | VERDADEIRO: provider exportado; permissao de outro app nao declarada; SQLi no #13 |
| 5 | sentinel-android-manifest-exported | app/src/main/AndroidManifest.xml | 46 | VERDADEIRO: provider exportado com grantUriPermissions e file paths amplos |
| 6 | sentinel-android-manifest-exported | app/src/main/AndroidManifest.xml | 59 | VERDADEIRO: provider exportado com grantUriPermissions e paths amplos |
| 7 | sentinel-android-manifest-exported | app/src/main/AndroidManifest.xml | 72 | VERDADEIRO (impacto provavel baixo): servico exportado sem permissao |
| 8 | sentinel-arquivo-temporario-inseguro | app/src/main/java/com/appsecco/vyapi/fragments/CameraFragment.java | 178 | BAIXO VALOR: createTempFile no diretorio externo do proprio app, nome aleatorio |
| 9 | sentinel-crypto-cifra-fraca | app/src/main/java/com/appsecco/vyapi/fragments/CreateContactFragment.java | 122 | VERDADEIRO: Cipher.getInstance("AES") = ECB, chave fixa "secretKey" |
| 10 | sentinel-crypto-cifra-fraca | app/src/main/java/com/appsecco/vyapi/fragments/CreateContactFragment.java | 141 | VERDADEIRO: decrypt com o mesmo ECB e chave fixa |
| 11 | sentinel-android-webview-javascript | app/src/main/java/com/appsecco/vyapi/fragments/ViewWebsiteFragment.java | 49 | VERDADEIRO: JS habilitado em WebView que carrega URL do usuario |
| 12 | sentinel-android-sqlite-concatenado | app/src/main/java/com/appsecco/vyapi/misc/ContactDBHandler.java | 53 | VERDADEIRO: SQLi por concatenacao em rawQuery |
| 13 | sentinel-android-sqlite-concatenado | app/src/main/java/com/appsecco/vyapi/provider/ContactDBProvider.java | 68 | VERDADEIRO: appendWhere com segmento da URI em provider exportado |
| 14 | sentinel-android-intent-caminho-arquivo | app/src/main/java/com/appsecco/vyapi/receiver/VyAPIBroadcastReceiver.java | 50 | VERDADEIRO: FileInputStream com caminho do extra do broadcast exportado |
| 15 | sentinel-android-intent-caminho-arquivo | app/src/main/java/com/appsecco/vyapi/receiver/VyAPIBroadcastReceiver.java | 51 | VERDADEIRO: FileOutputStream com nome do extra (traversal) |
| 16 | sentinel-android-intent-caminho-arquivo | app/src/main/java/com/appsecco/vyapi/receiver/VyAPIBroadcastReceiver.java | 58 | BAIXO VALOR: so cria o File, redundante com o #14 |
| 17 | sentinel-android-intent-caminho-arquivo | app/src/main/java/com/appsecco/vyapi/receiver/VyAPIBroadcastReceiver.java | 59 | VERDADEIRO: renameTo com destino controlado pelo extra |

---

## Volta 46 (amostra anterior)

Gerada na volta 46 (2026-11-18) a partir da auditoria do CI de `layro01/node-api-goat` (treino mais recente, branch `ciclo/estabilizacao-14`, regras da versão 12.116.0).
**Classificação feita na volta 47 por subagente independente do Claude** (contexto novo, recebeu só a lista e o código do alvo, sem ver diff, gabaritos nem LOG). Não é revisão externa: o João pode sobrescrever qualquer linha e a dele prevalece. Resultado: 12 VERDADEIRO, 3 VERDADEIRO DE BAIXO VALOR, 0 FALSO em 15 (estrito 12/15 = 80 %, Wilson 55 a 93 %; largo 15/15, Wilson 80 a 100 %). 8 dos 15 são duplicatas da mesma linha por regras diferentes (eval e exec), o que infla a precisão.

Meta era 20 achados de regras de segurança; o repositório só tem **15** (todos listados, sem sorteio, descartados achados de CVE em `package-lock.json`, estilo e qualidade). Na próxima amostra (volta 49), sortear 20 de um repositório com mais achados.

| # | Ferramenta | Regra | Arquivo | Linha | Classificação (V/F) |
|---|------------|-------|---------|-------|---------------------|
| 1 | Opengrep | sentinel-node-command-injection | app/server.js | 106 | VERDADEIRO: `cprocess.exec(req.query.foo)` executa string da query |
| 2 | Opengrep | sentinel-node-desserializacao | app/server.js | 97 | VERDADEIRO: `unserialize(req.query.foo)` com node-serialize permite RCE |
| 3 | Opengrep | sentinel-node-eval-requisicao | app/server.js | 32 | VERDADEIRO: `eval(req.query.red)` com entrada do usuário |
| 4 | Opengrep | sentinel-node-path-traversal | app/server.js | 54 | VERDADEIRO: `fse.readJsonSync(req.query.foo)` sem restrição de diretório (só JSON) |
| 5 | Opengrep | sentinel-node-xss-refletido | app/server.js | 62 | VERDADEIRO: `res.send("<p>..."+req.query.text)` sem escape, text/html |
| 6 | Opengrep | sentinel-node-xss-refletido | app/server.js | 80 | BAIXO VALOR: `res.send(req.query.text)` é XSS real, mas redundante com o #5 |
| 7 | Opengrep | sentinel-k8s-imagem-latest | azure-pipelines-1.yml | 17 | BAIXO VALOR: imagem `:latest` num agente IAST de pipeline de CI |
| 8 | Opengrep | sentinel-node-open-redirect | app/server.js | 89 | BAIXO VALOR: host fixo: não é open redirect, só injeção de parâmetro de query |
| 9 | ESLint | eslint-no-eval | app/server.js | 32 | VERDADEIRO: mesmo eval do #3 |
| 10 | ESLint | eslint-no-eval | app/server.js | 33 | VERDADEIRO: `eval(req.query.green, 10)` |
| 11 | ESLint | eslint-no-eval | app/server.js | 34 | VERDADEIRO: `eval(req.query.blue, 10)` |
| 12 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 32 | VERDADEIRO: duplicado do #3 por outra regra |
| 13 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 33 | VERDADEIRO: duplicado do #10 |
| 14 | ESLint | eslint-security/detect-eval-with-expression | app/server.js | 34 | VERDADEIRO: duplicado do #11 |
| 15 | ESLint | eslint-security/detect-child-process | app/server.js | 106 | VERDADEIRO: `cprocess.exec(req.query.foo)`, duplicado do #1 |


## Volta 55 (2026-11-22): WebGoat/WebGoat-Legacy, regras 12.123.0 (antes da correção de ruído do XSS-JSP desta volta), branch 

Sorteio:  sobre os 725 achados de regras de segurança (categoria ) do Opengrep na auditoria 45022478169, 20 sorteados e ordenados por arquivo e linha. **Classificação por subagente independente do Claude** (contexto novo, só a lista e o código do WebGoat-Legacy; não viu diff, gabarito nem LOG). Não é revisão externa: o João pode sobrescrever qualquer linha e a dele prevalece. Resultado: 8 VERDADEIRO, 5 BAIXO VALOR, 7 FALSO (estrito 8/20 = 40 %, Wilson 22 a 61 %; largo 13/20 = 65 %, Wilson 43 a 82 %). 545 dos 725 achados são da regra XSS-JSP, então a amostra é dominada por ela (13 das 20 linhas; 7 falsos, 6 deles XSS-JSP: constante, , acessador ). Estimativa, não medida.

| # | Regra | Arquivo | Linha | Classificação |
|---|---|---|---|---|
| 1 | sentinel-sql-concatenado-executado | src/main/java/org/owasp/webgoat/lessons/BlindStringSqlInjection.java | 114 | VERDADEIRO: executeQuery com accountNumber do usuário (injeção cega) |
| 2 | sentinel-sql-concatenado-executado | src/main/java/org/owasp/webgoat/lessons/DOS_Login.java | 106 | VERDADEIRO: username/password concatenados em executeQuery |
| 3 | sentinel-crypto-hash-fraco | src/main/java/org/owasp/webgoat/lessons/Encoding.java | 465 | BAIXO VALOR: MD5 só para exibir hash na lição, sem autenticação |
| 4 | sentinel-senha-comparada-com-literal | src/main/java/org/owasp/webgoat/lessons/HtmlClues.java | 84 | VERDADEIRO: credencial fixa admin/adminpw |
| 5 | sentinel-senha-fixa-no-codigo | src/main/java/org/owasp/webgoat/lessons/MultiLevelLogin2.java | 61 | FALSO: PASSWORD é o nome do parâmetro de formulário |
| 6 | sentinel-spring-requestmapping-sem-method | src/main/java/org/owasp/webgoat/service/LessonPlanService.java | 57 | BAIXO VALOR: RequestMapping sem method em handler só de leitura |
| 7 | sentinel-node-eval-dinamico | src/main/webapp/js/deprecated/menu_system.js | 144 | VERDADEIRO: eval com parte da URL (DOM XSS) |
| 8 | sentinel-html-handler-inline | src/main/webapp/lesson_plans/en/RoleBasedAccessControl.html | 15 | BAIXO VALOR: handler inline com conteúdo estático |
| 9 | sentinel-fe-target-blank | src/main/webapp/lesson_plans/en/UsefulTools.html | 36 | BAIXO VALOR: target=_blank sem rel em links fixos |
| 10 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/CrossSiteScripting/EditProfile.jsp | 103 | VERDADEIRO: notas impressas sem escape em textarea (XSS armazenado) |
| 11 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/CrossSiteScripting/ListStaff.jsp | 13 | FALSO: getFormAction() é valor interno do servidor |
| 12 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/CrossSiteScripting/ListStaff.jsp | 31 | FALSO: constante literal SEARCHSTAFF_ACTION |
| 13 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/DBCrossSiteScripting/ListStaff.jsp | 22 | VERDADEIRO: nome/papel do banco sem escape (XSS armazenado) |
| 14 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/DBCrossSiteScripting/ViewProfile.jsp | 45 | BAIXO VALOR: campo de data improvável de carregar payload |
| 15 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/GoatHillsFinancial/EditProfile.jsp | 40 | VERDADEIRO: telefone String sem escape em atributo |
| 16 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/GoatHillsFinancial/EditProfile.jsp | 121 | FALSO: constante literal VIEWPROFILE_ACTION |
| 17 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/GoatHillsFinancial/ViewProfile.jsp | 103 | FALSO: getManager() devolve int |
| 18 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/RoleBasedAccessControl/error.jsp | 10 | FALSO: getFormAction() é valor interno do servidor |
| 19 | sentinel-xss-jsp-unescaped-output | src/main/webapp/lessons/SQLInjection/EditProfile.jsp | 22 | VERDADEIRO: sobrenome editável sem escape em atributo |
| 20 | sentinel-xss-jsp-unescaped-output | src/main/webapp/main.jsp | 89 | FALSO: valores numéricos do servidor |
