# Plano de implementação: Sentinel Premium 1 — Medição e prova

**Ramo**: `spec/003-medicao-e-prova` (specs) → PRs de implementação por fatia | **Data**: 2026-11-12 | **Spec**: [spec.md](spec.md)

## Resumo

A régua do Sentinel Premium vira uma ferramenta única, `scripts/medicao.py` (Python, biblioteca padrão), que consolida as medidas já existentes (OWASP Benchmark, recall por gabarito, precisão revisada) num relatório com intervalos e "sem medida", compara antes × depois com critérios registrados antes (veredito fica / sai / inconclusivo) e administra o conjunto **reservado**. Os dados vivem em JSON versionado em `audit-rules/calibracao/` e os relatórios em `docs/evolucao/medicao/`. Não muda Java, banco nem painel. Detalhes de escolha em [research.md](research.md).

## Contexto técnico

**Linguagem/versão**: Python 3 (a do runner do CI), somente biblioteca padrão; scripts executados com `python3 -I`.
**Dependências principais**: nenhuma nova. Reaproveita `git`, os scripts existentes e os formatos de gabarito e de medida.
**Armazenamento**: arquivos JSON e Markdown no repositório (ver [data-model.md](data-model.md)).
**Testes**: `unittest` em `scripts/test_medicao.py` e `scripts/test_verificar_separacao.py`, com dados de teste em `scripts/fixtures/medicao/`; rodam aqui e no job de testes dos scripts.
**Plataforma-alvo**: runner do GitHub Actions (medição com clone de repositórios) e esta sessão (lógica e testes).
**Tipo de projeto**: ferramenta de linha de comando dentro do repositório (sem serviço).
**Metas de desempenho**: gerar um relatório a partir de medidas já coletadas em menos de 1 minuto; teste unitário completo em menos de 30 s.
**Restrições**: determinístico (mesmas entradas, mesma saída); sem rede nos testes; sem executar código auditado; compatível com os scripts atuais (nenhum é alterado de forma incompatível).
**Escala/escopo**: dezenas de repositórios por conjunto, milhares de achados por repositório.

## Verificação da constituição

*Portão: passa antes da Fase 0 e foi reavaliado depois do desenho.*

| Princípio | Como este plano cumpre |
|---|---|
| I. Motor de regras é a fonte da verdade | Mede o motor; não cria regra nem altera resultados. O Claude só lê e sugere. |
| II. Teste junto com o código | Cada subcomando nasce com teste de unidade antes da implementação da fatia; critérios SC-002 a SC-004 viram testes (ver tabela abaixo). |
| III. Treino e validação separados | Estende a separação com o grupo reservado e novas checagens no mesmo script que já roda no CI. |
| IV. Honestidade nos números | Todo relatório traz Verificado / NÃO verificado, "sem medida" e intervalos; correções com histórico. |
| V. Plug-and-play | Repositório novo entra por dado (`repositorios.json`, gabarito), sem compilar. |
| VI. Escopo fixo | `pilhas` só aceita as do escopo da constituição. |
| VII. Código auditado é dado | Só análise estática; nada é executado; nada de empregador entra (Q1 da spec). |
| VIII. Roda onde o João roda | Python no CI e aqui; nada exigido na máquina dele. Cada critério tem job (tabela abaixo). |
| IX. Mudanças pequenas e reversíveis | Seis fatias, uma por PR. Mexer em `.github/` e escolher os repositórios reservados **pedem autorização do João** e estão marcados. |

**Complexity Tracking**: nenhuma violação a justificar.

## Qual job do CI prova cada critério

