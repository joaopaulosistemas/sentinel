# Pesquisa: Sentinel Premium 2 — Menos alarme falso e só o que é novo no PR

Levantado no repositório em 2026-11-13. Cada decisão tem o motivo e as alternativas descartadas.

## O que já existe (corrige o "O que falta" da spec)

A spec foi escrita antes de ler o código a fundo. Conferido agora:

| Peça | Onde | Estado |
|---|---|---|
| Identidade do achado sem número de linha (regra + arquivo + trecho normalizado, com ordinal para repetidos) | `service/Fingerprints.java` | **existe**; cobre mudança de linha e reformatação (espaço e quebra de linha viram um espaço) |
| Marca "novo" contra a última auditoria **completa** do branch | `service/FindingLifecycleService.java` (`isNew`, `Baseline`) | **existe**, mas só guarda conjuntos de fingerprints: não sabe severidade nem contagem anterior |
| Filtro de PR | `service/ChangedFiles.java`, `FindingRefinement.onlyChangedFiles` | por **arquivo** (`git diff`); sem tratamento de renomeação; sem base, mantém tudo |
| Resumo para o comentário do PR | `audit-cli` `--pr-comment pr.md` (`MarkdownWriter`); o pipeline posta (`docs/integracao/github-actions.yml`) | lista os achados da auditoria de PR; não separa novo, piorado e existente |
| Código gerado por cabeçalho | `service/GeneratedFiles.java` + `pack/PathRole.GENERATED` | **detecta**; hoje só rebaixa a confiança para `LOW` (e o peso na nota só muda com `sentinel.audit.score.confidence-weighting.enabled`, desligado) |
| Nível de confiança do achado | `model/Confidence` (HIGH, MEDIUM, LOW), `service/FindingConfidence.java` | **existe**, por sinais (teste, valor de exemplo, regra que o time recusa, CVE); **não** vem de precisão medida e não tem "sem medida" |
| Qualidade da regra pelas decisões do time | `service/RuleQualityService.java` (amostra mínima 5, taxa de revisão 0,4) | existe; mede supressões, não revisão independente |
| Precisão por revisão | `docs/evolucao/precisao.md` (por regra: revisados, VP, FP, duvidosos) e `scripts/precisao-regras.py` | existe; é a fonte da faixa de confiança |
| Modo sombra por perfil | `shadow: true` em `audit-rules/packs/profiles/*.yml` | existe |
| Agrupamento de achados repetidos | — | **não existe** |

Consequência: o trabalho é **estender o que existe**, não criar do zero, e tudo atrás de chave desligada (FR-011).

## D1. A base do PR é a auditoria completa guardada do branch base

**Decisão:** "novo" = fingerprint ausente da última auditoria completa do branch base. Reaproveita `Baseline` e `isNew`.
**Motivo:** já existe, é barato (não audita duas vezes) e a identidade já ignora linha e formatação.
**Alternativas:** (a) auditar o commit base junto, a cada PR: exato, mas dobra o tempo e a carga; (b) olhar só as linhas do `git diff`: simples, mas um trecho movido ou reformatado conta como novo e quebra SC-002; (c) a mistura de (b) com fingerprint: mais complexa sem ganho sobre a escolha.

## D2. Quando a base é decidível (FR-003)

**Decisão:** a comparação vale para um arquivo se existe auditoria completa do branch base **e** o commit dela é ancestral do commit do PR **e** o arquivo não mudou no branch base entre esse commit e o ponto de partida do PR (`git diff --name-only <commit da auditoria> <merge-base>`). Arquivo fora dessa condição fica `INDECIDIDO`: mostra todos os seus achados, com a nota no comentário. Sem auditoria de base: o PR inteiro cai no comportamento atual por arquivo, com aviso.
**Motivo:** decide por arquivo, sem rejeitar o PR inteiro porque a base andou; nunca esconde nada em silêncio. O custo é um `git diff --name-only` a mais.
**Alternativa:** distância em commits (N commits atrás): arbitrária e não diz nada sobre o arquivo em questão.

## D3. Renomeação e movimentação (FR-002)

**Decisão:** `ChangedFiles` passa a ler `git diff --name-status -M` e devolver também os pares antigo→novo; a busca do fingerprint na base usa o caminho antigo quando o arquivo foi renomeado. Trecho movido dentro do arquivo já é coberto pelo fingerprint. Movido **entre** arquivos (sem renomear) conta como novo no destino e é um limite conhecido, registrado no LOG.
**Alternativa:** fingerprint sem caminho: apaga a diferença entre arquivos e funde achados distintos.

