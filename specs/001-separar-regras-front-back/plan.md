# Plano de implementação: Escopo front-end e back-end, o que falta fechar

**Branch**: `001-separar-regras-front-back` | **Data**: 2026-11-12 | **Spec**: [spec.md](spec.md)

## Resumo

O lado (back, front, comum) de regras e analisadores e o desligamento por sistema já existem (Dashboard 12.48.0 e 12.49.0). Falta: (1) ferramentas externas dos pacotes com lado (FR-008), (2) selo no Ranking e nos relatórios (FR-009), (3) teste de guarda contra regra de código esquecida como "comum" (FR-010) e, opcionalmente, (4) o Opengrep não executar regras do lado desligado (FR-011). Abordagem em [research.md](research.md): reaproveitar `RuleSide`, `SystemTuning` e `AuditPolicy`, sem mudar banco nem cálculo da nota.

## Contexto técnico

**Linguagem/versão**: Java 21 (bytecode; imagem no JRE 25) com Quarkus 3.40; TypeScript no dashboard (React 19, Vite, IBM Carbon)
**Dependências principais**: as que já existem; nenhuma nova
**Armazenamento**: PostgreSQL 16; uma migração Flyway (V6) com coluna anulável em `audit_run`; o ajuste do sistema já guarda `disableSides`
**Testes**: JUnit na API (`audit-api/src/test/java/com/sentinel/audit/...`), `vitest` + Testing Library no dashboard, testes dos pacotes (`opengrep --test`, `scripts/verificar-regras.py`)
**Plataforma-alvo**: Docker (Compose e Helm); roda em WSL/Windows e no GitHub runner
**Tipo de projeto**: aplicação web (API Quarkus + dashboard React) mais pacotes de regras declarativos
**Desempenho**: sem meta nova; FR-011 (P3) é o único item de desempenho
**Restrições**: nota e achados não mudam com os dois lados ligados; contrato da API só ganha campo; sem executar nem compilar código auditado
**Escala/escopo**: 311 regras próprias em 16 pacotes (README), 13 ferramentas em 9 arquivos de `market-tools/tools`, 9 telas no dashboard; a mudança toca poucas classes Java, a página do Ranking e os serviços de relatório

## Conferência da constituição

*Gate: precisa passar antes da pesquisa e ser reavaliada depois do desenho. Estado: **passa**.*

| Princípio | Como o plano cumpre |
|---|---|
| I. Motor de regras é a fonte da verdade | Nada muda no papel do Claude/MCP; o lado é dado do motor |
| II. Teste junto com o código | Cada história tem testes próprios (tabela em `research.md` R7); o guarda de FR-010 é um teste novo |
| III. Treino × validação | Nenhuma regra de detecção muda; `verificar-separacao.py` continua valendo |
| IV. Honestidade nos números | O selo existe exatamente para não comparar notas incomparáveis; LOG com Verificado / NÃO verificado |
| V. Plug-and-play e configuração por sistema | `side` opcional no YAML, dedução como padrão; o interruptor continua por sistema |
| VI. Escopo de tecnologias | Sem nova linguagem |
| VII. Código auditado é dado | Nada executa código dos alvos |
| VIII. Roda onde o João roda | Verificação no CI; sem exigir ferramenta local |
| IX. PRs pequenos e reversíveis | 3 PRs (abaixo); mudança de contrato de API só aditiva, sinalizada no PR |

Nenhuma violação: a tabela **Complexity Tracking** fica vazia.

## Estrutura do projeto

### Documentação desta funcionalidade

```text
specs/001-separar-regras-front-back/
├── spec.md
├── plan.md              # este arquivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api-sistemas.md
│   └── pacote-ferramentas.md
└── tasks.md             # saída de /speckit-tasks (ainda não criado)
```

### Código-fonte (caminhos reais do repositório)

```text
audit-api/src/main/java/com/sentinel/audit/
├── pack/ToolSpec.java, ToolSpecParser.java   # chave `side` (FR-008)
├── pack/AuditPolicy.java                     # `toolOffNote`, filtro por arquivo para o jscpd
├── analyzer/SarifAnalyzer.java               # pula ferramenta de lado desligado e registra no resumo
├── analyzer/SarifResumo.java                 # linha "lado desligado"
├── model/AuditRun.java                       # `disabledSides` gravado quando a auditoria começa (FR-009)
├── dto/SystemSummaryResponse.java            # campo `disabledSides` (FR-009)
├── resource/SystemViews.java                 # preenche o campo a partir da última auditoria concluída
├── service/ReportService.java, PdfReportService.java, PortfolioReportService.java   # selo nos relatórios
└── analyzer/SemgrepAnalyzer.java             # só na história 4 (P3)

audit-api/src/test/java/com/sentinel/audit/pack/   # RuleSideTest (guarda), ToolSpecParser e SarifAnalyzer
audit-rules/packs/market-tools/tools/              # `side:` em njsscan, squawk, sqlfluff, jscpd
audit-dashboard/src/pages/Ranking/RankingPage.tsx  # selo
audit-dashboard/src/pages/Reports/                 # selo, se a tela mostrar a nota
audit-dashboard/src/types/                         # `disabledSides` no tipo do sistema
docs/evolucao/LOG.md, docs/referencia/pacotes-de-regras.md   # registro e documentação da chave `side`
```

**Decisão de estrutura**: aplicação web existente (API + dashboard + pacotes); nenhum módulo novo. Reaproveita `RuleSide` como única fonte da dedução de lado, em vez de uma segunda lógica para ferramentas.

## Fatias de entrega (um PR cada)

| PR | Histórias | Conteúdo | Prova no CI |
|---|---|---|---|
| A | 2 e 3 (FR-008, FR-010) | `side` em `ToolSpec`/`ToolSpecParser`, skip no `SarifAnalyzer`, `side:` nas 4 ferramentas, teste de guarda, LOG e docs | `API, CLI e pacotes de regras` |
| B | 1 (FR-009) | `disabledSides` no resumo, selo no ranking e nos relatórios, testes `vitest` e Java | `Dashboard`, `API, CLI e pacotes de regras` |
| C | 4 (FR-011), opcional | Opengrep sem as regras do lado desligado | `Calibração em vários repositórios`, `OWASP Benchmark (recall e precisão)` |

O PR C só entra se a verificação do `research.md` R6 mostrar um caminho seguro. O PR B inclui uma migração de banco (coluna anulável, V6) e uma mudança aditiva na API.

## Decisões tomadas por delegação do João (2026-11-12, "escolha a melhor")

1. **Selo do escopo usado na auditoria** (não do ajuste atual): cada `AuditRun` guarda os lados desligados (coluna nova anulável, migração V6). Auditoria antiga: sem selo. Motivo: a nota exibida é daquela auditoria; o ajuste atual poderia contradizê-la (princípio IV).
2. **Versão e contrato:** os PRs sobem a versão como os anteriores; `disabledSides` na API pública é mudança só aditiva, sinalizada no PR B.
3. **jscpd:** roda sempre (`common`) e descarta achados dos arquivos do lado desligado, igual à `Duplicação` interna (R4).

Se o João discordar de qualquer um dos três, a mudança é local: (1) afeta só o PR B; (2) é uma linha por PR; (3) afeta só o PR A.

## Complexity Tracking

Sem violações da constituição a justificar.