| Critério | Prova | Onde |
|---|---|---|
| SC-002 (repetição idêntica) | `test_medicao.py` roda o relatório duas vezes e compara byte a byte | job "API, CLI e pacotes de regras" (linha de testes de scripts) |
| SC-003 (total e intervalo, aviso de amostra pequena) | testes de formatação do relatório | idem |
| SC-004 (3 mudanças de teste, veredito certo) | teste com 3 casos sintéticos (melhora, piora, sem efeito) | idem |
| SC-005, SC-006 (reservado) | `verificar-separacao.py` estendido + `test_verificar_separacao.py` | job "Separação treino e validação" |
| SC-001, SC-007, SC-009 | workflow de medição (`medicao.yml`, **novo, exige autorização**) publica `linha-de-base` como artefato; CI existente continua verde | job novo + jobs atuais |
| SC-008 | `medicao.py revisao` sobre `revisao-humana.md` preenchido | manual (depende do João) |

## Estrutura do projeto

### Documentação desta funcionalidade

```text
specs/003-medicao-e-prova/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── cli.md          # subcomandos, argumentos, códigos de saída
│   └── arquivos.md     # formatos dos arquivos de dados e relatórios
└── tasks.md            # criado por /speckit-tasks
```

### Código-fonte (raiz do repositório)

```text
scripts/
├── medicao.py                      # NOVO: relatorio | comparar | reservado | amostra | revisao | lacunas
├── test_medicao.py                 # NOVO
├── test_verificar_separacao.py     # NOVO
├── verificar-separacao.py          # ESTENDIDO: checagens 5 a 8 (reservado)
├── auditoria-completa.sh           # ESTENDIDO: duração e arquivos no resumo
└── fixtures/medicao/               # NOVO: medidas e gabaritos de teste

audit-rules/calibracao/
├── conjuntos.json                  # ESTENDIDO: grupo "reservado"
├── repositorios.json               # NOVO: commit, origem, pilhas, grupo
├── codigo-real.json                # NOVO: lista de projetos reais para precisão
├── propostas/                      # NOVO: critérios por mudança
├── gabaritos/reservado/            # NOVO: só com fonte pública
└── reservado/consultas.json        # NOVO: registro só-acrescenta

docs/evolucao/
├── medicao/linha-de-base.{json,md} # NOVO (gerado)
├── estado.json                     # ESTENDIDO: hash do reservado
└── LOG.md                          # entrada por PR

.github/workflows/medicao.yml       # NOVO (exige autorização do João)
```

**Decisão de estrutura**: um único módulo Python para a lógica (importável nos testes por caminho) e os dados ao lado dos que já existem em `audit-rules/calibracao/`.

## Fatias (um PR cada)

| Fatia | Entrega | Histórias | Toca `.github/`? |
|---|---|---|---|
| A | `medicao.py` com `relatorio` e `lacunas` sobre medidas existentes, estatística e `repositorios.json` | US1, US5 | não |
| B | `comparar` + `propostas/` + veredito, com os 3 casos de teste | US2 | não |
| C | Grupo `reservado`, checagens 5 a 8, registro de consultas (a **lista dos repositórios é aprovada pelo João**) | US3 | não |
| D | `auditoria-completa.sh` com duração/arquivos e `medicao.yml` para medir treino, validação e o reservado uma vez (marco 0) e gerar a **linha de base** | US1, SC-001, SC-007 | **sim** |
| E | `amostra` e `revisao` (precisão em código real e concordância), `codigo-real.json` | US4 | não |
| F | Fechamento: LOG, PLACAR, documentação e spec atualizada | todas | não |

Ordem: A → B → C → D → E → F. A, B e C podem ser desenvolvidas aqui; D só se prova no GitHub; E depende do João para os vereditos.

## Riscos e o que NÃO se consegue verificar aqui

- Medição real (clonar repositórios, rodar a auditoria completa) só ocorre no GitHub.
- O CI não detecta leitura do reservado por pessoa ou agente fora do que fica versionado.
- SC-008 só fecha quando o João (e depois o revisor externo, spec 008) preencher vereditos.
- O reservado inicial precisa de gabarito de fonte pública; se um candidato não tiver, ele não entra.
