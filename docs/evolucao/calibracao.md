# Calibração das regras em repositórios públicos

Rodada de 2026-10-21. Semgrep com as regras dos pacotes `core-*` e `ts-clean-code` (o pacote `core-corporativo` entra em modo sombra no perfil corporativo).
Limite honesto: são repositórios **públicos** escolhidos por perfil, não projetos do João. Mede as regras, não o pipeline completo (API, pontuação e dashboard).

| Repositório | Perfil | Arquivos de código | Achados (antes → depois das correções) |
|---|---|---|---|
| spring-projects/spring-petclinic | Spring Boot + Thymeleaf | 62 | 134 → 80 |
| gothinkster/angular-realworld-example-app | Angular | 61 | 133 → 133 |
| gothinkster/react-redux-realworld-example-app | React | 39 | 2 → 2 |
| WebGoat/WebGoat | Java vulnerável de propósito | 498 (Java) | 5619 → 1609 |

## Falsos positivos encontrados e corrigidos
1. `sentinel-sql-select-estrela-arquivo`: marcava `SELECT *` dentro de `EXISTS (...)`, uso idiomático (42 achados no petclinic). Regra ajustada, com caso `ok` no teste.
2. `sentinel-fe-html-sem-lang`: marcava fragmentos Thymeleaf (`th:replace`, pasta `fragments/`) que não são páginas completas (12 achados). Regra ajustada.
3. Bibliotecas de terceiros embutidas (jQuery, Ace, wysihtml5 em `js/libs/`) geravam ~4.000 achados de estilo. O Semgrep agora ignora `node_modules`, `vendor(s)`, `third_party`, `bower_components`, `libs`, `*.min.js/css`, `*.bundle.js`, `*.pack.js` (`SemgrepAnalyzer.VENDORED_EXCLUDES`, com teste).

## Falhas de detecção encontradas e corrigidas (WebGoat)
1. `sentinel-desserializacao-insegura` não pegava `try (ObjectInputStream ois = new ...) { ois.readObject(); }` nem o parâmetro `ObjectInputStream`. Passou a usar tipo da variável; agora acha os 2 pontos reais do WebGoat.
2. Novo `sentinel-command-injection-shell`: `new ProcessBuilder("sh", "-c", cmd)` e `exec(new String[]{"sh","-c",cmd})`.

## Limites conhecidos
- O comando via `sh -c` com o argumento de shell guardado em variável (caso do WebGoat `CommandExecutionService`) continua sem detecção.
- Regras de estilo do pacote `ts-clean-code` (padrão TypeScript da empresa) geram muitos achados em projetos fora do padrão (51 em um só projeto Angular); o pacote vem desligado por padrão.
- Sem contagem de falsos negativos além dos casos conhecidos do WebGoat; não houve revisão manual de todos os achados.

## Segunda rodada: Frequency e Sentinel (repositórios do João)

Regras `core-*` sobre `joaopaulosistemas/frequency` (PWA em JavaScript) e `joaopaulosistemas/sentinel` (Java/React). Só relato: o Frequency não foi alterado.

| Repositório | Achados antes → depois |
|---|---|
| frequency | 125 → 88 |
| sentinel | 83 → 40 |

Falsos positivos corrigidos:
1. `sentinel-mem-colecao-estatica` (35 no Sentinel): a expressão atravessava `(){}` e marcava métodos `static` que retornam `List`. Agora só campos.
2. `sentinel-jpa-n-mais-um` (10 → 5): consulta no cabeçalho do `for`/`forEach`/`stream` não é N+1. Os 5 restantes são N+1 reais no código do Sentinel (`RuleQualityService`, `AuditScheduler`, etc.) e ficam como dívida a corrigir.
3. `sentinel-compose-docker-sock` e regras de Compose/K8s (4): liam os arquivos de remediação do próprio Sentinel. Pasta `remediation` excluída.
4. `sentinel-ts-catch-vazio` (37 no Frequency): `catch (_) {}` é a forma de ignorar de propósito e deixou de ser marcado.

Limites que ficam: `sentinel-xss-inner-html` marca 68 atribuições a `innerHTML` no Frequency sem saber se o valor passou por `esc()` (vale revisão manual, não é falso positivo comprovado); `sentinel-owasp-a01-endpoint-sem-autorizacao` marca recursos do Sentinel protegidos por filtro global (`ApiKeyFilter`).

## Terceira rodada: auditoria ponta a ponta no petclinic (CI real)

Auditoria completa pela API (todas as ferramentas, não só o Semgrep) rodando num contêiner no GitHub Actions: **1.498 achados, nota 42,6** num projeto de exemplo limpo. Isso é ruído, e a distribuição mostrou de onde vinha:

| Origem | Achados | Causa |
|---|---|---|
| Stylelint | 1.000 (limite da ferramenta; 4.049 reais) | `petclinic.css` é CSS compilado (a fonte é `petclinic.scss`) e a configuração `standard-scss` cobra formatação |
| SQLFluff | 241 (419 reais) | regras de layout, maiúsculas e alias de coluna em scripts de dados |

Correções:
- O formatador do Stylelint ignora `.css` cujo nome existe como `.scss/.sass/.less` no projeto (saída de compilação).
- A configuração do Stylelint desliga 44 regras de estilo (linhas em branco, notação de cores, padrões de nome, especificidade).
- O SQLFluff exclui `layout`, `capitalisation` e `AL03`.

Medido localmente com as mesmas ferramentas: Stylelint 4.049 → 26 achados (todos de regras de acessibilidade, `!important` e erros reais no SCSS); SQLFluff 419 → 133 (126 de AL03 antes da exclusão do AL03). A nota final da auditoria será conferida na próxima rodada do CI.

Resultado da rodada seguinte do CI (petclinic completo): 1.498 → **290 achados**; a nota ficou em 42,6 porque o cálculo já limita a penalidade de severidade baixa por regra (20 pontos), então o ruído de estilo inflava a contagem, mas quase não a nota. O que sustenta a nota são poucos achados HIGH e a diversidade de regras.

Mais um ajuste desta rodada: o ESLint deixa de cobrar `lang`, `title`, `meta`, fechamento de tags e níveis de título em templates de servidor (`templates/`, `fragments/`, `views/`), que são trechos de página. No petclinic, 53 achados nessa área caíram para 20, todos relevantes (rótulos de campo, estilos inline, imagens sem alt).
