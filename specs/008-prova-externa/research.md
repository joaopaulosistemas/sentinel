# Pesquisa: Sentinel Premium 6 — Prova externa

Levantado no repositório em 2026-11-13.

## O que já existe

| Peça | Onde | Estado |
|---|---|---|
| OWASP Benchmark por categoria, com baseline | job `benchmark` do `ci.yml`, `scripts/benchmark-owasp.py`, `audit-rules/calibracao/benchmark/` | existe; treino = casos 1 a 1370, validação = 1371 a 2740 |
| Precisão por regra (revisão do agente) | `docs/evolucao/precisao.md`, `scripts/precisao-regras.py` | existe; amostras pequenas |
| Revisão humana por amostragem | `docs/evolucao/revisao-humana.md` | existe o formato; a tabela traz a coluna **"Veredito do agente"** ao lado do humano, e nenhum veredito humano foi preenchido |
| Placar | `docs/evolucao/PLACAR.md` (eixo 10 Credibilidade; eixo 3: "SARIF aceito pelo GitHub Code Scanning em execução real") | existe; item "SARIF de uma auditoria subido ao Code Scanning" **aberto** |
| Relatórios | `docs/evolucao/relatorios/` | pasta existe |
| SARIF e SBOM | `SarifWriter`, `SbomWriter`; modelo de pipeline `docs/integracao/github-actions.yml` (`upload-sarif`) | existem; sem evidência de aceite real |
| Ferramentas externas já integradas | pacote `market-tools` (Checkstyle e outras, `tools/*.yml`), Joern opcional | existem |
| Régua, amostra cega, registro do reservado, linha de base | spec 003 (`scripts/medicao.py`) | **só documentos**: a implementação foi revertida |

Consequência: a 008 **consome** a 003 (relatório por conjunto, `amostra --cego`, `revisao`, registro de consultas ao reservado). Sem a 003 implementada, nada da 008 produz número.

## D1. Relatório de prova gerado por script, versionado e determinístico

**Decisão:** `scripts/prova.py` (Python padrão, sem dependência nova), que **monta** o relatório a partir das saídas da 003, sem recalcular estatística própria. Saída: `docs/evolucao/relatorios/prova-<versão>.md` mais `.json`. Mesma entrada, mesmos bytes. Seções fixas (contrato `relatorio-prova.md`), terminando em Verificado e NÃO verificado (FR-012); o script falha sem essa seção.
**Alternativa:** relatório escrito à mão: não é repetível nem verificável.

## D2. Repetição por terceiro

**Decisão:** `prova.py reproduzir` refaz as medições a partir dos conjuntos com commits fixos (`repositorios.json` da 003) e compara com o `.json` publicado. Números de detecção e alarme falso devem coincidir exatamente; **tempo** só dentro de tolerância declarada no relatório (proposta: 30 %, a calibrar). Repositório que saiu do ar ou mudou fica "não reproduzível" no relatório (caso de borda).

## D3. Revisão externa cega

**Decisão:** a amostra vem de `medicao.py amostra --cego` (003): sorteio com semente registrada, só **código aberto público**, **sem** coluna de veredito do agente nem do João. O arquivo entregue ao revisor é gerado à parte do `revisao-humana.md` (que mostra o veredito do agente); `prova.py` confere que o arquivo de entrega não contém veredito algum. Vereditos: `VP`, `FP`, `?`. `prova.py concordancia` calcula precisão por regra (Wilson 95 %) e concordância com os vereditos internos, **listando cada divergência**. Critério do placar: 100 achados em 10 repositórios públicos, 60 julgados por pessoa, concordância ≥ 80 %, ao menos um terço em Java/Spring ou Angular.
**Revisor:** colega de trabalho do João, que não participou do projeto (esclarecimento de 2026-11-12); o relatório declara que a independência é em relação ao projeto, não à empresa. Julga só código aberto público; o que ficar sem revisão sai como "pendente", nunca completado com veredito interno.
**Abaixo de 80 %:** as regras divergentes ficam "sem evidência de precisão" até serem corrigidas e medidas de novo (SC-002).

## D4. Comparação com ferramentas de mercado

**Restrição:** princípio VII proíbe compilar ou instalar algo do repositório auditado; logo só entram ferramentas que **leem o fonte** sem compilar. CodeQL fica **fora** (o Java exige compilação e a licença limita o uso); SpotBugs e FindSecBugs pelo mesmo motivo (precisam de bytecode).
**Decisão:** duas ferramentas na primeira rodada, ambas já presentes no repositório: **Opengrep com um conjunto público de regras da comunidade** (versão e SHA fixos) e o **Joern** (taint). PMD e ESLint podem entrar como extras. Cada uma tem um **mapa regra→categoria** (`audit-rules/calibracao/mercado/<ferramenta>.json`) escrito e commitado **antes** de ver qualquer resultado, para não escolher o mapa depois do número; a mesma definição de acerto e de alarme falso do `benchmark-owasp.py`; versões fixadas; resultados desfavoráveis publicados como os demais (FR-007). Rodar em job dedicado ou manual, resultado como artefato.
**Pendência de licença:** conferir os termos do conjunto de regras da comunidade antes de rodar e de publicar (pode restringir uso comparativo ou comercial).

## D5. Medição final no reservado

**Decisão:** `medicao.py relatorio --conjunto reservado --marco <versão>` (003), **uma vez** por versão publicada; o registro de consultas da 003 é a fonte. `prova.py verificar` confere "exatamente uma medição final por versão" e inclui o total de consultas no relatório (SC-004). Resultado pior que o do treino é publicado e explicado (História 4).

## D6. Aceite real do formato padrão

**Decisão:** enviar o SARIF de uma auditoria a um repositório **público** de teste (Code Scanning é gratuito em repositório público; em privado depende de plano), com o fluxo de `docs/integracao/github-actions.yml`. Evidência anexada ao relatório: link da execução e exportação dos alertas pela API do GitHub. É **ação do João** (cria o repositório e o fluxo, que é `.github/` fora deste repositório); o plano entrega o roteiro e o modelo de evidência.

## D7. Publicabilidade (FR-011, SC-007)

**Decisão:** `repositorios.json` (003) ganha o campo `publicavel` (verdadeiro só para código aberto com licença que permite referência); `prova.py verificar` falha se o relatório tiver trecho de código ou achado individual de repositório não publicável, ou URL fora da lista. O relatório é **privado por padrão** até o João decidir publicar (premissa). Extensão do contrato de arquivos da 003 registrada em `contracts/relatorio-prova.md`.

## D8. Histórico de correções (FR-012)

**Decisão:** `docs/evolucao/relatorios/prova-publicados.json` guarda os números já publicados; `prova.py` recusa trocar um número publicado sem uma linha em `prova-historico.md` (versão, número antigo e novo, motivo). A versão anterior fica no histórico do git.

## D9. O que é humano

Julgamentos do revisor, criação do repositório do aceite, decisão de publicar e metas extras do João **não** são tarefas do agente. As tarefas marcam essas ações como **Ação humana** e o relatório as mostra como pendentes enquanto não ocorrerem.

## Pendências

1. 003 implementada (régua, amostra cega, registro do reservado) e 004 a 007 entregues e medidas antes: sem isso a 008 não tem o que provar.
2. **Autorização do João:** `ci.yml` (job dedicado da comparação e linha de testes de `scripts/test_prova.py`).
3. Licença do conjunto de regras da comunidade e a tolerância de tempo (30 % proposta).
4. Disponibilidade do revisor externo.
