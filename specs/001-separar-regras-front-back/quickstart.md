# Guia de validação (quickstart): escopo front-end e back-end

Cenários para provar que a funcionalidade funciona. A sessão do Claude na nuvem não roda Maven nem Docker: o que for de Java e do dashboard é provado pelo CI do PR; o que for de tela real fica para o João, com Docker na máquina dele.

## Pré-requisitos

- Sentinel no ar (`docker compose up -d --build`, ver `docs/guia/instalacao.md`).
- Um sistema cadastrado com código Java e arquivos de estilo (`*.scss`), por exemplo uma pasta local.

## Cenário 1: selo no ranking e no relatório (história 1)

1. Na aba Motor de regras do sistema, escolher o atalho "Só back-end" e salvar.
2. Rodar uma auditoria.
3. Abrir Ranking de Sistemas. **Esperado**: a linha do sistema mostra o selo "front-end desligado" ao lado da nota.
4. Gerar o relatório do sistema e o de todos os sistemas. **Esperado**: o selo aparece nos dois.
5. Voltar para "Full-stack", auditar de novo. **Esperado**: sem selo, e a nota é a mesma que o cálculo atual dá para o mesmo código.

Prova no CI: testes `vitest` do ranking e dos relatórios; teste Java do `SystemViews.summary` (contrato em `contracts/api-sistemas.md`).

## Cenário 2: ferramentas externas acompanham o lado (história 2)

1. Com "Só back-end", auditar o sistema com `*.scss`.
2. **Esperado**: no resumo da auditoria, `stylelint` e `dependency-cruiser` aparecem como "lado front-end desligado", e nenhum achado deles existe.
3. `checkstyle` e `njsscan` rodam (lado back); `hadolint`, `squawk` e `sqlfluff` rodam sempre.

Prova no CI: testes de `ToolSpecParser` (chave `side`), de dedução por `applies-to` e do `SarifAnalyzer` com política de lado desligado.

## Cenário 3: guarda contra regra sem lado (história 3)

1. Criar, num pacote de teste, uma regra com `languages: [java, typescript]` e sem `metadata.side`.
2. Rodar os testes dos pacotes. **Esperado**: falha com o id da regra.
3. Acrescentar `metadata.side: back` na regra. **Esperado**: passa.

Prova no CI: o próprio teste de guarda, mais o `RuleSideTest` ampliado.

## Cenário de regressão (todas as histórias)

1. Auditar o mesmo repositório antes e depois com os dois lados ligados. **Esperado**: mesmos achados e mesma nota.

Prova no CI: jobs `Calibração em vários repositórios` e `OWASP Benchmark (recall e precisão)`.
