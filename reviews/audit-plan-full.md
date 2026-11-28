# Auditoria completa (10 lentes): Sentinel, saúde geral do repositório

**Data**: 2026-11-28
**Modo**: Saúde geral do repositório (commit `1fbbcb2`, `main`)
**Fonte**: `audit-api` (Java/Quarkus), `audit-cli`, `audit-dashboard` (React/Carbon), `scripts`, `deploy`, `.github`, `docs`
**Lentes executadas**: 10/10 (a de Backend foi escrita para Rust/Tauri e rodou adaptada para Java/Quarkus)
**Lentes puladas**: nenhuma
**Substitui**: o relatório de 2026-11-26 (PR #374, só o painel, 6 lentes). O anterior continua no histórico do Git.
**Limites**: análise de código e de configuração, sem rodar o sistema. Não compilei o Java (Maven sem acesso ao BOM do Quarkus aqui) nem abri o painel em navegador. Onde a lente exigiria execução, está marcado "não verificado".

---

## Veredito geral: APROVADO COM MUDANÇAS

O repositório é saudável: 0 `any`/`@ts-ignore` no painel, 0 `catch` vazio no Java, testes em volume (140 arquivos de teste Java para 292 de código; 81 para 117 no painel), contêineres sem root com healthcheck, CSP e cabeçalhos no nginx, alertas e CI com limite de tempo. Os riscos reais são poucos e concentrados: ausência de `ErrorBoundary` no painel, estados visuais de botão e filtro que dependem de estilo em linha (1.044 objetos `style`), e um contrato API↔painel digitado à mão.

## Veredito por lente

| # | Lente | Veredito | Críticos | Recomendações |
|---|---|---|---|---|
| 1 | Design | Aprovado com mudanças | 2 | 5 |
| 2 | UX | Aprovado com mudanças | 1 | 4 |
| 3 | Frontend | Aprovado com mudanças | 1 | 5 |
| 4 | Backend (Java) | Aprovado com mudanças | 0 | 4 |
| 5 | Estrutura | Aprovado com mudanças | 0 | 4 |
| 6 | Mantenedor do repositório | Aprovado com mudanças | 0 | 5 |
| 7 | Prontidão para produção | Aprovado com mudanças | 0 | 5 |
| 8 | DX | Aprovado com mudanças | 0 | 4 |
| 9 | Desempenho | Aprovado | 0 | 3 |
| 10 | Acessibilidade | Aprovado com mudanças | 1 | 4 |

## Todos os problemas críticos (corrigir)

> Correção de 2026-11-28: a primeira versão listava aqui "configurações e reinício da API sem papel". Estava errado. `RolePolicy` manda `/api/v1/settings/*` (inclusive `/restart`) para `sentinel-admin`, o `RoleAuthorizationFilter` aplica e o `RolePolicyTest` cobre. Só o comentário em `SettingsResource` induzia ao erro.

| # | Problema | Lentes | Arquivo(s) | Impacto | Correção |
|---|---|---|---|---|---|
| 1 | Não há `ErrorBoundary`. Um erro de renderização em qualquer tela derruba o painel inteiro (tela branca). | Frontend, UX, Produção | `audit-dashboard/src/main.tsx`, `App.tsx` | Dado inesperado da API (já aconteceu com a API simulada) apaga a tela toda, sem mensagem. | `ErrorBoundary` por rota, com mensagem em português e botão "Tentar de novo". |
| 2 | Botão desabilitado parece ativo e o filtro selecionado quase não se vê (≈1,2:1, abaixo dos 3:1 de WCAG 1.4.11). | Design, Acessibilidade, UX | `pages/Claude/ClaudeDecisions.tsx`, `components/ClaudeReviewPanel.tsx` | Clica-se sem efeito visível; quem tem baixa visão não vê o filtro ativo. | Classes CSS com `:disabled`, hover e estado selecionado com borda ou peso, além do fundo. |

## Preocupações transversais (2 lentes ou mais)

| Preocupação | Apontada por | Causa raiz | Correção única |
|---|---|---|---|
| Estilo em linha em toda parte: 1.044 `style={{` contra 64 `className`, 97 cores hexadecimais fixas | Design, Frontend, Mantenedor, Acessibilidade, Produção | Sem camada de classes: faltam `:hover`, `:disabled` e `:focus`; o CSP precisa de `style-src 'unsafe-inline'`; estilos de botão e select copiados em vários arquivos. | Criar classes (`.sentinel-btn`, `.sentinel-select`, `.sentinel-pill`) em `styles/` e migrar por tela, começando pela área Claude. |
| Contrato API↔painel digitado à mão e sem validação em tempo de execução | Frontend, Estrutura, Produção | Tipos TS escritos manualmente; respostas tratadas com `as` e `Array.isArray`. O relatório de 26/11 já registrou telas quebradas por formato de resposta. | Gerar os tipos do OpenAPI que a API já publica (`SentinelOpenApiFilter`) e validar a resposta nas bordas. |
| Verificação que só roda na `main` | Produção, Mantenedor | O teste dos scripts (Semgrep) e o Joern só rodam na `main`; o PR nº 385 passou e a `main` quebrou. | Rodar `test_conferir_esquema` e os testes dos scripts também em PR. |
| Classes grandes e com muitas dependências | Estrutura, Backend, Mantenedor | `IngestionService` 516 linhas, `ReportService` 507, `AnalyzerSet` com 16 `@Inject`; no painel, `SystemsPage` 644. | Dividir por responsabilidade, começando por `AnalyzerSet`. |
| Mensagens de erro genéricas | UX, DX, Produção | "Não foi possível" com a mensagem crua da exceção. | Padrão "o que houve, por quê, o que fazer" e registro com contexto. |

## Todas as recomendações

| # | Recomendação | Lente | Arquivo(s) | Correção |
|---|---|---|---|---|
| 1 | Confirmar antes de aprovar "iniciar auditoria", que não tem desfazer | UX | `ClaudeDecisions.tsx` | Diálogo curto: "Iniciar auditoria do sistema X agora?" |
| 2 | Cores fora dos tokens (`#fff`, `#da1e28`, 7 hexadecimais no `CodeBlock`) | Design, Mantenedor | `ClaudeDecisions.tsx:11`, `ClaudeReviewPanel.tsx:9`, `CodeBlock.tsx` | Tokens `--s-on-primary` e `--s-code-*`; borda do perigo com `--s-danger-text` |
| 3 | Abas sem navegação por setas e sem `aria-controls` | Acessibilidade, DX | `ClaudePage.tsx` (`TabBar`) | Padrão WAI-ARIA de abas: setas, Home/End, `tabIndex` móvel |
| 4 | CLI sem `--help` (só Javadoc de uso) | DX | `audit-cli/.../AuditCli.java` | Texto de ajuda com flags e exemplos; códigos de saída documentados |
| 5 | Documentação diz Java 21; imagem roda JDK 25 e compila com JDK 26 | Mantenedor | `CONTRIBUTING.md`, `pom.xml`, `audit-api/Dockerfile` | Uma tabela de versões em um só lugar |
| 6 | Commits fora do padrão (36%) | Mantenedor | histórico do Git | `commitlint` e hook `commit-msg` |
| 7 | Resource acessando Repository direto (2 casos) | Estrutura | `resource/*` | Passar pelo serviço |
| 8 | `new Thread` manual em reinício e leitura de saída | Backend | `SettingsResource.java:81`, `ChangedFiles.java:149` | Executor gerenciado (thread virtual) |
| 9 | "Carregando…" só em texto | Design, UX | `Claude*.tsx` | `SkeletonText`, como o `IsoQualityPanel` |
| 10 | Títulos `h2` de 18 e 20 px | Design | `ClaudeDecisions.tsx:149` | 18 px |
| 11 | Selo sem ícone (cor + texto apenas) | Acessibilidade, Design | `ClaudeParts.tsx` | Prefixo ✓ / ! / – |
| 12 | Componentes acima de 400 linhas | Frontend, Estrutura | `SystemsPage`, `SystemFormFields`, `EnginePanel`, `AuditRunPage` | Extrair partes, como foi feito em `ClaudePage` |
| 13 | Polling de 4 consultas por sistema na área Claude | Desempenho | `hooks/useClaudeArea.ts` | Um endpoint agregado (`/claude/overview`) |
| 14 | `Dockerfile` com `INSECURE_SSL` no build | Produção | `audit-api/Dockerfile:10,38` | Manter desligado por padrão e fixar no CI que a imagem final não o contém |
| 15 | Erros genéricos no painel | UX, DX | `ClaudeDecisions.tsx` | "Não foi possível aprovar: <motivo>. <o que fazer>." |

---

# Relatórios por lente

---

## 1. Design

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Papel visual | Risco |
|---|---|---|
| `pages/Claude/*` | Decisões, visão geral, atividade | Alto |
| `components/ClaudeReviewPanel.tsx` | funil, sugestões, regras | Médio |
| `components/CodeBlock.tsx` | trecho de código | Médio |
| `styles/paletas.css`, `leitura.css` | tokens `--s-*`, foco global | Baixo |

### Problemas críticos
| # | Problema | Arquivo(s) | Impacto | Correção |
|---|---|---|---|---|
| 1 | Desabilitado não muda de aparência (`disabled` sem estilo; cursor de mão) | `ClaudeDecisions.tsx` | Clique sem resposta visível | Classe com `:disabled` |
| 2 | Filtro selecionado ≈1,2:1 | `ClaudeDecisions.tsx` (`Filters`) | Estado invisível para baixa visão | Borda de 2 px ou peso 600 |

### Recomendações
| # | Problema | Por quê | Correção |
|---|---|---|---|
| 1 | `#fff` e `#da1e28` fora dos tokens | Fragmenta a paleta | Tokens |
| 2 | Estilo de botão copiado em 2 arquivos, de select em 4 | Mudança em um lugar esquece os outros | Exportar de `ClaudeParts.tsx` |
| 3 | Sem hover em botões e abas | Estilo em linha não tem `:hover` | Classes em `leitura.css` com `prefers-reduced-motion` |
| 4 | Carregando só em texto | `IsoQualityPanel` usa `SkeletonText` | Padronizar |
| 5 | `h2` de 18 e 20 px | Hierarquia incoerente | 18 px |

### Conformidade com o sistema de design
Tokens `--s-*` cobrem texto, borda, fundo e tons nas quatro paletas (claro, alto contraste, escuro, sépia). Foco de teclado vem de uma regra global (`leitura.css:75`). O ponto fraco é o método: 1.044 estilos em linha.

### Cobertura de estados visuais
| Elemento | Hover | Foco | Ativo | Desabilitado | Carregando | Vazio | Erro | Escuro |
|---|---|---|---|---|---|---|---|---|
| Botões de decisão | não | sim | não | não | n/a | n/a | sim | sim |
| Filtros | não | sim | fraco | n/a | n/a | sim | n/a | sim |
| Abas | não | sim | sim | n/a | n/a | n/a | n/a | sim |
| Lista de decisões | n/a | n/a | n/a | n/a | texto | sim | sim | sim |
| Selo e Tile | n/a | n/a | n/a | n/a | n/a | n/a | n/a | sim |

### Detalhes
- Fidelidade aos tokens: atenção
- Cobertura de estados: atenção
- Modo escuro: aprovado
- Animação: aprovado (não há animações)
- Consistência: atenção

---

## 2. UX

**Veredito**: Aprovado com mudanças

### Mapa de jornada (conectar o Claude e decidir)
| Passo | Gatilho → Ação → Retorno | Estado |
|---|---|---|
| 1 | Pessoa abre Claude (MCP) → vê Visão geral com selo por sistema | OK |
| 2 | Conectar: assistente de 4 passos, comando pronto por sistema operacional | OK |
| 3 | Claude chama o MCP → aparece em Atividade, com o motivo se recusar | OK |
| 4 | Claude pede uma ação → aba Decisões mostra Aprovar/Recusar | OK |
| 5 | Decidir → o item vai para Decididas, com resultado e hora; aviso em região viva | OK |
| 6 | Desfazer → quando o Sentinel permite; senão, mostra o motivo | OK |
| 7 | Aprovar "iniciar auditoria" → sem confirmação e sem desfazer | Atenção |
| 8 | Falha ao decidir → "Não foi possível" + mensagem crua | Atenção |
| 9 | Erro de renderização → tela branca | Falha |

### Problemas críticos
| # | Problema | Passo | Impacto | Correção |
|---|---|---|---|---|
| 1 | Sem `ErrorBoundary`: tela branca sem saída | 9 | Pessoa perde o contexto e não sabe o que fazer | Mensagem + "Tentar de novo" por rota |

### Recomendações
| # | Problema | Impacto | Correção |
|---|---|---|---|
| 1 | Ação irreversível sem confirmação | Auditoria longa iniciada sem querer | Confirmação específica |
| 2 | Erro genérico | Pessoa não sabe o que fazer | Padrão o quê/por quê/o que fazer |
| 3 | "Carregando…" sem forma | Ansiedade em listas longas | Esqueleto |
| 4 | Decisões anteriores à V8 sem hora e sem desfazer | Parece falha | O texto já explica; manter e medir |

### Boas ideias
| # | Sugestão | Motivo |
|---|---|---|
| 1 | Desfazer por 10 s logo após decidir | Evita ir à lista de Decididas |

### Consistência de interação
Aba e sistema ficam na URL (voltar e compartilhar funcionam). Padrões de lista, selo e botão repetem entre as telas. O desvio: decisões usam botões em linha, e o restante do painel usa Carbon.

### Detalhes
- Descobribilidade: aprovado
- Fluxo completo: aprovado
- Recuperação de erro: atenção
- Feedback e estado: aprovado
- Consistência: atenção

---

## 3. Frontend

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Papel | Risco |
|---|---|---|
| `main.tsx`, `App.tsx` | raiz, rotas, `QueryClient` (`staleTime` 30 s) | Alto |
| `hooks/useClaudeArea.ts` | `useQueries` por sistema | Médio |
| `pages/Systems/SystemsPage.tsx` (644), `SystemFormFields.tsx` (540) | telas grandes | Médio |
| `vite.config.ts` | `manualChunks`, 10 `lazy()` | Baixo |

### Problemas críticos
| # | Problema | Arquivo(s) | Por que quebra | Correção |
|---|---|---|---|---|
| 1 | Nenhum `ErrorBoundary` nem `errorElement` | `main.tsx`, `App.tsx` | Qualquer exceção na renderização desmonta a árvore inteira | Envolver cada rota |

### Recomendações
| # | Problema | Arquivo(s) | Por quê | Correção |
|---|---|---|---|---|
| 1 | Tipos escritos à mão e respostas tratadas com `as` | `api/client.ts`, `types/index.ts` | Formato novo na API vira erro em tempo de execução | Gerar do OpenAPI e validar na borda |
| 2 | 1.044 objetos `style` recriados a cada renderização | todo o painel | Sem `:hover`/`:disabled`; custo pequeno, mas constante | Classes |
| 3 | 23 `useEffect` | vários | Parte pode ser derivada | Revisar um a um |
| 4 | Componentes acima de 400 linhas | listados | Difícil testar | Extrair, como em `ClaudePage` |
| 5 | `(decide.error as Error)` | `ClaudeDecisions.tsx` | Erro pode não ser `Error` | Estreitar o tipo |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | `useQueries` agregado em um hook único de "visão" | Menos repetição entre `useClaudeSystems` e `useDecisions` |

### Análise do caminho de renderização
A área Claude tem dois hooks que criam uma consulta por sistema (`mcp`, `calls`, `pending-actions`, `claude-review`). Cada resposta que muda re-renderiza a `ClaudePage` inteira. Com poucos sistemas é invisível. As listas (`DecisionRow`) têm chave estável. Nada está em `React.memo`, o que está certo para esse tamanho.

### Avaliação de tipos
`strict` ligado; 0 `any`, `@ts-ignore` e `@ts-expect-error`. As lacunas estão na borda: tipos manuais e `as` depois de `Array.isArray`.

### Detalhes
- Arquitetura de componentes: atenção (telas grandes)
- Gerência de estado: aprovado (react-query, sem estado duplicado)
- Segurança de tipos: atenção (borda da API)
- Desempenho de renderização: aprovado
- Impacto no pacote: aprovado (`manualChunks`, 10 `lazy()`)

---

## 4. Backend (Java/Quarkus, adaptado)

**Veredito**: Aprovado com mudanças

A lente original é de Rust/Tauri. Apliquei os mesmos princípios (erros, recursos, concorrência, acesso) ao Java.

### Arquivos revisados
| Arquivo | Papel | Risco |
|---|---|---|
| `settings/SettingsResource.java` | configurações e reinício | Alto |
| `settings/DbConfigSource.java` | lê configuração do banco na subida | Médio |
| `service/ChangedFiles.java` | leitura de saída de processo | Médio |
| `service/IngestionService.java` (516), `ReportService.java` (507) | núcleo de ingestão e relatório | Médio |
| `mcp/*`, `actions/*`, `review/*` | MCP, pedidos, revisão | Médio |

### Problemas críticos
Nenhum. (A versão anterior apontava `@PermitAll` em `/api/v1/settings` como crítico: engano. O papel `sentinel-admin` é exigido por `RolePolicy` e aplicado por `RoleAuthorizationFilter`; o comentário em `SettingsResource` é que dava a entender o contrário e foi reescrito.)

### Recomendações
| # | Problema | Arquivo(s) | Por quê | Correção |
|---|---|---|---|---|
| 1 | `new Thread` manual | `SettingsResource.java:81`, `ChangedFiles.java:149` | Sem limite de concorrência | Executor com thread virtual |
| 2 | Consulta dentro de laço (N+1) | `FindingLifecycleService.java:260` | Cresce com o número de achados | Buscar de uma vez (hoje está como risco aceito) |
| 3 | `Exceção` descartada e relançada só com texto | `AnalyzerRunner.java:186` | Perde a causa | Passar a causa |
| 4 | Métodos que devolvem `null` para coleção | `pack/ProfileLoader.java:92`, `Versions.java:21`, `Yamls.java:32,59` | Obriga todo chamador a checar | Devolver coleção vazia |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | `MeResource` sem anotação de acesso (risco aceito hoje) | Documentar no próprio código que o filtro protege |

### Análise do caminho de erro
0 `catch` vazio em 292 arquivos de código. As 10 ocorrências de `System.exit` e `printStackTrace` estão no CLI (códigos de saída) e em `PackTool`, onde encerrar o processo é o contrato. O reinício da API usa `Quarkus.asyncExit()` com atraso, certo. Um ponto de cuidado já conhecido: exceção em `@Transactional` aninhado marca a transação para reversão (já tratado em `PendingActionService.undo` com checagem antes).

### Auditoria do ciclo de vida de recursos
| Recurso | Alocado em | Liberado em | Limpeza no caminho de erro | Risco |
|---|---|---|---|---|
| Thread de reinício | `SettingsResource.restart` | termina sozinha (daemon) | sim | baixo |
| Thread leitora de saída | `ChangedFiles` | não verificado | não verificado | médio |
| Conexão JDBC de boot | `DbConfigSource` | `try-with-resources` | sim | baixo |
| Processos de scanners externos | analisadores | não verificado | não verificado | médio |

### Detalhes
- Tratamento de erros: aprovado
- Segurança de concorrência: atenção
- Gestão de recursos: atenção (dois itens não verificados)
- Correção do contrato (REST): aprovado
- Segurança de acesso: aprovado (papéis centralizados em `RolePolicy`, com teste)

---

## 5. Estrutura

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Papel estrutural | Camada | Risco |
|---|---|---|---|
| `service/AnalyzerSet.java` | agrega analisadores (16 `@Inject`) | serviço | Alto |
| `service/IngestionService.java`, `ReportService.java` | núcleo | serviço | Médio |
| `actions/*`, `review/*`, `resource/*` | domínio MCP | domínio/API | Médio |
| `audit-dashboard/src/api`, `hooks`, `types` | contrato com a API | borda | Médio |

### Problemas críticos
Nenhum.

### Recomendações
| # | Problema | Por quê | Correção |
|---|---|---|---|
| 1 | `AnalyzerSet` com 16 dependências injetadas | Qualquer analisador novo mexe nele | Registro por descoberta (`Instance<Analyzer>`) |
| 2 | Ciclo entre 5 pacotes (`actions` e vizinhos) | Mudança em um quebra os outros | Extrair as interfaces para um pacote comum |
| 3 | 2 `Resource` acessam `Repository` direto | Salta a camada de serviço | Passar pelo serviço |
| 4 | `FindingResponse` com 20 e 21 parâmetros | Difícil de manter | Agrupar em objetos de valor |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | Gerar tipos TS do OpenAPI | Remove a duplicação de contrato |

### Mapa de dependências
```
audit-dashboard (React)  --HTTP/JSON-->  audit-api (Quarkus)
   api/client.ts, types/ (à mão)            resource/ -> service/ -> model + repository
audit-cli (cliente HTTP, só Jackson)  -->  /api/v1/* (mesma API do painel)
audit-rules (pacotes de regras)  ------->  pack/ (PackLoader) -> analyzer/
MCP  (/mcp/{sistema}) -> mcp/ -> actions/, review/ -> model
```
Setas preocupantes: dashboard → API sem contrato gerado; `actions` ↔ `review` ↔ `service` em ciclo.

### Análise de cruzamento de fronteiras
| Cruzamento | De → Para | Limpo? | Notas |
|---|---|---|---|
| Painel → API | `api/client.ts` → REST | em parte | Tipos manuais |
| Recurso → Repositório | 2 casos | não | Saltam o serviço |
| MCP → domínio | `mcp/` → `actions/`, `review/` | sim | Regras puras testadas (`PendingActionRules`, `RuleFunnel`) |
| CLI → API | `audit-cli` → `/api/v1/*` por `HttpClient` | sim | Cliente fino: depende só de Jackson, não do motor |

### Detalhes
- Direção das dependências: atenção
- Acoplamento: atenção (`AnalyzerSet`, ciclo)
- Coesão: atenção (serviços grandes)
- Fronteiras entre camadas: atenção
- Propagação de mudança: aprovado para regras (pacotes), atenção para analisadores

---

## 6. Mantenedor do repositório

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Status de convenção | Risco |
|---|---|---|
| `CONTRIBUTING.md`, `README.md`, `docs/` (120 arquivos) | boa cobertura | Baixo |
| `pom.xml`, `audit-api/Dockerfile`, `.github/workflows/*` | versões divergentes | Médio |
| `audit-dashboard/eslint.config.mjs` | zero avisos no CI | Baixo |
| `reviews/` | relatórios versionados | Baixo |

### Problemas críticos
Nenhum.

### Recomendações
| # | Problema | Por quê | Correção |
|---|---|---|---|
| 1 | `CONTRIBUTING.md` diz "Quarkus 3.40, Java 21"; a imagem usa JDK 25 e compila com JDK 26 | Quem lê não sabe qual JDK usar | Tabela de versões única |
| 2 | 36% dos commits fora do padrão | Histórico difícil de ler | `commitlint` e hook |
| 3 | Estilo duplicado em 2 a 4 arquivos | Deriva visual | Exportar de `ClaudeParts.tsx` |
| 4 | Testes só na `main` não rodam em PR | Já quebrou a `main` uma vez | Rodar em PR |
| 5 | `reviews/audit-plan-full.md` é sobrescrito a cada auditoria | Perde-se a comparação | Salvar com data (`reviews/AAAA-MM-DD-...md`) |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | Índice de `docs/` com a data da última revisão | Achar o que está velho |

### Lista de conformidade
| Convenção | Estado | Notas |
|---|---|---|
| Colocação de arquivos | ok | `pages/`, `components/`, `hooks/`, `utils/` |
| Nomes | ok | `useX`, `ClaudeX`, nomes em português no domínio |
| Imports de tipo | ok | `import type` em uso |
| Código morto | ok | 8 exports removidos no PR #387; não há ferramenta de código morto (como `knip`) no CI |
| Logs estruturados | ok | JBoss Logger (`infof`, `warnf`) |
| Supressões de lint | atenção | 1 `eslint-disable` removido no PR #387 |
| Documento de instruções | atenção | `CONTRIBUTING.md` e `docs/guia`; não há `CLAUDE.md` |

### Oportunidades de reuso
`btn`, `btnPrimary`, `btnDanger` e o estilo de `select` em um módulo único.

### Detalhes
- Organização de arquivos: aprovado
- Consistência de nomes: aprovado
- Higiene de imports: aprovado
- Reuso de código: atenção
- Conformidade com lint: aprovado

---

## 7. Prontidão para produção

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Papel em produção | Risco |
|---|---|---|
| `docker-compose.yml`, `deploy/helm`, `deploy/observabilidade` | implantação e alertas | Médio |
| `audit-api/Dockerfile`, `audit-dashboard/Dockerfile`, `nginx.conf.template` | imagens e proxy | Médio |
| `db/migration/V1..V8` | esquema (Flyway) | Médio |
| `StartupChecks.java` | recusa subir sem `API_KEY` em produção | Baixo |

### Problemas críticos
Nenhum.

### Recomendações
| # | Problema | Por quê | Correção |
|---|---|---|---|
| 1 | Sem `ErrorBoundary` | Tela branca para o usuário final | Por rota |
| 2 | CSP com `style-src 'unsafe-inline'` | Consequência do estilo em linha | Migrar para classes e remover |
| 3 | Teste de esquema só na `main` | Quebra a `main` depois do merge | Rodar em PR |
| 4 | `INSECURE_SSL` no Dockerfile | Build sem verificar certificados, se ligado por engano | Falhar o CI se a imagem final contiver `.curlrc`/`.npmrc` inseguros |
| 5 | Chave de cifra dos tokens cai para `API_KEY` quando `SECRETS_KEY` está vazia | Girar a `API_KEY` invalida os tokens cifrados | Exigir `SECRETS_KEY` própria em produção |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | Alerta para "decisões esperando há mais de 24 h" | O Claude fica parado sem ninguém ver |

### Análise de modos de falha
| Cenário | O que falha | Impacto no usuário | Recuperação | O código trata? |
|---|---|---|---|---|
| Banco indisponível | API sem resposta | Painel com erro | `restart: unless-stopped`, healthcheck | sim |
| Subir sem `API_KEY` | API aberta | Exposição | `StartupChecks` recusa | sim |
| Resposta de API com formato novo | exceção na renderização | Tela branca | nenhuma | **não** |
| Migração V8 aplicada e volta do código | colunas novas ficam sem uso | nenhum (colunas anuláveis) | automática | sim |
| Usuário sem papel chama `/restart` | recusado com 403 | nenhum | n/a | sim (`RolePolicy` exige `sentinel-admin`) |
| Scanner externo travado | auditoria demora | atraso | tempo limite por job (não verificado) | parcial |

### Lista de ambiente
| Item | Dev | Prod | Diferença? |
|---|---|---|---|
| Caminhos | `localhost`, portas do compose | variáveis (`DB_URL`) | documentado |
| Rede | proxy `/api` no Vite | nginx com `proxy_read_timeout 120s` | ok |
| Permissões | usuário da imagem `audit` | sem root, `USER 101` no painel | ok |
| Configuração | `.env` | segredos em variáveis, `ALLOW_OPEN_API=false` | ok |
| Saída de build | `vite dev` | `dist/` com `Cache-Control: immutable` | ok |

### Detalhes
- Fluxo ponta a ponta: aprovado
- Recuperação de erro: atenção
- Integridade de dados: aprovado (Flyway, colunas anuláveis, sem reescrita)
- Paridade de ambiente: aprovado
- Observabilidade: aprovado (alertas, painéis em `deploy/observabilidade`)

---

## 8. DX

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Papel de DX | Risco |
|---|---|---|
| `audit-cli/.../AuditCli.java` | CLI (485 linhas) | Médio |
| `docs/integracao/mcp.md` | guia do MCP | Baixo |
| `utils/mcpSetup.ts` | comandos prontos e ajuda de recusa | Baixo |
| `CONTRIBUTING.md` | como contribuir | Baixo |

### Problemas críticos
Nenhum.

### Recomendações
| # | Problema | Por quê | Correção |
|---|---|---|---|
| 1 | CLI sem `--help` (só Javadoc) | Quem digita `--help` não acha nada | Ajuda com flags, exemplos e códigos de saída |
| 2 | Erro do painel genérico | Quem depura quer causa e ação | Padrão em três partes |
| 3 | Abas sem teclado completo | Quem usa só o teclado perde tempo | Setas e Home/End |
| 4 | Configurações exigem reinício pela tela | Em CI ou SSH não há tela | Documentar o equivalente por variável de ambiente |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | Ferramenta MCP `ultimo_relatorio` | Hoje o Claude junta 3 chamadas para ler o relatório |
| 2 | `--dry-run` em ações do CLI que escrevem | Padrão em ferramentas de desenvolvedor |

### Auditoria de mensagens de erro
| Cenário | Mensagem atual | O que a pessoa precisa | Mensagem sugerida |
|---|---|---|---|
| Aprovar falha | "Não foi possível" + texto cru | motivo e próximo passo | "Não foi possível aprovar o pedido 7: <motivo>. Tente de novo ou recuse." |
| MCP recusado | `refusalHelp` com motivo e o que fazer | já cobre | manter |
| API subiu sem chave | `StartupChecks` explica e pede `API_KEY` | já cobre | manter |
| Resposta inesperada da API | tela branca | mensagem e ação | `ErrorBoundary` |

### Jornada do desenvolvedor
Primeiro uso: `docker compose up`, `.env`, painel; o assistente do MCP entrega o comando pronto. Uso diário: CLI e painel. Uso avançado: pacotes de regras e supressões. O atrito está em descobrir opções do CLI sem `--help`.

### Detalhes
- Qualidade dos erros: atenção
- Padrões e configuração: aprovado
- Teclado: atenção
- Descobribilidade: atenção (CLI)
- Integração com o fluxo: aprovado (SARIF, JUnit, `--fail-below`)

---

## 9. Desempenho

**Veredito**: Aprovado

### Arquivos revisados
| Arquivo | Papel de desempenho | Risco |
|---|---|---|
| `hooks/useClaudeArea.ts` | polling por sistema | Médio |
| `hooks/usePendingClaude.ts` | selo do menu (60 s) | Baixo |
| `hooks/useMcpActivity.ts`, `useAuditRuns.ts` | polling condicional | Baixo |
| `vite.config.ts` | divisão de pacotes | Baixo |
| `mcp/McpCallLogRepository.java` | consulta limitada (`page(0, limit)`) | Baixo |

### Problemas críticos
Nenhum.

### Recomendações
| # | Problema | Arquivo(s) | Por quê | Correção |
|---|---|---|---|---|
| 1 | 4 consultas por sistema com a área aberta | `useClaudeArea.ts` | 10 sistemas ≈ 100 requisições por minuto | Endpoint agregado |
| 2 | Polling de 60 s do selo do menu corre em todas as telas, uma consulta por sistema (a chave `pending-actions` é a mesma da área Claude, então não dobra quando as duas estão abertas) | `usePendingClaude.ts` | Custo fixo por sistema | Uma consulta agregada |
| 3 | Possível N+1 no ciclo de vida de achados | `FindingLifecycleService.java:260` | Cresce com os achados | Buscar de uma vez |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | Medir tamanho dos pacotes no CI | Evitar regressão do `manualChunks` |

### Orçamento de recursos
| Recurso | Hoje | Depois | Preocupação? |
|---|---|---|---|
| Requisições por minuto (área Claude, N sistemas) | ≈10·N | ≈2 (agregado) | só com N grande |
| Listeners | 2 `setInterval` (1 s no reinício, `useNow`) | igual | não |
| Crescimento de memória por hora | limitado (listas com `limit`, react-query com coleta de lixo) | igual | não |
| Impacto na subida | 10 telas com `lazy()` | igual | não |
| Tamanho do pacote | `manualChunks` | igual | não |

### Análise do caminho quente
O caminho quente do painel é a renderização de listas de achados (paginadas) e o polling enquanto uma auditoria roda (`POLL_MS` só se há execução ativa). O polling de abas em segundo plano é pausado pelo react-query. No servidor, a ingestão e a geração do PDF são os pontos pesados; ambas rodam por requisição (não medi o tempo de PDF: não verificado).

### Detalhes
- Gestão de memória: aprovado
- Eficiência de chamadas: atenção (agregar)
- Padrões de E/S: aprovado
- Impacto na subida: aprovado
- Estabilidade em sessão longa: aprovado

---

## 10. Acessibilidade

**Veredito**: Aprovado com mudanças

### Arquivos revisados
| Arquivo | Papel de acessibilidade | Risco |
|---|---|---|
| `pages/Claude/ClaudePage.tsx` (`TabBar`) | abas | Alto |
| `pages/Claude/ClaudeDecisions.tsx` | botões, filtros, aviso em região viva | Alto |
| `components/CodeBlock.tsx` | região rolável com `tabIndex` | Médio |
| `styles/leitura.css` | foco global, alvos de 24 px, texto só para leitor de tela | Baixo |

### Problemas críticos
| # | Problema | Critério WCAG | Arquivo(s) | Impacto | Correção |
|---|---|---|---|---|---|
| 1 | Filtro selecionado distinguível só por cor de fundo (≈1,2:1) | 1.4.11 e 1.4.1 | `ClaudeDecisions.tsx` | Estado invisível para baixa visão ou daltonismo | Borda, peso ou ✓ |

### Recomendações
| # | Problema | Critério WCAG | Correção |
|---|---|---|---|
| 1 | Abas sem setas, Home/End e `aria-controls` | 2.1.1, 4.1.2 | Padrão WAI-ARIA de abas |
| 2 | Botão desabilitado sem aparência de desabilitado | 1.4.11 | `:disabled` visível e `aria-busy` |
| 3 | Selo (`Pill`) com cor e texto, sem ícone | 1.4.1 | Prefixo de símbolo |
| 4 | `ErrorBoundary` ausente: tela branca sem foco nem anúncio | 3.3.1 | Mensagem com foco e `role="alert"` |

### Boas ideias
| # | Sugestão | Rationale |
|---|---|---|
| 1 | Teste automatizado com `axe` nas telas principais | Pega regressões |

### Passo a passo por teclado (Decisões)
| Passo | Ação | Foco esperado | Anúncio esperado | Tratado? |
|---|---|---|---|---|
| 1 | Tab até as abas | aba selecionada | "Decisões, aba, 3 de 5" | em parte (sem posição) |
| 2 | Setas entre abas | próxima aba | nome da aba | não |
| 3 | Tab nos filtros | botão do filtro | "Esperando (2), pressionado" | sim (`aria-pressed`) |
| 4 | Enter em Aprovar | permanece no botão | aviso "Aprovar: título. O item continua em Decididas…" | sim (`role="status"`) |
| 5 | Tab no trecho de código | região rolável | "Código de arquivo:linha, região" | sim |
| 6 | Escape | n/a | n/a | n/a |

### Lista de ARIA
| Elemento | Papel | Rótulo | Estados | Teclado | Notas |
|---|---|---|---|---|---|
| Barra de abas | `tablist` | "Seções do Claude (MCP)" | `aria-selected` | só Tab | faltam setas e `aria-controls` |
| Painel da aba | `tabpanel` | `aria-labelledby` | n/a | n/a | ok |
| Filtros | grupo | "Mostrar decisões" | `aria-pressed` | Tab | contraste fraco |
| Item de decisão | `li` | "Tipo: título" | n/a | n/a | ok |
| Aviso de decisão | `status`, `aria-live="polite"` | n/a | n/a | n/a | ok |
| Bloco de código | `region` | "Código de arquivo:linha" | n/a | foco com `tabIndex` | intencional (rolagem) |

### Detalhes
- Compatibilidade com leitor de tela: aprovado (215 `aria-*`, 70 `role`, 0 `onClick` em `div`/`span`)
- Navegação por teclado: atenção (abas)
- Gestão de foco: aprovado
- Contraste: atenção (filtro e desabilitado)
- Sensibilidade a movimento: aprovado (`prefers-reduced-motion` respeitado; não há animações)
