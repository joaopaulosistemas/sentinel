# Desenvolvimento orientado por especificação (SDD) no Sentinel

Guia curto de como o Sentinel usa o **GitHub Spec Kit** com o Claude. A ideia é simples: antes de pedir código, escreve-se **o que** a funcionalidade deve fazer, o Claude ajuda a achar as dúvidas, e só depois vêm plano, tarefas e implementação. A spec fica no repositório, versionada junto com o código, e o PR aponta para ela.

Nada precisa ser instalado na máquina do João. Os comandos rodam dentro do Claude (sessão na nuvem ou Claude Code), e a verificação roda no CI do GitHub.

## Onde ficam as coisas

| Caminho | O que é |
|---|---|
| `.specify/memory/constitution.md` | A **constituição**: os 9 princípios do Sentinel que toda spec, plano e tarefa precisam respeitar |
| `.specify/templates/` | Modelos de `spec.md`, `plan.md`, `tasks.md` e checklist |
| `.specify/scripts/bash/` | Scripts auxiliares que os comandos usam (numerar funcionalidade, criar branch, achar caminhos) |
| `.claude/skills/speckit-*/` | Os comandos do Spec Kit, instalados como skills do Claude |
| `specs/NNN-nome/` | Uma pasta por funcionalidade: `spec.md`, `plan.md`, `tasks.md` e anexos |
| `docs/pull_request_template.md` | Modelo de PR que pede o link da spec e a conferência da constituição |

Versão do kit usada para gerar estes arquivos: 1.4.1 (nesta versão os comandos se chamam `/speckit-specify`, com hífen, e a opção `--ai` do instalador virou `--integration`).

## O fluxo, passo a passo

| # | Comando | O que faz | Quem decide |
|---|---|---|---|
| 1 | `/speckit-constitution` | Cria ou atualiza a constituição. Já está pronta; só se usa para emendar | João |
| 2 | `/speckit-specify <descrição>` | Cria `specs/NNN-nome/spec.md` com histórias de usuário, requisitos e critérios de sucesso. **Só o quê e o porquê, sem tecnologia** | João revisa |
| 3 | `/speckit-clarify` | O Claude pergunta o que ficou ambíguo (os `[NEEDS CLARIFICATION]`) e grava as respostas na spec | João responde |
| 4 | `/speckit-plan` | Escreve `plan.md`: o **como** (módulos, dados, contratos) e a conferência com a constituição | João revisa |
| 5 | `/speckit-tasks` | Quebra o plano em tarefas ordenadas, com os testes de cada fatia | João revisa |
| 6 | `/speckit-analyze` | Confere se spec, plano e tarefas dizem a mesma coisa, antes de escrever código | automático |
| 7 | `/speckit-implement` | Executa as tarefas, uma fatia por vez | Claude, com CI |
| 8 | `/speckit-converge` | Depois de implementado, compara o código com a spec e acrescenta o que faltou como novas tarefas | Claude |

`/speckit-checklist` (opcional) gera listas para validar se os requisitos estão completos e claros.

**Regra prática:** a spec descreve o que o usuário vê e o que precisa ser verdade. Palavras como "Quarkus", "tabela" ou "endpoint" pertencem ao plano.

## Como isso se liga ao GitHub

1. **Uma issue por funcionalidade**, com o título `[SPEC] nome` e o link para `specs/NNN-nome/spec.md`. A issue serve para discutir e acompanhar; o texto de verdade fica no arquivo.
2. **Uma branch por funcionalidade**, com o nome da pasta (`001-separar-regras-front-back`). O Spec Kit cria esse nome para você.
3. **Um PR por fatia** (cada história de usuário que entrega valor sozinha). O modelo `docs/pull_request_template.md` pede: link da spec e da issue, critérios de aceite atendidos, testes incluídos e a lista dos princípios da constituição.
4. **O CI é o porteiro.** Cada critério de aceite deve virar um teste que o CI roda (Maven e Docker só rodam no runner do GitHub). Critério que não vira teste não está verificado, e o PR diz isso.
5. **Spec e código andam juntos.** Se o código divergir da spec, a spec é corrigida no mesmo PR, e `docs/evolucao/LOG.md` recebe a entrada com *Verificado* e *NÃO verificado*.
6. **Merge** só com CI verde e com a sua autorização.

## Quando usar, e quando não

- **Use** para funcionalidades com várias partes ou decisões: separar regras em front e back, novas camadas de análise (AST, taint), telas novas, mudanças em cadastro de sistemas.
- **Não use** para correção pequena, ajuste de texto ou bug óbvio: vá direto ao PR. O ciclo completo para uma linha de código é só burocracia.
- **O agente `sentinel-ciclo` continua à parte.** Ele mede e calibra as regras contra repositórios vulneráveis; as specs descrevem funcionalidades do produto. Uma volta do ciclo pode, no máximo, apontar uma lacuna que vira uma spec nova.

## Specs existentes

Estado lido dos `tasks.md` de cada pasta (tarefas marcadas `[x]` sobre o total):

| Spec | Assunto | Estado |
|---|---|---|
| `001-separar-regras-front-back` | Separar as regras em front-end e back-end (tela Motor de regras) | Implementada: 35 de 36 tarefas; falta a T039, rodar o `quickstart.md` numa instalação real |
| `002-documentar-api-openapi` | Documento OpenAPI da API | Em andamento: 21 de 24 tarefas; faltam o LOG e o merge dos PRs B e C (T015, T018) e a conferência manual no Swagger (T022) |
| `003-medicao-e-prova` | Medir e provar o ganho de cada mudança | Só escrita; nenhuma tarefa executada |
| `004-menos-alarme-falso` | Menos alarme falso e só o que é novo nos PRs | Só escrita; nenhuma tarefa executada |
| `005-modelo-estrutural-codigo` | Modelo estrutural do código | Só escrita; nenhuma tarefa executada |
| `006-rastreio-entre-arquivos` | Rastreio de dados entre arquivos (começando por injeção de SQL) | Só escrita; nenhuma tarefa executada |
| `007-arquitetura-verificavel` | Arquitetura verificável | Só escrita; nenhuma tarefa executada |
| `008-prova-externa` | Prova externa, com revisor de fora | Só escrita; nenhuma tarefa executada |

As specs 003 a 008 formam o plano "Sentinel Premium". Antes de implementar qualquer uma, rode `/speckit-analyze`.

## Atualizar ou regenerar o kit

Os arquivos de `.specify/` e `.claude/skills/speckit-*` foram gerados por `specify init --here --integration claude --script sh` (versão 1.4.1). Para atualizar, peça ao Claude para rodar o instalador numa pasta temporária, comparar com o que está aqui e abrir um PR só com a diferença, mantendo a constituição do Sentinel (`.specify/memory/constitution.md`) e as pastas de `specs/` como estão.

Referências: [GitHub Spec Kit](https://github.com/github/spec-kit) · [artigo do GitHub Blog](https://github.blog/news-insights/product-news/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/)
