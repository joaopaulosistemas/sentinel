---

description: "Tarefas da spec 008: Sentinel Premium 6 — Prova externa"
---

# Tarefas: Sentinel Premium 6 — Prova externa

**Entrada**: `specs/008-prova-externa/` (plan, spec, research, data-model, contracts/, quickstart)
**Testes**: **obrigatórios** (princípio II): cada fatia traz os testes **antes** do código.
**Organização**: por história; as fatias do plano (A a F) são os PRs. **Nada roda sem o João mandar implementar.**

## Formato: `[ID] [P?] [História] Descrição com caminho`

- **[P]**: paralelizável (arquivos diferentes, sem dependência pendente)
- **[USn]**: US1 relatório repetível, US2 revisão externa, US3 mercado, US4 reservado, US5 aceite real
- ⚠️ **Autorização**: o João precisa liberar antes (`.github/`)
- 👤 **Ação humana**: não é tarefa do agente (julgamento do revisor, criar repositório, decidir publicar); o relatório a mostra como pendente até ocorrer
- Toda fatia termina com entrada em `docs/evolucao/LOG.md` (Verificado / NÃO verificado).
- Testes de `scripts/prova.py` ficam em `scripts/test_prova.py`, módulo carregado por caminho (compatível com `python3 -I`).

## Mapa de fatias

| Fatia | Fase | PR |
|---|---|---|
| A | 1, 2 e 3 (US1) | `prova.py` esqueleto, `verificar`, `montar` ⚠️ depende da 003 |
| B | 4 (US2) | Amostra cega e concordância |
| C | 5 (US3) | Comparação de mercado |
| D | 6 (US4) | Reservado e `reproduzir` ⚠️ `ci.yml` |
| E | 7 (US5) | Aceite real |
| F | 8 | Fechamento |

---

## Fase 1: Preparação (fatia A)

- [ ] T001 Criar `scripts/fixtures/prova/` com medidas de teste (números só de teste, com `_comentario` dizendo isso), um `repositorios.json` de teste com `publicavel`, um registro de consultas ao reservado e vereditos de teste internos e externos
- [ ] T002 [P] Criar o esqueleto de `scripts/prova.py` (subcomandos `montar`, `verificar`, `amostra-externa`, `concordancia`, `comparar-mercado`, `reproduzir`, `aceite`, cada um devolvendo "não implementado" com código 2) e o de `scripts/test_prova.py`
- [ ] T003 ⚠️ **Autorização**: acrescentar `scripts/test_prova.py` à linha de testes dos scripts em `.github/workflows/ci.yml`

## Fase 2: Fundação (bloqueia as histórias; fatia A)

- [ ] T004 [P] Teste de varredura: `scripts/prova.py` não usa `subprocess`, `os.system`, `eval` nem `exec` sobre código de repositório (princípio VII) em `scripts/test_prova.py`
- [ ] T005 [P] Testes de `verificar`: relatório sem seção obrigatória falha; taxa sem `n` ou `ic95` falha (SC-003); pilha sem medida deve dizer "sem medida" e o que falta (FR-003); sem "Verificado" e "NÃO verificado" falha (FR-012)
- [ ] T006 [P] Testes de publicabilidade: trecho de código ou achado individual de repositório com `publicavel: false`, ou URL fora da lista, falha (FR-011, SC-007)
- [ ] T007 [P] Testes do histórico: trocar um número já publicado sem linha em `prova-historico.md` é recusado (FR-012) e a linha acrescentada preserva a anterior
- [ ] T008 Implementar `verificar` e a gravação determinística (chaves ordenadas, `\n` final) em `scripts/prova.py`
- [ ] T009 Estender a validação de `audit-rules/calibracao/repositorios.json` com o campo `publicavel` (extensão do contrato da 003, `contracts/relatorio-prova.md`)

**Checkpoint**: `python3 -I scripts/test_prova.py` verde.

---

## Fase 3: História 1 — Relatório repetível por terceiro (P1) 🎯 MVP (fatia A)

**Pré-requisito**: 003 implementada. **Teste independente**: roteiros 1 e 2.

- [ ] T010 [P] [US1] Teste de `montar`: duas execuções com a mesma entrada dão bytes idênticos; saem as 12 seções na ordem do contrato; conjuntos com commit fixo e versões (SC-001, FR-001, FR-002)
- [ ] T011 [P] [US1] Teste: repositório que saiu do ar ou mudou aparece como "não reproduzível"; diferença entre treino, validação e reservado é publicada e marcada para explicação (FR-009)
- [ ] T012 [US1] Implementar `montar` a partir das saídas de `medicao.py` da 003 (sem recalcular estatística própria)
- [ ] T013 [P] [US1] Teste de `reproduzir`: refaz as medições de teste e compara; detecção e alarme falso exatos, tempo dentro da tolerância declarada
- [ ] T014 [US1] Implementar `reproduzir` e escrever no relatório as instruções para quem não participou do projeto (método, conjuntos, versões, comandos)
- [ ] T015 👤 **Ação humana**: uma pessoa de fora segue as instruções e confere que os números coincidem (SC-001); registrar o resultado no relatório

