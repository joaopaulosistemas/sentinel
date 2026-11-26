# Full Spectrum Audit: painel do Sentinel (todas as telas)

**Date**: 2026-11-26
**Mode**: Repo Health (código em `audit-dashboard/src`) + execução real com dados de amostra (Playwright, 1440 px e 390 px)
**Lenses Run**: 6/10 (design, UX, frontend, repo maintainer, performance, acessibilidade)
**Lenses Skipped**: Backend (sem Rust/Tauri), Structural (sem módulos novos), Production Readiness e DX (sem mudança de operação nem de CLI)
**Limites**: Guia e Achados quebram com a API simulada (formato errado de resposta) e não foram varridos em execução; foram lidos só no código. Sem leitor de tela real e sem medição de contraste no modo escuro.

## Master Verdict: APPROVE WITH CHANGES (aplicadas na PR #374)

As telas já tinham uma base boa (contraste medido, piso de leitura, sem rolagem horizontal em 390 px, um `h1` por tela, rótulos nos campos). As falhas eram sistêmicas e baratas: tabelas sem nome nem escopo, foco de teclado ausente em controles nativos, cores fixas fora dos tokens e o cartão ISO sem texto para leitor de tela.

## Verdict by Lens

| # | Lens | Verdict | Critical | Recommendations |
|---|------|---------|----------|-----------------|
| 1 | Design Engineer | APPROVE WITH CHANGES | 1 | 2 |
| 2 | UX Engineer | APPROVE WITH CHANGES | 0 | 3 |
| 3 | Frontend Engineer | APPROVE | 0 | 2 |
| 6 | Repo Maintainer | APPROVE WITH CHANGES | 0 | 2 |
| 9 | Performance Engineer | APPROVE | 0 | 0 |
| 10 | Accessibility Engineer | APPROVE WITH CHANGES | 3 | 3 |

## All Critical Issues (Must Fix)

| # | Issue | Lens | File(s) | Impact | Fix | Status |
|---|-------|------|---------|--------|-----|--------|
| 1 | 9 tabelas sem nome acessível e `<th>` sem `scope` (WCAG 1.3.1) | A11y | EngineRulesParts, McpPanel, ScoreExplanationPanel, FindingsTable, RecentAudits, InsightsPanel, SystemDetailPage, SystemsTable, IsoQualityPanel | Leitor de tela não anuncia a coluna da célula | `aria-label` + `scope="col"`; guarda em `a11y/tabelas.test.ts` | Corrigido |
| 2 | Sem foco visível nos controles nativos estilizados em linha (WCAG 2.4.7) | A11y | `leitura.css` | Quem usa teclado perde onde está | regra `:focus-visible` escopada | Corrigido |
| 3 | Variação ▲/▼ e situação só por símbolo/cor (WCAG 1.1.1, 1.4.1) | A11y | IsoQualityPanel | Leitor de tela lê "triângulo"; daltônico não separa bom de ruim | texto por extenso + palavra de situação | Corrigido |
| 4 | Cores fixas (#0f62fe, #f1c21b, #198038, #e8f5e9, #da1e28) fora dos tokens | Design | FindingsTable, ExecutionPanels, ResultSteps, McpPanel, EngineRulesParts, EnginePanel, RestartOverlay | Não acompanham o modo escuro | tokens `--s-*` | Corrigido |

## Cross-Cutting Concerns

| Concern | Flagged By | Root Cause | Unified Fix |
|---------|-----------|------------|-------------|
| Estilos soltos em linha repetidos em cada tela | Design, Repo Maintainer, Frontend | Cada painel redefine `cell`, `box`, cabeçalho de tabela | Extrair um componente de tabela com legenda e `scope` embutidos (não feito: mudança grande, fica como próximo passo) |
| Informação só por cor ou símbolo | A11y, UX | Faixas de nota e variações | `isoSituacao` e `isoDeltaSr`; replicar nos outros painéis de nota |

## All Recommendations

| # | Recommendation | Lens | File(s) | Fix | Status |
|---|---------------|------|---------|-----|--------|
| 1 | Link "Skip to main content" estava em inglês | A11y | AppShell | "Pular para o conteúdo" | Corrigido |
| 2 | Links do caminho (Início, Sistemas) com 16 px de altura (WCAG 2.5.8) | A11y | PageShell, leitura.css | `.sentinel-caminho a` com 24 px | Corrigido |
| 3 | Ranking pulava de `h1` para `h3` | A11y | RankingPage | `h2` | Corrigido |
| 4 | Botão do nome do sistema com 20 px de altura | A11y | SystemsTable | `minHeight: 24` | Corrigido |
| 5 | "Desde a anterior" vazio parecia erro | UX | IsoQualityPanel | "—" + "sem histórico para comparar" | Corrigido |
| 6 | Sem indicação de por onde começar | UX | IsoQualityPanel | faixa "Comece por aqui" | Corrigido |
| 7 | 5 linhas iguais de "não avaliada" poluíam a tabela | UX | IsoQualityPanel | bloco recolhido | Corrigido |
| 8 | Tabela de componente único com estilos duplicados | Repo Maintainer | vários | componente `DataTable` próprio | Aberto |
| 9 | Guia e Achados quebram quando a API devolve formato inesperado | Frontend | GuidePage, FindingsPage | validar o formato como o cartão ISO já faz | Aberto |

---

# Lens Reports (resumo por lente)

## 1. Design Engineer
**Verdict**: APPROVE WITH CHANGES. Tokens `--s-*` bem definidos para claro e escuro, mas 7 arquivos ainda usavam hex fixo (corrigido). Raios e espaçamentos variam entre painéis (aberto, depende do componente de tabela).

## 2. UX Engineer
**Verdict**: APPROVE WITH CHANGES. Fluxos completos e estados de carregamento e erro existentes. O cartão ISO deixava o leitor sem próximo passo (corrigido).

## 3. Frontend Engineer
**Verdict**: APPROVE. Hooks de dados centralizados, tipos fortes, 460 testes. Risco: telas que assumem o formato da resposta (rec. 9).

## 4–5. Backend, Structural
**Skipped**: sem mudança nesses eixos.

## 6. Repo Maintainer
**Verdict**: APPROVE WITH CHANGES. Convenções respeitadas (`sentinel-*`, tokens). Duplicação de estilos de tabela é o principal débito.

## 7–8. Production Readiness, DX
**Skipped**: sem mudança de operação nem de CLI.

## 9. Performance Engineer
**Verdict**: APPROVE. Sem novo estado global, listeners ou I/O; o cartão ISO faz 2 consultas, só com a auditoria concluída. Sem rolagem horizontal em 390 px.

## 10. Accessibility Engineer
**Verdict**: APPROVE WITH CHANGES (itens críticos 1 a 3 acima, todos corrigidos). Em execução real: 1 `h1` por tela, `main`/`nav`/`header` presentes, 0 imagens sem `alt`, 0 campos sem rótulo, 0 links sem nome. Os "botões sem nome" que a varredura listou eram modais fechados (falso positivo).
