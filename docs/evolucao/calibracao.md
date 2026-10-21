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