## Fase 4: História 2 — Revisão por pessoa independente (P1) (fatia B)

- [ ] T016 [P] [US2] Testes de `amostra-externa`: mesma semente dá a mesma amostra; só código aberto público; critério de 100 achados, 10 repositórios e um terço em Java/Spring ou Angular verificado; o arquivo **não** contém veredito do agente nem do João (FR-004) em `scripts/test_prova.py`
- [ ] T017 [P] [US2] Testes de `concordancia`: precisão por regra com Wilson 95 %; concordância em percentual; cada divergência listada; abaixo de 80 % as regras divergentes viram "sem evidência"; item sem veredito externo fica "pendente" e **nunca** é completado com veredito interno (FR-005, SC-002, caso de borda)
- [ ] T018 [US2] Implementar `amostra-externa` sobre `medicao.py amostra --cego` (003)
- [ ] T019 [US2] Implementar `concordancia` e a seção 7 do relatório, com a declaração de que a independência é em relação ao projeto, não à empresa
- [ ] T020 [US2] Registrar, em divergência relevante, a correção de regra ou de veredito interno em `docs/evolucao/precisao.md` e no histórico (FR-006)
- [ ] T021 👤 **Ação humana**: o revisor externo (colega do João) julga a amostra; o que ficar sem revisão sai como pendente; SC-002 só é dado como atendido quando ele concluir

## Fase 5: História 3 — Comparação honesta com o mercado (P2) (fatia C)

- [ ] T022 [P] [US3] Testes de `comparar-mercado`: mesma definição de acerto e alarme falso para todas as ferramentas, o Sentinel incluído; resultado desfavorável incluído; versão sem SHA fixo é recusada (FR-007, SC-005)
- [ ] T023 [P] [US3] Teste: o mapa regra→categoria precisa estar commitado **antes** dos achados (data do arquivo anterior ao arquivo de resultados) em `scripts/test_prova.py`
- [ ] T024 [US3] Escrever `audit-rules/calibracao/mercado/opengrep-comunidade.json` e `joern.json` (mapas de regra para categoria) e fixar versões e SHAs; **conferir antes a licença** do conjunto de regras da comunidade e registrar o resultado
- [ ] T025 [US3] Implementar `comparar-mercado` e a seção 8 do relatório, com parágrafo de limites
- [ ] T026 [US3] ⚠️ **Autorização**: job dedicado ou manual em `.github/workflows/ci.yml` que roda as ferramentas sobre os mesmos conjuntos e publica o resultado como artefato

## Fase 6: História 4 — Medição final no reservado (P1) (fatia D)

- [ ] T027 [P] [US4] Testes: exatamente uma medição final por versão publicada; segunda tentativa na mesma versão é recusada; o total de consultas ao reservado entra no relatório (FR-008, SC-004)
- [ ] T028 [P] [US4] Teste: resultado do reservado pior que o do treino e da validação é publicado com a diferença e o espaço da explicação (História 4, cenário 2)
- [ ] T029 [US4] Ler o registro de consultas da 003 em `prova.py` e montar a seção 6
- [ ] T030 👤 **Ação humana**: o João decide a versão a publicar e autoriza a medição final (ato único e irreversível na 003)

## Fase 7: História 5 — Aceite em ambiente real (P2) (fatia E)

- [ ] T031 [P] [US5] Teste de `aceite --modelo`: gera o modelo de evidência (serviço, repositório público de teste, URL da execução, alertas exportados, data) e `verificar` exige o preenchimento antes de marcar SC-006 como atendido
- [ ] T032 [US5] Implementar `aceite --modelo` e escrever o roteiro passo a passo (repositório público de teste, fluxo `upload-sarif` de `docs/integracao/github-actions.yml`, exportação dos alertas pela API)
- [ ] T033 👤 **Ação humana**: o João cria o repositório público de teste e o fluxo (fora deste repositório), roda e anexa a evidência; marcar o item do `PLACAR.md` só depois

## Fase 8: Fechamento (fatia F)

- [ ] T034 Rodar o roteiro completo do `quickstart.md` com o relatório final e `prova.py verificar`; conferir 0 trechos não publicáveis (SC-007)
- [ ] T035 Atualizar `docs/evolucao/PLACAR.md` (eixos 3 e 10) **somente** com o que tiver evidência; registrar tudo no `docs/evolucao/LOG.md` e o relatório fica privado até o João decidir publicar

## Dependências

003 implementada antes de tudo (e 004 a 007 entregues e medidas). Fase 2 antes das histórias; US1 antes de US2 a US4 (o relatório é o contêiner); US2 depende de T021 para fechar; US3 depende de T024 antes de T025; T026 e T003 dependem da autorização; F por último.
