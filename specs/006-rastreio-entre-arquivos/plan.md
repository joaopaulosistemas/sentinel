# Plano de implementação: Sentinel Premium 4 — Rastreio de dado inseguro entre arquivos

**Ramo**: `spec/006-plano` | **Data**: 2026-11-13 | **Spec**: [spec.md](spec.md)

## Resumo

Um motor de rastreio em Java, sobre o modelo estrutural da spec 005, segue o dado de uma origem externa até uma execução de SQL perigosa, atravessando métodos, classes e arquivos, e mostra o caminho passo a passo. Origens, destinos, sanitizadores e propagadores vivem em arquivos de dados, para que a segunda classe de falha entre sem mexer no motor. As regras de padrão e o Joern continuam; o rastreio só entra no padrão com o "fica" da régua. Tudo atrás de chave **por sistema, desligada**. Ver [research.md](research.md).

## Contexto técnico

- **Linguagem/pilha**: Java no `audit-api` (Quarkus; compila no JDK 26 e roda no JRE 25 da imagem). Os frameworks Spring e Quarkus acima são os dos repositórios **auditados**.
- **Armazenamento**: só o achado e o resumo de cobertura do rastreio com o resultado da auditoria; nenhuma tabela nova. Definições em `audit-rules/taint/`.
- **Testes**: JUnit com repositórios de teste em `audit-api/src/test/resources/rastreio/`; `scripts/benchmark-owasp.py` e o job `joern` do CI para a comparação.
- **Restrições**: sem executar nem compilar o auditado (VII); sem instalação no host (VIII); campo novo no achado é API pública (IX).
- **Dependências abertas**: (1) spec 005 (leitor Java) antes; (2) régua da 003 (hoje só documentos) para o veredito; (3) **autorização do João** para o campo `trace`/`sources` no achado (T023) e para mexer em `.github/workflows/ci.yml` no job `benchmark` (T035).

## Verificação da constituição

| Princípio | Estado | Como |
|---|---|---|
| I | ok | o rastreio é parte do motor; nada de IA na auditoria |
| II | ok | testes antes; classe nova com caso positivo e negativo; `semgrep --test` e paridade só para as regras de padrão que continuam |
| III | ok | sanitizadores e propagadores revistos só contra treino; reservado intocado; `verificar-separacao.py` |
| IV | ok | medida antes × depois com intervalo; sem régua não afirma ganho; LOG Verificado / NÃO verificado |
| V | ok | definições em dados (classe nova sem compilar); chaves por sistema em `.sentinel.yml`, nada em `application.properties` |
| VI | ok | só Java (Spring e Quarkus) na primeira fatia |
| VII | ok | só leitura; teste de varredura proíbe execução e compilação |
| VIII | ok | roda na JVM da imagem, sem Joern, sem ferramenta no host |
| IX | **atenção** | campo novo no achado: pedir ao João; `ci.yml` só se um job novo for preciso |

Reavaliada após o desenho: sem violação; uma autorização pendente.

## Qual job do CI prova cada critério

| Critério | Prova |
|---|---|
| SC-001, SC-004 | JUnit sobre repositórios de teste de 3 camadas (job de testes da API) |
| SC-002, SC-003 | `benchmark` (categoria `sqli` e demais) e job `joern` para a comparação; régua da 003 quando existir; **bloqueado** sem ela |
| SC-005 | JUnit dos rastros perdidos e do relatório de cobertura |
| SC-006 | teste que adiciona um arquivo de definição sem tocar o motor |
| SC-007 | tempo registrado por repositório (medição da 003) |
| FR-007 | JUnit da fusão de fontes |
| FR-003 | teste de esquema das definições |

## Estrutura

```text
specs/006-rastreio-entre-arquivos/   plan, research, data-model, quickstart, contracts/, segunda-classe.md (depois)
audit-rules/taint/                   sqli.yml (definição de classe)
audit-api/.../rastreio/              FluxoDeValores, Resumos, Motor, Caminho, Cobertura, FusaoDeFontes
audit-api/.../service/ProjectConfig  chaves taint.* (por sistema)
audit-api/.../local/LocalMain.java    comandos `rastreio cobertura` e `rastreio executar` (ponto de entrada local da 005; `audit-cli` não muda)
```

## Fatias (um PR cada, só depois de `/speckit-tasks`)

- **A**: definições em dados, esquema e carregador; configuração por sistema.
- **B**: camada de fluxo de valores sobre o leitor Java da 005; motor e achado de SQL (3 camadas). ⚠️ depende da 005.
- **C**: sanitizadores, propagadores, rastro perdido, limites e cobertura do rastreio.
- **D**: fusão de fontes com padrão e Joern; campo no achado ⚠️ autorização.
- **E**: comando `rastreio executar`, mapa de regras do Benchmark e medição (padrão × Joern × rastreio) ⚠️ `ci.yml`.
- **F**: segunda classe (só definições).
- **G**: fechamento.

Nada é implementado nesta etapa: é só plano.
