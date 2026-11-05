# Clean code em TypeScript (pacote `ts-clean-code-labs42`)

Regras automáticas baseadas no guia [clean-code-typescript](https://labs42io.github.io/clean-code-typescript/), do projeto labs42io. As regras e as mensagens foram escritas pelo Sentinel; o guia é a referência de quais boas práticas valem a pena checar.

- **Pacote:** `audit-rules/packs/ts-clean-code-labs42` (21 regras `sentinel-cct-*`, todas com caso de teste e remediação).
- **Desligado por padrão.** Para ligar, escolha o perfil `clean-code-typescript` no sistema (ele traz os pacotes padrão e rebaixa as regras do guia para severidade baixa).
- **Não combina com** o perfil `padrao-typescript-angular` (pacote `ts-clean-code`): os dois são conjuntos de estilo diferentes e podem se sobrepor.
- **Verificação:** `semgrep --test` passa nas 21 regras, e Opengrep e Semgrep dão o mesmo resultado (`scripts/paridade-motores.py`).

## O que o pacote verifica

| Seção do guia | Regra | O que acusa |
|---|---|---|
| Variáveis | `sentinel-cct-nome-sem-significado` | Variável de uma letra (fora i, j, k, x, y) ou nome genérico (temp, aux, foo) |
| Variáveis | `sentinel-cct-numero-magico` | Número solto em comparação (3 ou mais) |
| Variáveis | `sentinel-cct-valor-padrao-com-ou` | Valor padrão do parâmetro dado com `\|\|` dentro da função |
| Funções | `sentinel-cct-parametros-demais` | Mais de dois parâmetros (construtores ficam de fora) |
| Funções | `sentinel-cct-parametro-booleano` | Parâmetro booleano (a função costuma fazer duas coisas) |
| Funções | `sentinel-cct-altera-parametro` | Função que muda o objeto ou a lista recebida |
| Funções | `sentinel-cct-escreve-no-global` | Escrita em `window`, `globalThis` ou em tipo nativo (`Array.prototype`) |
| Funções | `sentinel-cct-condicao-composta` | `if` com três ou mais partes na condição |
| Funções | `sentinel-cct-verificacao-de-tipo-em-cadeia` | Cadeia de `typeof` ou `instanceof` |
| Funções | `sentinel-cct-codigo-inalcancavel` | Código depois de `return` ou `throw` |
| Funções | `sentinel-cct-laco-com-indice` | `for (let i = 0; i < lista.length; i++)` |
| Erros | `sentinel-cct-throw-sem-error` | `throw` de texto, número ou objeto que não é `Error` |
| Erros | `sentinel-cct-catch-so-registra` | `catch` que só chama `console` |
| Erros | `sentinel-cct-promessa-rejeicao-ignorada` | `.catch(() => {})` e parecidos |
| Concorrência | `sentinel-cct-then-encadeado` | Promessa encadeada com `then`/`catch` |
| Concorrência | `sentinel-cct-parametro-callback` | Parâmetro chamado `callback`, `cb`, `done`, `onSuccess`, `onError` |
| Formatação | `sentinel-cct-import-relativo-profundo` | Importação com três ou mais `../` |
| Comentários | `sentinel-cct-codigo-comentado` | Linhas de código comentadas |
| Comentários | `sentinel-cct-comentario-de-diario` | Comentário com autor, data ou "alterado por" |
| Comentários | `sentinel-cct-marcador-de-posicao` | Linha de enfeite (`// ======`) |
| Testes | `sentinel-cct-teste-sem-nome-descritivo` | `it('works')`, `it('test1')` |

## O que o guia pede e o pacote não verifica

São pontos que dependem de entender o que o código quer fazer; não dá para checar por padrão de código sem gerar muito alarme falso. Ficam para a revisão humana (e para a revisão com Claude, quando ligada).

- **Nomes:** pronunciáveis, mesmo vocabulário para o mesmo conceito, sem contexto repetido (`Carro.carroMarca`), variáveis explicativas.
- **Funções:** fazer uma coisa só, nome que diz o que faz, um nível de abstração, sem código duplicado (o `jscpd` do pacote `market-tools` cobre a duplicação textual), evitar condicionais negativas por significado.
- **Objetos e classes:** classes pequenas, coesão e acoplamento, composição em vez de herança, `type` ou `interface` conforme o uso, imutabilidade.
- **SOLID e testes:** os cinco princípios, TDD, FIRST, um conceito por teste. Os testes do projeto são o lugar de olhar.
- **Formatação:** ordem das importações e proximidade entre quem chama e quem é chamado: o ESLint (`import/order`) é a ferramenta certa.
- **TODO:** o guia aceita `// TODO` para lembrar de melhorias; por isso não há regra contra eles.

## Limites conhecidos

- Os limites das regras de número, nome e parâmetros são escolhas do Sentinel (por exemplo, "três ou mais" partes na condição); ajuste pela severidade do perfil se o volume incomodar.
- A precisão ainda não foi medida em repositório real: use em modo de observação antes de pesar na nota (campo `shadow: true` no perfil).
