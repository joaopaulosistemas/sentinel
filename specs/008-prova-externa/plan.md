# Plano de implementação: Sentinel Premium 6 — Prova externa

**Ramo**: `spec/008-plano` | **Data**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Resumo

Um script (`scripts/prova.py`) monta, a partir das medições da spec 003, um relatório de prova determinístico e repetível por terceiros: método, conjuntos com commit fixo, versões, números por pilha com intervalo de 95 %, limites, Verificado e NÃO verificado e histórico de correções. Em volta dele: amostra cega para um revisor externo e cálculo de concordância, comparação com duas ferramentas de mercado que leem o fonte sem compilar, a única medição final do reservado por versão e o roteiro do aceite real no GitHub Code Scanning. O que é julgamento humano fica marcado como **ação humana**. Ver [research.md](research.md).

## Contexto técnico

- **Linguagem**: Python padrão (sem dependência nova), testado com `python3 -m unittest scripts/test_prova.py`, no estilo dos outros scripts.
- **Armazenamento**: arquivos versionados (`docs/evolucao/relatorios/`, `audit-rules/calibracao/mercado/`); nenhum banco.
- **Restrições**: nada de repositório auditado é executado nem compilado (VII); relatório privado por padrão; dados não publicáveis fora (FR-011).
- **Dependências abertas**: (1) **003 implementada** (relatório por conjunto, `amostra --cego`, registro do reservado): hoje só documentos; (2) specs 004 a 007 entregues e medidas; (3) revisor externo disponível; (4) **autorização do João** para `ci.yml`; (5) licença do conjunto de regras da comunidade; (6) ação do João no aceite real.

## Verificação da constituição

| Princípio | Estado | Como |
|---|---|---|
| I | ok | números vêm do motor e da régua; nenhuma IA decide o veredito externo |
| II | ok | `scripts/test_prova.py` antes do código; verificador do relatório é o teste do relatório |
| III | ok | reservado medido uma vez por versão, com registro; `verificar-separacao.py` estendido pela 003 |
| IV | ok | toda taxa com n e IC; "sem medida" sem estimativa; resultado desfavorável publicado; histórico de correções |
| V | ok | mapas de regra por ferramenta em arquivos de dados |
| VI | ok | sem analisador novo; só mede |
| VII | ok | só ferramentas que leem o fonte; CodeQL, SpotBugs e FindSecBugs fora (exigem compilar) |
| VIII | ok | script Python; nada a instalar no host |
| IX | **atenção** | `ci.yml` (job dedicado e linha de testes) pede o João; aceite real é ação dele |

Reavaliada após o desenho: sem violação; autorizações e ações humanas listadas.

## Qual job do CI prova cada critério

| Critério | Prova |
|---|---|
| SC-001 | `prova.py montar` determinístico (teste) e `reproduzir` por terceiro (**manual**, com instruções no relatório) |
| SC-002 | teste do cálculo com vereditos de teste; **só vale com o revisor real concluindo** (ação humana) |
| SC-003, SC-007 | `prova.py verificar` rodando em `scripts/test_prova.py` e, depois, sobre o relatório final |
| SC-004 | registro do reservado da 003; `prova.py verificar` confere uma medição por versão |
| SC-005 | job dedicado ou manual da comparação, resultado como artefato (**`ci.yml`: autorização**) |
| SC-006 | evidência anexada; execução real fora do CI (ação humana) |

## Estrutura

```text
specs/008-prova-externa/            plan, research, data-model, quickstart, contracts/
scripts/prova.py                    montar, verificar, amostra-externa, concordancia, comparar-mercado, reproduzir, aceite
scripts/test_prova.py               testes
scripts/fixtures/prova/             medidas e vereditos de teste (só de teste)
audit-rules/calibracao/mercado/     mapa regra→categoria por ferramenta (commitado antes da medição)
docs/evolucao/relatorios/           prova-<versão>.md/.json, prova-publicados.json, prova-historico.md
```

## Fatias (um PR cada, só depois de `/speckit-tasks`)

- **A**: esqueleto, `verificar` (seções, n e IC, publicabilidade, histórico) e `montar`. ⚠️ depende da 003.
- **B**: amostra cega e `concordancia` (precisão por regra, divergências).
- **C**: comparação de mercado (mapas, versões fixas, mesma definição de acerto).
- **D**: medição final do reservado e `reproduzir`. ⚠️ `ci.yml`.
- **E**: roteiro e modelo do aceite real.
- **F**: fechamento.

Nada é implementado nesta etapa: é só plano.
