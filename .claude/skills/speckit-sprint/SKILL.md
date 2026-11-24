---
name: "speckit-sprint"
description: "Monte o roteiro de fatias das specs (um cartão por fatia), com quadro kanban e, se configurado, sprints, a partir dos tasks.md existentes."
argument-hint: "Opcional: specs a incluir (ex.: 003 005), ou 'sprints' para ligar sprints nesta execução"
compatibility: "Requires spec-kit project structure with .specify/ directory"
metadata:
  author: "sentinel"
  source: ".specify/templates/sprint-template.md"
user-invocable: true
disable-model-invocation: false
---

## Entrada do usuário

```text
$ARGUMENTS
```

Considere a entrada antes de continuar (se não estiver vazia).

## Objetivo

Transformar os `tasks.md` das specs em um **roteiro único e ordenado de fatias** (cada fatia é um PR pequeno), com um cartão por fatia para o quadro kanban. Sprints e estimativa de esforço são **opcionais** e vêm desligadas por padrão (`.specify/sprint-config.yml`).

O comando pode ser executado a qualquer momento, inclusive agora, enquanto nada está implementado, e de novo depois de cada fatia fechada: o status dos cartões é recalculado a partir das caixas marcadas dos `tasks.md`.

## Restrições

- **Escreve só** os dois arquivos de `saida` da configuração (roteiro `.md` e cartões `.csv`) e uma linha de resumo em `docs/evolucao/LOG.md`. **Nunca** altera `spec.md`, `plan.md`, `tasks.md`, código, `.github/` nem `.env*`.
- **Nunca** marca tarefas, cria issues, cria PRs nem chama a API de Projects. A criação do quadro e a importação do CSV são do João.
- Não estima por conta própria: sem `estimativa.habilitada`, nenhum número de esforço aparece.
- Respeita a constituição (`.specify/memory/constitution.md`): em especial o princípio IX (mudança pequena, perguntar antes de tocar em áreas protegidas).

## Passos

1. **Verificações antes** (ganchos): se `.specify/extensions.yml` existir, leia `hooks.before_sprint`; processe como os demais comandos (ignore ganchos com `enabled: false`; ganchos opcionais são apenas oferecidos, os obrigatórios são executados). Se não existir, siga em silêncio.

2. **Configuração**: leia `.specify/sprint-config.yml`. Se faltar, use os valores padrão do próprio arquivo de exemplo: kanban ligado, sprints e estimativa desligadas. O argumento `sprints` liga sprints só nesta execução.

3. **Descobrir as specs**: liste `specs/*/tasks.md` (limite aos números dados em `$ARGUMENTS`, se houver). Spec sem `tasks.md` entra no resumo como "sem tarefas ainda".

4. **Extrair fatias**: para cada `tasks.md`, leia os cabeçalhos `## Fase ... (fatia X)` e as tarefas `- [ ] Txxx` ou `- [X] Txxx` de cada um. Fases sem letra de fatia (por exemplo "Fase extra") são anexadas à fatia que o texto da seção "Dependências" indicar; se não indicar, vão para uma fatia própria marcada "a confirmar". Para cada fatia registre: spec, letra, entrega (título da fase), intervalo e contagem de tarefas, feitas, e quantas têm ⚠️ (autorização) ou 👤 (ação humana).

5. **Dependências**: leia a seção "Dependências" de cada `tasks.md` e a "Dependências" do `plan.md`. Monte o grafo fatia→fatia e spec→spec **só com o que está escrito**. Dependência que não for explícita vai para "a confirmar", nunca é inventada.

6. **Ordenar**: ordenação topológica pelas dependências; empates seguem `ordem-specs`. Detecte ciclos e fatias que dependem de uma posterior: registre como problema em "Verificações do comando".

7. **Status por fatia**: `Feito` se todas as tarefas estão marcadas; `Em andamento` se algumas; `Aguardando você` se a próxima tarefa é ⚠️ ou 👤 ou se há bloqueio externo da configuração que a afeta; `Pronto` se as dependências estão `Feito` e ela não começou; senão `Backlog`. Respeite `kanban.limite-em-andamento`: se houver mais fatias em andamento que o limite, registre aviso.

8. **Sprints (opcional)**: só se `sprint.habilitada` (ou argumento `sprints`). Preencha sprints em ordem, `fatias-por-sprint` por sprint, sem partir fatia e sem pôr fatia antes de suas dependências. Se `estimativa.habilitada`, some os pesos por tipo de tarefa e respeite `capacidade-por-sprint`; senão, nenhum número de esforço.

9. **Gerar**: use `.specify/templates/sprint-template.md` e escreva o roteiro. Preserve integralmente a seção "Notas do João" do arquivo existente. Gere o CSV com as colunas `Spec,Fatia,Titulo,Entrega,Tarefas,Feitas,Depende de,Bloqueio,Status,Sprint` (UTF-8, vírgula, aspas nos campos com vírgula); `Sprint` fica vazio sem sprints.

10. **Verificar**: toda tarefa pertence a exatamente uma fatia; contagens do resumo batem com os `tasks.md`; nenhuma dependência aponta para fatia posterior; o CSV tem uma linha por fatia. Se algo falhar, corrija ou liste em "Verificações do comando"; não declare sucesso com item pendente.

11. **Registrar**: acrescente uma entrada curta em `docs/evolucao/LOG.md` (antes da linha `## Documentação alinhada ao código da 12.84.0 (sem versão nova)`), no padrão **Verificado / NÃO verificado** do projeto.

12. **Verificações depois** (ganchos): leia `hooks.after_sprint` em `.specify/extensions.yml`, se existir, e processe como no passo 1.

## Relatório final

Informe: arquivos gravados, número de fatias por spec, quantas estão `Aguardando você` e por quê, problemas das verificações, e a sugestão do próximo passo (por exemplo, importar o CSV no quadro do GitHub Projects ou liberar a próxima fatia).
