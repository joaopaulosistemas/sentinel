# Padrão de código TypeScript / Angular

Este é o padrão de clean code da casa para TypeScript, transformado em verificações automáticas. Ele vale para módulos gerais (containers, components, services, routes, models, interfaces, enums, store), módulo shared e componentes atômicos.

## Como cada regra é verificada

| Regra do padrão | Quem verifica |
|---|---|
| Membro privado começa com `_`; público não tem `_` | Semgrep `sentinel-tsp-privado-sem-underscore`, `sentinel-tsp-publico-com-underscore` e ESLint `naming-convention` (inclui parâmetros do construtor) |
| Modificador de acesso explícito (`public`/`private`) | Semgrep `sentinel-tsp-metodo-sem-modificador` e ESLint `explicit-member-accessibility` |
| Hooks (`ngOnInit`...) só chamam métodos privados | Semgrep `sentinel-tsp-ciclo-de-vida-com-logica` |
| Enum no singular terminado em `Enum`, membros `MAIUSCULAS_COM_UNDERLINE` | Semgrep `sentinel-tsp-enum-sem-sufixo` e ESLint `naming-convention` |
| Interface `IConta` ou `ContaInterface` | Semgrep `sentinel-tsp-interface-sem-padrao` e ESLint `naming-convention` |
| Sem abreviações (`num`, `cod`, `desc`, `qtd`, `vlr`, `dt`) | Semgrep `sentinel-tsp-abreviacao` |
| Método até 50 linhas, no máximo 10 ifs (complexidade), aninhamento 3, arquivo 500 linhas | `CleanCodeAnalyzer`, com os limites do perfil `padrao-typescript-angular` |
| Todo método declara o retorno; sem `any` | ESLint `explicit-function-return-type` e `no-explicit-any`; regra Semgrep `sentinel-ts-any-explicito` |
| Ordem: campos, construtor, métodos | ESLint `member-ordering` |
| Sem linha em branco dentro de método; uma linha entre métodos | ESLint `padding-line-between-statements`, `lines-between-class-members` |
| `===` sempre; sem `debugger`; `console` só `warn`/`error` | ESLint `eqeqeq`, `no-debugger`, `no-console` |

Não dá para verificar por máquina: método pequeno e coeso, nome que começa com verbo, ordem dos métodos na sequência em que o pai os chama, e o dicionário de nomes do domínio. Esses itens ficam para a revisão de código.

## Como usar

**Nos seus projetos Angular:** copie `eslintrc.json` desta pasta para `.eslintrc.json` na raiz do projeto. É o seu arquivo, com os ajustes da seção abaixo. Precisa de `eslint@8`, `@typescript-eslint` (parser e plugin), `@angular-eslint`, `prettier` e `eslint-plugin-prettier`.

**No Sentinel:**
1. Escolha o perfil `padrao-typescript-angular` no sistema (liga o pacote `ts-clean-code` e fixa os limites).
2. Para o lint de estrutura e nomes, construa a imagem com `WITH_ESLINT=true` e defina `ESLINT_CONFIG=/opt/eslint/eslint.padrao-ts.config.mjs` no `.env`. Depois `docker compose up -d --build`.
3. Adoção gradual em sistema legado: `rules: { "sentinel-tsp-*": { shadow: true } }` no perfil faz os achados aparecerem sem pesar no score.

## Conflitos encontrados no documento original (e o que adotei)

| Conflito | Decisão |
|---|---|
| Enum: o texto manda terminar em `Enum` (`TipoPessoaEnum`), a configuração exigia prefixo `E` | Sufixo `Enum` (todos os exemplos já usam) |
| Interface: o texto usa `StatusInterface`, os exemplos usam `IConta...`, a configuração exigia `I` | Aceita os dois; escolha um por projeto |
| Construtor: o texto usa `public constructor(`, a configuração proíbe `public` nele | Vale a configuração (sem `public`) |
| Abreviações: o dicionário proíbe `num`/`cod`, mas os exemplos usam `numContaCorrente` e `codTarifaLegado` | Regra mantida; os exemplos do documento precisam ser renomeados |
| `console.log`: vários exemplos usam, a configuração proíbe | Vale a configuração |
| `.subscribe(value => ...)` no exemplo de ciclo de vida, `{ next, error }` na seção de services | Não imposto, o padrão não decide |
| `padding-line-between-statements` com `never` entre quaisquer statements proibiria linha em branco também entre declarações no topo do arquivo | Exceção para `export`, `function` e `class`; `import` seguido de qualquer coisa exige linha em branco |
| `@typescript-eslint/lines-between-class-members` não existe mais no typescript-eslint 8 | Trocado pela regra equivalente do ESLint (`lines-between-class-members`) |
| `ignorePatterns: ["projects/**/*"]` esconde as bibliotecas de um workspace Angular | Removido; volte a incluir se não quiser lintar `projects/` |
| Tamanho: o texto diz "ideal menos de 20 linhas, máximo 50" | Só o máximo (50) é imposto; para impor o ideal, use `max-function-lines: 20` no perfil |

A classe "modelo" do próprio documento (seção de exemplo) quebra o padrão: tem `private idInstituicao` sem underscore, `any`, `console.log`, `numContaCorrente` e métodos privados sem `_`. Rodando o pacote `ts-clean-code` nela, saem 11 achados (9 privados sem underscore e 2 abreviações), fora o que o ESLint acrescenta. Vale corrigi-la, já que as pessoas copiam dali.

Atrito conhecido: `explicit-function-return-type` também reclama de callbacks em propriedades de objeto (`next: data => {...}`), como nos exemplos do documento. Se incomodar em `subscribe({ next, error })`, é a regra que precisa de decisão: exigir o retorno ali ou aceitar a inferência.
