# Padrão Google (Java e TypeScript/Angular) + SEI CERT e Sonar

Este padrão usa guias públicos como fonte, transformados em verificações automáticas:

| Guia | Escopo | Quem verifica |
|---|---|---|
| [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) | Java: nomes, formatação, imports, chaves, Javadoc | Checkstyle com `audit-rules/lint/checkstyle/google-checks.xml` (pacote `java-google-style`) |
| [SEI CERT Oracle Coding Standard for Java](https://wiki.sei.cmu.edu/confluence/display/java) | Java: robustez e segurança | Pacote `java-cert-sonar` (Semgrep) |
| [Sonar (regras Java)](https://rules.sonarsource.com/java/) | Java: robustez e segurança | Pacote `java-cert-sonar` (Semgrep), mais PMD e `core-*` |
| [Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html) | TypeScript: nomes, visibilidade, tipos, exports, controle de fluxo | ESLint com `audit-rules/lint/eslint/eslint.google-angular.config.mjs` |
| [Angular Style Guide](https://angular.dev/style-guide) | Angular: nome de arquivo, seletores, `inject()`, outputs readonly | A mesma configuração do ESLint |
| Sonar (`eslint-plugin-sonarjs`) | TypeScript/JavaScript: robustez e segurança | A mesma configuração do ESLint |

## Como usar

**Java:** escolha o perfil `padrao-google-java` no sistema (liga `java-google-style` e `java-cert-sonar`). Construa a imagem com `WITH_MARKET_TOOLS=true` (padrão), que instala o Checkstyle.

**TypeScript/Angular:** escolha o perfil `padrao-google-typescript-angular` e, no `.env`:

```
WITH_ESLINT=true
ESLINT_CONFIG=/opt/eslint/eslint.google-angular.config.mjs
```

Depois `docker compose up -d --build`. Só uma configuração de ESLint vale por vez (`ESLINT_CONFIG` é da instalação, não do sistema).

**Este perfil e o `padrao-typescript-angular` (padrão da casa) se contradizem. Use um ou outro, nunca os dois no mesmo sistema:**

| Ponto | Padrão da casa | Google TypeScript |
|---|---|---|
| Membro privado | começa com `_` | proibido `_` no começo ou no fim |
| Modificador de acesso | `public` explícito | sem a palavra `public` (exceto propriedade de parâmetro) |
| Sufixo `Enum` | obrigatório | sem regra (valores em `CONSTANT_CASE`) |
| Interface | `IConta` ou `ContaInterface` | sem prefixo `I` |

## O que é verificado

### Java: Google Java Style (`checkstyle-google`)

Nomes (pacote, tipo, método, constante, campo, campo estático, parâmetro, variável local, parâmetro de tipo, record), siglas como palavra (`HttpUrl`), recuo de 2 espaços, 100 colunas, chaves obrigatórias em estilo K&R, ordem de imports (estáticos primeiro, ordem ASCII), uma classe de topo por arquivo, sobrecargas juntas, `@Override`, `catch` vazio, linhas em branco entre membros, espaços, Javadoc (obrigatório em tipo e método públicos, resumo, ordem das tags).

Os módulos que o `checks.xml` padrão já executa (`AvoidStarImport`, `UnusedImports`, `MissingSwitchDefault`, `FallThrough`, `UpperEll`, `ModifierOrder`, `OneStatementPerLine`, `MultipleVariableDeclarations`, `ArrayTypeStyle`, `NoFinalizer`, `RedundantImport`) não se repetem: o perfil mantém as duas ferramentas ativas, e o mesmo problema não é contado duas vezes. Os achados de estilo saem com severidade baixa.

Dica: o [google-java-format](https://github.com/google/google-java-format) aplica quase toda a formatação sozinho.

### Java: SEI CERT e Sonar (`java-cert-sonar`, 11 regras Semgrep)

| Regra | CERT | Sonar |
|---|---|---|
| `sentinel-cert-interrupted-engolida` | THI05-J | S2142 |
| `sentinel-cert-catch-npe` | ERR08-J | S1696 |
| `sentinel-cert-throw-generico` | ERR07-J | S112 |
| `sentinel-cert-thread-run-direto` | THI00-J | S1217 |
| `sentinel-cert-thread-stop` | THI05-J | |
| `sentinel-cert-wait-fora-de-loop` | THI03-J | S2274 |
| `sentinel-cert-sync-em-string-ou-boxed` | LCK01-J | S1860 |
| `sentinel-cert-retorno-ignorado-arquivo` | EXP00-J | S899 |
| `sentinel-cert-locale-implicito` | STR02-J | S1449 |
| `sentinel-cert-charset-implicito` | STR04-J | |
| `sentinel-cert-securerandom-semente-fixa` | MSC02-J | S4347 |

O código de origem também está na `metadata` de cada regra (`cert`, `sonar`, `cwe`, `references`). Já estavam cobertos por outras regras do Sentinel e por isso não foram repetidos: zip-slip (IDS04-J), injeção de comando (IDS07-J), `System.exit` (ERR09-J), TLS, criptografia e `Random` previsível (`core-security`); retorno em `finally` (ERR04-J), recurso não fechado (FIO04-J) e `finalize` (MET12-J) ficam com PMD e Checkstyle.

### TypeScript/Angular (ESLint)

**Google TypeScript:** nomes (`lowerCamelCase`, `UpperCamelCase`, `CONSTANT_CASE`, sem `_`, sem prefixo `I`), sem `public`, `parameter-properties`, sem `any`, sem `!`, `as` em vez de `<T>`, `interface` em vez de `type` de objeto, `T[]`/`Array<T>`, sem tipos empacotados (`String`, `Number`), sem `namespace`/`require`, sem `@ts-ignore`, `const enum` e `#privado` proibidos, export nomeado (sem `default`), `const`/`let` (sem `var`), uma variável por declaração, `===`, chaves, `default` no `switch`, `throw` só de `Error`, aspas simples e ponto e vírgula.

**Angular:** nome de arquivo com hífen e sem nomes genéricos (`utils.ts`, `helpers.ts`; regra local `sentinel-angular/nome-de-arquivo`), `inject()` em vez de construtor, outputs `readonly`, seletor de componente em `kebab-case` e de diretiva em `camelCase`, sufixos de classe, interfaces de ciclo de vida, uma classe por arquivo. Os templates (`*.component.html`) seguem a configuração padrão do Sentinel (acessibilidade e boas práticas).

**Sonar:** 27 regras de `eslint-plugin-sonarjs` de segurança e robustez além das que a configuração padrão já liga (senhas e segredos fixos, protocolo sem TLS, comando do SO, `Math.random`, cookie e JWT inseguros, retorno ignorado, `catch` ignorado, `delete` em array, `Error` sem `throw`, entre outras). Entram em modo sombra no perfil: aparecem no relatório, mas só pesam no score depois de a precisão ser medida (`docs/evolucao/precisao.md`).

## O que não dá para verificar por máquina (fica para a revisão de código)

- Nome que descreve a intenção; método que começa com verbo; ordem lógica dos membros (Google Java 3.4.2).
- Escolha entre quebras de linha equivalentes (Google Java 4.5).
- Angular: marcar `protected` os membros usados só pelo template; nomear o handler pela ação (`saveUserData()`) e não pelo evento; manter a lógica do template simples; organizar por feature e não por tipo de arquivo.
- Google TypeScript: `import type` para tipos (não imposto, porque quebra a injeção por construtor com `emitDecoratorMetadata`); abreviações ambíguas; `Array()`/spread de primitivos; estado de classe nunca alterado após o construtor.
- Regras que dependem de informação de tipos (`only-throw-error`, `no-floating-promises`): o `EslintAnalyzer` roda sem `tsconfig`.

## Conflitos entre os guias e a decisão

| Conflito | Decisão |
|---|---|
| Nome de arquivo: Google TS manda `snake_case`, Angular manda hífen | Vale o Angular (`user-profile.component.ts`) |
| Constante de módulo: Google TS aceita `CONSTANT_CASE` e `lowerCamelCase` conforme o caso | Aceita as duas |
| Recuo e largura de linha: só o Google Java os define (2 espaços, 100 colunas); Google TS e Angular delegam ao formatador | Java: verificado pelo Checkstyle. TypeScript: tarefa do Prettier, não do lint |

## Adoção gradual em sistema legado

Estilo gera muitos achados no início. Para vê-los sem pesar no score, ponha no perfil:

```yaml
rules:
  "checkstyle-google-*": {shadow: true}
  "eslint-@typescript-eslint/naming-convention": {shadow: true}
```

e promova as regras (`shadow: false`) à medida que o código for ajustado.