## D4. "Piorado" (esclarecido em 2026-11-13)

**Decisão:** a base passa a guardar, por fingerprint, a severidade, e, por (regra, arquivo), a contagem. Piorado = severidade maior que a da base, ou contagem da mesma regra no mesmo arquivo maior (o excedente é o piorado; o fingerprint com ordinal cuida de repetidos idênticos).
**Motivo:** é o que o João definiu; custa duas colunas de leitura na base, sem tabela nova.

## D5. Código gerado fora da nota (FR-005)

**Decisão:** reaproveitar `GeneratedFiles` e `PathRole.GENERATED`. Nova exclusão automática com motivo próprio: achado de categoria diferente de segurança em arquivo gerado fica fora da nota e do comentário, e conta no relatório. Segurança permanece. Categoria vem do catálogo da regra, sem classificação nova. Chave `sentinel.audit.generated.exclude-non-security`, **desligada** por padrão.
**Motivo:** o `.sentinel.yml exclude` já mostra como retirar da nota sem apagar o achado; não há como o "exclude" do projeto ser a única via porque o objetivo é valer para todos sem configurar.
**Alternativa:** ligar `confidence-weighting`: só reduz o peso (0,4), não tira da nota nem do comentário, e mexe também em teste e valor de exemplo.

## D6. Faixa de confiança por precisão medida (FR-008)

**Decisão:** campo novo e separado, `ruleBand` (ALTA, MEDIA, BAIXA, SEM_MEDIDA), mais `ruleBandSample` com k, n e o intervalo. Faixa pelo limite inferior de Wilson de 95 %: alta ≥ 80 %, média 50 % a 79 %, baixa < 50 %; "sem medida" quando a amostra é menor que a mínima da spec 003 (n < 20 → intervalo largo demais) **ou** a regra foi corrigida depois da amostra. Dado: `scripts/precisao-regras.py --json` gera `audit-rules/calibracao/precisao-por-regra.json` a partir de `precisao.md`; a API lê o arquivo empacotado junto do catálogo de regras.
**Motivo:** o `confidence` atual descreve o **achado** por sinais; a faixa descreve a **regra** por medida. Misturar mudaria o que existe (FR-011) e confundiria a leitura.
**Pendência:** o corte "n < 20" é herdado da spec 003 (amostra mínima da régua); como a 003 foi revertida para só especificação, o valor fica fixado aqui e ajustado quando a régua existir.

## D7. Agrupamento (FR-007)

**Decisão:** chave do grupo = regra + trecho normalizado (o fingerprint sem o caminho), com contagem e lista de locais. Calculado na leitura (nada novo no banco). Parâmetro opcional `groupBy=cause` na listagem de achados e um interruptor no painel. A nota não usa o grupo.
**Motivo:** reduz leitura sem esconder; "mesma causa" é o mesmo padrão de código.
**Contrato:** parâmetro novo é aditivo, mas mexe na API pública: pede autorização do João (princípio IX).

## D8. Chaves e padrão (FR-009, FR-010, FR-011)

**Decisão:** toda redução entra desligada. Ligar como padrão só depois do veredito "fica" da spec 003 sobre treino e validação, com critérios registrados antes. Quem não liga nada vê o comportamento de hoje.
**Estado real:** a implementação da 003 foi revertida em 2026-11-13; sem a régua, o veredito é manual (recall do gabarito e OWASP Benchmark atuais, com a trava −0,02). O plano diz isso e não finge outra coisa.

## D9. Rebaixar regra por precisão (FR-006, SC-006)

**Decisão:** estender `scripts/precisao-regras.py --verifica` para exigir, em cada `shadow: true` novo, o comentário de origem com amostra e precisão (`# precisao.md: 4 VP de 12`) e conferir contra `precisao.md`; regra corrigida depois da amostra não vale (já é regra do verificador).
**Alternativa:** campo estruturado no YAML do perfil: muda o formato dos perfis e os pacotes.

## Perguntas em aberto

Nenhuma bloqueia o plano. Itens de autorização do João: parâmetro `groupBy` na API pública (fatia E), e a linha de teste no `ci.yml` se a fatia F criar teste de script novo.
