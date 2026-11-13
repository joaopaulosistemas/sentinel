# Plano de implementação: Menos alarme falso e só o que é novo no PR

**Branch**: `spec/004-plano` | **Data**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Resumo

O comentário do PR passa a mostrar só o que é novo ou piorou, com a faixa de confiança de cada regra e achados repetidos agrupados; código gerado deixa a nota quando não é segurança. A abordagem **estende o que já existe** (`Fingerprints`, `Baseline`, `ChangedFiles`, `GeneratedFiles`, `FindingConfidence`; ver [research.md](research.md)), com tudo atrás de chaves desligadas por padrão. Nenhuma tabela nova.

## Contexto técnico

- **Linguagens/pilha**: Java 17 e Spring Boot (`audit-api`), CLI (`audit-cli`), painel web, scripts Python (`scripts/`), regras YAML (`audit-rules/`).
- **Armazenamento**: banco atual; só leitura de colunas existentes. Arquivo novo versionado `audit-rules/calibracao/precisao-por-regra.json`.
- **Testes**: JUnit (`audit-api`), `python3 -m unittest scripts/test_*.py`, gabarito e OWASP Benchmark para a trava de recall.
- **Restrições**: chaves desligadas = saída idêntica à de hoje; recall não cai mais que 0,02; sem mudar contrato público sem autorização.
- **Dependência da spec 003**: a régua (treino e validação) foi revertida e só existe como especificação. O veredito para ligar qualquer chave como padrão é **manual** (gabarito e OWASP Benchmark atuais) até a régua existir. Nenhuma chave vira padrão neste plano.
- **Pendências de autorização do João**: parâmetro `groupBy` e campos novos na API (princípio IX); eventual linha nova em `ci.yml` (fatia F).

## Verificação da constituição

| Princípio | Estado | Como |
|---|---|---|
| I a III | ok | reaproveita o que existe; sem dependência nova |
| IV (prova) | ok | cada critério tem job abaixo; trava de recall |
| V | ok | chaves **por sistema** em `.sentinel.yml`/`ProjectConfig`, desligadas por padrão; nada em `application.properties` |
| VI | ok | sem pilha nova |
| VII | ok | aviso de base indecidível; nada omitido em silêncio |
| VIII | ok | LOG por PR, com Verificado / NÃO verificado |
| IX (pedir antes) | **atenção** | API pública (`groupBy`, campos) só com autorização do João |

Reavaliada após o desenho: sem violação; um item de autorização pendente.

## Qual job do CI prova cada critério

| Critério | Prova |
|---|---|
| SC-001, SC-002 (só o novo; movido, reformatado ou renomeado não conta) | testes JUnit de `Baseline`/`ChangedFiles`/`PrClassifier` + roteiro 1 do [quickstart](quickstart.md) |
| SC-003 (detecção não cai) | gabarito e OWASP Benchmark com chaves ligadas e desligadas, trava −0,02 |
| SC-004 (só entra no padrão com veredito "fica") | registro do veredito no LOG; chaves seguem desligadas até lá |
| SC-005 (meta de redução) | medida em código real depois da linha de base da 003; meta fixada pelo João |
| SC-006 (rebaixe cita amostra e precisão) | `scripts/precisao-regras.py --verifica` |
| SC-007 (agrupamento não muda a nota) | JUnit de listagem com `groupBy` comparando a nota |
| Faixa = precisão medida (FR-008) | teste do `precisao-regras.py --json` + JUnit de `RuleBandService` |
| Gerado fora da nota (FR-005) | JUnit de exclusão automática |
| Nada muda desligado (FR-011) | teste de regressão do comentário e do relatório |

## Estrutura

```text
specs/004-menos-alarme-falso/   plan, research, data-model, quickstart, contracts/
audit-api/.../service/          Baseline (estende), PrClassifier (novo), RuleBandService (novo), GeneratedFiles (usa)
audit-cli/                      MarkdownWriter --pr-comment
audit-rules/calibracao/         precisao-por-regra.json (gerado)
scripts/                        precisao-regras.py (--json, --verifica)
```

## Fatias (um PR cada, só depois de `/speckit-tasks`)

- **A**: chaves por sistema em `ProjectConfig`; `Baseline` com severidade e contagem; `ChangedFiles` com renomeação; decisão por arquivo (D1 a D4).
- **B**: `PrClassifier`, comentário do PR e SARIF (anotações)  (contrato `comentario-pr.md`).
- **C**: exclusão de não segurança em código gerado (D5).
- **D**: `precisao-por-regra.json` e `ruleBand` (D6).
- **E**: agrupamento e `groupBy` (**pede autorização**).
- **F**: verificação de rebaixe por precisão (D9).

Nada é implementado nesta etapa: é só plano.
