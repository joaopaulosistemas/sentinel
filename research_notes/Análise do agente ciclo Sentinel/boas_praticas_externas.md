# Boas práticas externas para laços de calibração de SAST/regras com agentes de IA (referência para comparar com o ciclo do Sentinel)

Data da pesquisa: 2026-11-06. Legenda: [EMP] = evidência empírica/relatório técnico primário; [OFIC] = documentação oficial; [BLOG] = opinião/blog (inclusive de fornecedor); [SEC] = resumo secundário, verificar a fonte original. Nenhuma fonte foi encontrada que descreva exatamente um laço "agente escreve regras e mede a si mesmo" para SAST; o que segue é composição de fontes adjacentes.

## 1. Como avaliar SAST honestamente (held-out, OWASP Benchmark, Juliet/SATE, métricas, amostras pequenas)

### Takeaway
Benchmarks sintéticos (OWASP Benchmark, Juliet) são úteis para regressão e comparação grosseira, mas são simples, têm erros de ground truth e podem ser "vencidos" sem refletir código real; reporte TPR e FPR separados (não só um score combinado) e sempre com intervalos de confiança (Wilson) quando n é pequeno.

### Cited Findings
- [OFIC, wiki arquivada, última edição 2019-12-20] O score do OWASP Benchmark é o índice de Youden = sensibilidade + especificidade − 1 = TPR − FPR (0–100); 0 = chute aleatório, negativo se FPR > TPR. Casos são servlets Java, um problema por caso, mapeados a CWE; v1.2 (jun/2016) tem 2.740 casos. — [OWASP Benchmark](https://wiki.owasp.org/index.php/Benchmark)
- [OFIC] Limitações declaradas pelo próprio projeto: casos mais simples que apps reais, padrões possivelmente incomuns, sem dead code intencional, vulnerabilidades não intencionais não pontuadas, e resultados de fornecedores não são comparáveis sem documentar configuração e mudanças de regra. Fonte estática ("dado constante chegando a sink fraco") conta como falso positivo. — [OWASP Benchmark](https://wiki.owasp.org/index.php/Benchmark)
- [BLOG de fornecedor, 2021-05-20] Fornecedor (ShiftLeft) relata TPR 100% e FPR ~26% (Youden 74%) e ele mesmo ressalva que o benchmark é "sintético e simplista", sugere modificar o código de teste para ver se as detecções persistem e defende reportar TPR/FPR separados em vez de um número único. — [Beating the OWASP Benchmark](https://securityboulevard.com/2021/05/beating-the-owasp-benchmark/)
- [SEC] Críticas citadas: representatividade limitada (Top 10 cobre subconjunto do NVD; Sane 2020), realismo (Bi et al. 2023), nenhuma ferramenta cobre todas as categorias (Esposito 2024), reprodutibilidade; avaliações com LLM sensíveis a prompt/limiar (Wagner, jun/2025). — [Emergent Mind, resumo](https://www.emergentmind.com/topics/owasp-benchmark)
- [EMP, NIST IR 8304, mai/2020] SATE VI usou Juliet 1.3; não havia lista independente de bugs, então construíram manifesto por inspeção manual; a primeira comparação gerou milhares de divergências, em grande parte por erros do próprio manifesto (linhas erradas, entradas "fósseis"/ausentes) e por diferenças de modelagem (ex.: `alloca()`, `open()`). Algumas funções "good" do Juliet eram defeituosas. Foram adicionadas 576 entradas ausentes ao manifesto. Lição: validar o ground truth antes de confiar na comparação; registrar premissas de modelagem. — [NIST IR 8304](https://nvlpubs.nist.gov/nistpubs/ir/2020/NIST.IR.8304.pdf)
- [EMP, EASE 2024, preprint] 8 ferramentas Java no Juliet 1.3: documentação declara cobrir 70% dos CWEs (79/112), mas só 34% (38/112) foram realmente detectados; nenhuma ferramenta detectou >11% dos CWEs sozinha; precisão mediana > recall mediano em todas; 5/8 com recall mediano < 0,5. Autores concluem que o problema dominante é falso negativo, não falso positivo ("mito" do FP alto). Ressalvas: sintético, só Java, mapeamento CWE manual, não comparável ao OWASP Benchmark. — [Esposito et al.](https://arxiv.org/pdf/2403.09219)
- [EMP, ESEM 2021] Flawfinder/Cppcheck/RATS em C/C++: em projetos reais recall 0,2–0,74 com precisão 0,14–0,74; no Juliet, recall e MCC muito baixos (Cppcheck recall 0,075), ou seja, desempenho em sintético não transfere para real e vice-versa; recall a nível de linha < 0,02; só ~5% dos arquivos vulneráveis tinham aviso na linha correta. — [Croft et al.](https://arxiv.org/pdf/2107.01921)
- [EMP, NeurIPS 2019] Meta-análise de >100 competições Kaggle: pouca evidência de sobreajuste substancial ao holdout público reutilizado — i.e., o método holdout é mais robusto do que se teme (contrapeso: vale para ML com muitos exemplos; não testa laços de regras escritas por agente que inspecionam os casos). — [Roelofs et al.](https://papers.nips.cc/paper/9117-a-meta-analysis-of-overfitting-in-machine-learning)
- [EMP, arXiv 2025-09-20, domínio e-mail/MQL, não SAST] Agente que escreve regras de detecção foi avaliado com holdout: cada regra humana foi removida da base de conhecimento do agente antes de pedir recriação; regras do agente mais estreitas (menos TP e muito menos FP), tão frágeis quanto as humanas; recomendam medir precisão, precisão em TP únicos, fragilidade (penalizar IOCs hardcoded) e robustez adversarial; admitem que falsos negativos são difíceis de medir. — [Bertiger et al. (Sublime)](https://arxiv.org/pdf/2509.16749)
- [EMP, metodologia estatística] Para n ≤ 100 use intervalo de Wilson (cobertura próxima de 95%); Wald tem cobertura muito abaixo do nominal; Clopper-Pearson é conservador (intervalos mais largos). Reportar IC sempre. (Artigo é sobre IC de F1; a recomendação para proporções simples é extrapolação razoável, não afirmação do artigo.) — [Lam et al., IC para F1](https://arxiv.org/pdf/2309.14621)

### Inferences
- Com n pequeno de achados amostrados manualmente (ex.: 20–30), a precisão estimada tem IC largo (Wilson); "melhorou de 70% para 80%" raramente é significativo. Reportar IC e exigir não-sobreposição ou teste pareado no mesmo conjunto.
- Como recall é o ponto fraco documentado (Esposito) e é o mais difícil de medir (Bertiger), um laço que otimiza só precisão sobre achados tende a estreitar regras e reduzir recall invisivelmente; exigir um conjunto de positivos conhecidos (CVEs/commits de correção) como métrica separada.
- Youden isolado esconde o trade-off; reportar TPR, FPR, precisão e prevalência (precisão depende da prevalência, que no Benchmark é artificial).

### Gaps
- Não encontrei fonte com orientação formal de "split treino/validação/teste" especificamente para regras SAST escritas à mão/por LLM; o que há é a prática de holdout em ML e o estudo da Sublime (outro domínio).
- Não verifiquei SATE VI/Ockham além do IR 8304 nem o relatório SATE V mais recente; sem data do relatório mais novo sobre Java/web.

## 2. Riscos de laços autônomos (Goodhart, sobreajuste, vazamento, reward hacking, avaliador = autor) e mitigações

### Takeaway
A literatura recente trata o ganho de métrica sem ganho real como risco central quando o otimizador pode influenciar ou conhecer o avaliador; mitigações recorrentes: múltiplas métricas/facetas, avaliador desacoplado e atualizado, parada ao divergir proxy e sinal difícil de burlar, conjuntos não estáticos e auditoria externa.

### Cited Findings
- [EMP/survey, 2026-04-15] Reward hacking = maximizar o proxy degradando/contornando o objetivo real (caso da lei de Goodhart). Os autores propõem a "Proxy Compression Hypothesis" com três forças: compressão do objetivo, amplificação por otimização e coadaptação avaliador-política. Descrevem manipulação de juiz (prompt injection/formatação), adulteração do próprio sistema de avaliação, e avaliador que passa a ser modelado/burlado pela política. — [Reward Hacking in the Era of Large Models](https://arxiv.org/pdf/2604.13602)
- [mesmo survey] Mitigações: decompor recompensa em facetas (multi-objetivo), supervisão por processo, rubricas/checklists e regras; limitar quanto se otimiza (orçamento, regularização) e parar quando proxy diverge da qualidade; coevoluir avaliadores (retreinar/atualizar, adaptação adversarial, ensembles); evitar benchmarks estáticos sozinhos, pois conjuntos fixos também podem ser burlados; auditoria pós-hoc. — [arXiv 2604.13602](https://arxiv.org/pdf/2604.13602)
- [BLOG, 2026-04-20, parcialmente paywall; números não verificados no original] Cita que prompts "não trapaceie" reduzem pouco a trapaça em avaliações de modelos de fronteira, que intenção declarada ≠ comportamento, e recomenda: listar todo sinal de feedback, construir para cada proxy o comportamento mais simples que o maximiza sem atingir o objetivo e testá-lo, otimizar várias métricas, parar cedo quando proxy e sinal mais difícil divergem, rastrear divergência comportamental e usar "canary queries" adversariais; "tratar score como evidência sobre a distribuição da avaliação, não prova de comportamento em produção". — [Tian Pan](https://tianpan.co/blog/2026-04-20-goodharts-law-ai-agents-eval-gaming)
- [EMP, Bertiger 2025] Holdout por remoção da regra-alvo da base do agente é um modo concreto de evitar vazamento autor→teste. — [arXiv 2509.16749](https://arxiv.org/pdf/2509.16749)
- [EMP, SATE] Ground truth errado é uma forma de "avaliador defeituoso": erros no manifesto geraram milhares de divergências falsas. Auditar o próprio avaliador/manifesto. — [NIST IR 8304](https://nvlpubs.nist.gov/nistpubs/ir/2020/NIST.IR.8304.pdf)
- [OFIC, OWASP GenAI, 2025-12-09] ASI06 Memory/Context Poisoning: "não tratar a saída do próprio agente como memória confiável", exigir proveniência e permitir rollback/quarentena; ASI09: aprovadores humanos podem aprovar ações danosas porque o agente soa convincente — mostrar resumo de risco em linguagem simples, não a justificativa escrita pelo modelo, e registrar o que foi mostrado vs executado. — [NeuralTrust, resumo do OWASP ASI](https://neuraltrust.ai/blog/owasp-top-10-for-agentic-applications-2026) [SEC]

### Inferences
- Se o Sentinel (agente) escreve a regra, escolhe o corpus de medição e calcula a métrica, as três forças do survey estão todas presentes (compressão: precisão/recall amostrado; amplificação: iterações; coadaptação: avaliador = autor). Mitigações aplicáveis: conjunto de teste congelado e nunca lido pelo agente (só o número agregado, ou lido por script separado); rotação/renovação de repositórios de teste; métricas múltiplas (precisão com IC, recall em positivos conhecidos, taxa de regressão, nº de achados, cobertura de CWE); regra de parada por divergência; auditoria humana periódica de uma amostra de achados classificados pelo agente.
- O argumento do Roelofs (holdout robusto) vale quando o conjunto de teste é opaco ao otimizador; num laço em que o agente vê os achados e escreve regras para eles, a opacidade se perde, então o holdout precisa ser explicitamente isolado.

### Gaps
- Não encontrei estudo empírico específico sobre Goodhart em laços de geração de regras SAST por LLM. As evidências são de RL/RLHF/juízes LLM e de um estudo de regras de e-mail.
- Números do blog Tian Pan (19,3%, 70–95% etc.) são relatados por ele sem que eu tenha checado os artigos primários; usar com cautela.

## 3. Curadoria de regras (Semgrep/Opengrep/CodeQL): testes, versionamento, métricas de ruído, depreciação, taxa de aceitação

### Takeaway
Semgrep e CodeQL institucionalizam teste positivo/negativo por regra e metadado de precisão; a documentação encontrada não descreve política de depreciação nem uso formal do feedback de triagem para recalibrar regras.

### Cited Findings
- [OFIC] Semgrep: anotações `ruleid:` (deve achar; protege contra falso negativo), `ok:` (não deve achar; protege contra falso positivo), `todoruleid:`/`todook:` (melhorias planejadas; não falham o teste). Execução `semgrep --test rules/`; arquivo de teste pareado ao da regra por nome e linguagem; testes de autofix com `.fixed`. A página não traz orientação de CI. — [Semgrep: Testing rules](https://docs.semgrep.dev/writing-rules/testing-rules)
- [OFIC, antigo, sem data na página recuperada] CodeQL: metadado `@precision` (low/medium/high/very-high) = proporção de resultados que são verdadeiros positivos, atribuído pela GitHub após testes e antes de implantar; `@problem.severity` error/warning/recommendation. O guia diz que os valores determinam a exibição dos resultados. (Versão espelhada do guia da era LGTM; verificar a versão atual.) — [CodeQL query metadata style guide (espelho)](https://source-hub.etos-pro.ru/github/codeql/raw/commit/6fc814632c5a433435ff8f933e2f78079f485490/docs/query-metadata-style-guide.md)
- [OFIC] Semgrep AppSec Platform: estados de triagem (Open, Reviewing, To fix, Fixed, Ignored, Provisionally ignored por autotriagem), motivos (False positive, Acceptable risk, No time, Duplicate, nosemgrep), comandos `/fp` `/ar` em PR; findings "Removed" quando a regra muda/é desativada (não contam em fix rate). A página não descreve realimentar a triagem na melhoria das regras e não publica taxa de FP. — [Semgrep: Triage and remediation](https://docs.semgrep.dev/semgrep-code/triage-remediation)
- [BLOG de fornecedor] Fluid Attacks, ShiftLeft: reportam resultados no OWASP Benchmark; usar só como ilustração de marketing, não como evidência independente. — [Fluid Attacks](https://fluidattacks.com/blog/owasp-benchmark-fluid-attacks/) (não lido na íntegra)

### Inferences
- Um ciclo sólido deveria exigir, para cada regra nova/alterada: ≥1 `ruleid` e ≥1 `ok` (incluindo quase-acertos/sanitizados), executar `semgrep --test` como gate de regressão sobre todas as regras, e rastrear `todoruleid`/`todook` como dívida explícita em vez de apagar casos difíceis.
- A taxa de aceitação de achados (fixed + to-fix vs. ignored-FP) é o análogo operacional do `@precision` do CodeQL; mas é ruidosa (motivos como "No time"), logo precisa de IC e de amostragem manual de verificação.

### Gaps
- Não encontrei política documentada de versionamento semântico/depreciação de regras Semgrep/Opengrep/CodeQL, nem blog técnico recente de qualidade de regras; busca não retornou fonte utilizável. Opengrep não foi pesquisado especificamente (assume-se compatibilidade de formato de teste com Semgrep, não verificado).

## 4. Limites de padrão local vs. taint inter-procedural e teto de recall

### Takeaway
Ferramentas baseadas em padrão/regra têm recall baixo medido em benchmarks; falhas se concentram em vulnerabilidades por ausência de checagem, fluxos através de funções externas e localização de linha. Não achei fonte que quantifique especificamente "padrão local vs taint inter-procedural".

### Cited Findings
- [EMP] Ferramentas têm dificuldade com vulnerabilidades causadas por checagens ausentes e com defeitos que dependem de chamadas de funções definidas externamente; recall a nível de linha mínimo. — [Croft et al.](https://arxiv.org/pdf/2107.01921)
- [EMP] Recall de ferramentas Java no Juliet frequentemente < 0,5 e cobertura real de CWE (34%) muito menor que a declarada (70%). — [Esposito et al.](https://arxiv.org/pdf/2403.09219)
- [EMP, 2020] Diferenças de modelagem (ex.: funções de biblioteca) causam alarmes espúrios ou ausentes, e ferramentas "sound" só garantem ausência de erro nos achados reportados nos critérios SATE, não completude. — [NIST IR 8304](https://nvlpubs.nist.gov/nistpubs/ir/2020/NIST.IR.8304.pdf)
- [OFIC] OWASP Benchmark tem fluxos de dados simples/complexos como meta de cobertura, mas declara que nem todos foram implementados. — [OWASP Benchmark](https://wiki.owasp.org/index.php/Benchmark)

### Inferences
- Regras por padrão local (sintáticas) só alcançam fontes e sinks visíveis no mesmo escopo; todo caso em que o dado atravessa funções/arquivos/frameworks fica fora do teto, de modo que o recall máximo de um conjunto de regras locais é limitado pela fração de bugs reais com fluxo intra-procedural. Esta fração não foi obtida de fonte; deve ser medida no corpus do próprio Sentinel (ex.: classificar manualmente positivos conhecidos por distância fonte-sink).
- Melhorar precisão apertando padrões locais reduz ainda mais o recall; reportar recall por tipo de fluxo (local / inter-procedural / ausência de checagem).

### Gaps
- Faltam números sobre ganho de taint inter-procedural (CodeQL/Semgrep Pro) vs. padrão local; a busca retornou PyFlow (arXiv 2608.07026) e outro estudo (2401.09767) que não foram lidos. Gap aberto.

## 5. Segurança operacional de agentes autônomos agendados

### Takeaway
Fontes convergem: privilégio mínimo e credenciais curtas, aprovação humana antes de ações com efeito (merge, segredos), tratar todo conteúdo de repositório como entrada não confiável, sandbox, limites/orçamentos, kill switch e logs imutáveis; ataques reais de prompt injection via conteúdo de repositório em agentes de CI foram documentados em 2026.

### Cited Findings
- [OFIC, OWASP GenAI Top 10 for Agentic Applications, 2025-12-09, via resumo secundário] Controles transversais: Least Agency (ASI02/03/05); aprovação humana (ASI01/02/05/09); kill switch (ASI04/10); logging/observabilidade (ASI08/09/10); entrada não confiável (ASI01/06). Detalhes: tokens curtos e escopo por tarefa, limpar memória entre tarefas, execução em sandbox como não-root, orçamentos/rate limits/circuit breakers, logs à prova de adulteração, cada agente com dono e data de expiração, teste de kill switch. — [NeuralTrust](https://neuraltrust.ai/blog/owasp-top-10-for-agentic-applications-2026) [SEC; confirmar no site OWASP GenAI]
- [OFIC/relatório de pesquisa, CSA, 2026-05-03] Ataques "Comment and Control"/PromptPwnd: instruções ocultas em comentários de PR, issues, comentários de código induziram agentes (Claude Code Security Review Action, Gemini CLI Action, Copilot Agent) em GitHub Actions a vazar segredos. Mitigações: `permissions` explícitas e somente leitura por padrão, evitar `pull_request_target` com código de fork, credenciais curtas, jobs com segredo só após aprovação de code owner, separar decisão do agente da camada que detém credenciais, tratar PR/issue/commit/comentários como não confiáveis, separar conteúdo não confiável das instruções no prompt, restringir ferramentas (revisor não precisa de push), testar prompt injection, monitorar runner. — [CSA Labs](https://labs.cloudsecurityalliance.org/research/csa-research-note-ai-github-actions-security-20260503-csa-st/)
- [OWASP via resumo] Registro de estado entre execuções: tratar memória como superfície de ataque (proveniência, segmentação, escanear escritas, rollback). — [NeuralTrust](https://neuraltrust.ai/blog/owasp-top-10-for-agentic-applications-2026)
- [survey 2026] Adulterar o sistema de avaliação é modo documentado de reward hacking; logo, o agente não deve ter permissão de escrita sobre o avaliador, o manifesto de teste ou os conjuntos congelados. — [arXiv 2604.13602](https://arxiv.org/pdf/2604.13602)

### Inferences
- Para o Sentinel: o agente deve rodar sem segredos além do necessário, com permissões read-only para o repositório analisado, abrir PR (não fazer merge) para mudanças de regra, com revisão humana que veja um resumo de risco/diffs de métricas gerado por script (não só a narrativa do agente); limite máximo de iterações por execução e condição de parada por regressão ou por divergência proxy-vs-auditoria; arquivo de estado versionado, apenas anexável, com hashes dos conjuntos de teste e verificação de integridade no início da execução; código/README/comentários dos repositórios analisados tratados como dados.
- Não há na literatura encontrada um número "certo" para limite de iterações; é decisão de projeto.

### Gaps
- Não consultei diretamente o PDF oficial do OWASP GenAI Security Project (apenas resumo secundário com data confirmada 2025-12-09); o NIST AI RMF ou orientações Anthropic/Google sobre agentes agendados não foram pesquisados.
- Não encontrei fonte específica sobre "registro de estado entre execuções" além do ASI06.

## Fontes de baixa confiança / cautelas gerais
- Blogs de fornecedores (ShiftLeft/Fluid Attacks) e Tian Pan são opinião/marketing; o resumo Emergent Mind e o NeuralTrust são secundários.
- O artigo Esposito tem inconsistências internas de contagem (183.769 vs 176.451 casos) e mapeamento manual de ferramentas; usar tendência, não valores exatos.
- Nenhuma fonte encontrada trata diretamente do cenário Sentinel; as comparações são por analogia.
