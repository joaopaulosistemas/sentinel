# Pesquisa: Sentinel Premium 4 — Rastreio de dado inseguro entre arquivos

Levantado no repositório em 2026-11-13.

## O que já existe

| Peça | Onde | Estado |
|---|---|---|
| Taint **entre arquivos** em Java com Joern (SQL, comando, caminho de arquivo) | `audit-api/joern/taint-entre-arquivos.sc`, `docs/integracao/joern.md`, job `joern` no `ci.yml` | existe, **opcional** (`WITH_JOERN`, ~2 GB); origens, destinos e saneadores estão **dentro do script**, não em dados |
| Taint dentro de um arquivo | regras `taint` em `audit-rules/packs/core-security/rules/*.yml` | existem (Opengrep) |
| Deduplicação de achados da mesma ferramenta | `audit-rules/packs/core-security/dedup.yml` | existe, mas só vale para regras da **mesma** ferramenta |
| OWASP Benchmark por categoria com baseline | job `benchmark`, `scripts/benchmark-owasp.py`, `audit-rules/calibracao/benchmark/baseline.json` | existe; `sqli`: acerto 0,938, alarme falso 0,69 (hoje); `xpathi` 0,467 / 0,3; `pathtraver` 0,887 / 0,726; `cmdi` 0,921 / 0,712; `ldapi` 0,852 / 0,562 |
| Modelo estrutural | spec 005 (só documentos) | **ainda não existe**; o plano dela define unidades e relações, **não** fluxo de valores |

Correção à spec: a linha de base diz que o Joern cobre só Java e é opcional (certo), mas a classe "injeção de SQL" **já** tem origem, destino e saneadores no script do Joern. O mecanismo novo precisa vencer **isso**, não um vazio.

## D1. Mecanismo: motor próprio em Java sobre o modelo da spec 005

**Decisão:** motor de rastreio interprocedural em Java, no mesmo processo da API, lendo o modelo da 005; definições por classe de falha em dados (`audit-rules/taint/<classe>.yml`, contrato em `contracts/definicao-classe.md`). Joern continua e é comparado.
**Motivo:** FR-001 manda usar o modelo; FR-003 manda definições em dados; roda sem os 2 GB nem o Java 21 do Joern (princípio VIII).
**Alternativa:** manter o script do Joern e fazê-lo ler as definições de um arquivo de dados: menos trabalho, mas continua opcional e pesado, e a comparação "novo × Joern" perderia sentido. O Joern permanece como fonte de comparação.

## D2. Dependência de fluxo de valores (cruza com a spec 005)

**Problema:** o modelo da 005 guarda unidades, chamadas e acessos a dados. Rastrear dado exige também **fluxo de valores**: argumento→parâmetro, atribuição, retorno e propagadores.
**Decisão:** a fatia A da 006 acrescenta uma camada de fluxo de valores **sobre o mesmo leitor Java** (reuso da árvore sintática da 005, fatia B), sem mudar o contrato público do modelo. Sem a 005 pronta, a 006 não começa.
**Alternativa:** pedir que a 005 já inclua fluxo: alarga uma spec ainda não implementada; descartada.

## D3. Algoritmo e limites

**Decisão:** resumo por método (quais parâmetros chegam ao retorno e a quais destinos), propagação interprocedural por fila de trabalho, sem sensibilidade a contexto no começo. Limites configuráveis (FR-010) com **valores iniciais propostos, a calibrar depois da linha de base da 003**: profundidade 12 chamadas, 20 caminhos por destino, 60 s por repositório. Ao atingir: relatório de cobertura do rastreio (esclarecimento 1), nunca "seguro".
**Alternativa:** análise sensível a contexto: mais precisa, bem mais cara; só se a medição mostrar alarme falso por falta dela.

## D4. Definições em dados

**Decisão:** um arquivo por classe (origens, destinos, sanitizadores, propagadores) no pacote `audit-rules`, validado por teste de esquema; classe nova = arquivo novo, sem compilar (princípio V, SC-006). Origens de SQL (Spring/Quarkus): parâmetros anotados (`@RequestParam`, `@PathVariable`, `@RequestBody`, `@QueryParam`, `@PathParam`, `@HeaderParam`) e leituras de `HttpServletRequest`. Destinos (esclarecimento 5): JDBC, `JdbcTemplate`, consulta nativa e JPQL/HQL via `EntityManager` e Panache. Sanitizadores e propagadores partem da lista que o script do Joern já usa (`parseInt`, `parseLong`, `UUID.fromString`, `encodeForSQL`, `setParameter`...), revisada contra o treino (princípio III).

## D5. Achado, convivência e deduplicação

**Decisão:** o achado leva `ruleId` próprio por classe (`sentinel-taint-sqli`), o caminho e as fontes (contrato `achado-rastreio.md`). A fusão com achados do padrão e do Joern usa **classe de falha + destino** (esclarecimento 4), num passo novo de "fusão de fontes" antes da nota; `dedup.yml` não serve porque só vale dentro da mesma ferramenta.
**Contrato público:** campo novo no achado (`trace`, `sources`): mexe na API, **pede autorização do João** (princípio IX).

## D6. Configuração por sistema

**Decisão:** `.sentinel.yml` do sistema (via `ProjectConfig`): `taint.enabled` (padrão **desligado**), `taint.max-depth`, `taint.max-paths`, `taint.timeout-seconds`. Nada em `application.properties` (princípio V, lição do C1 da 004).

## D7. Medição

**Decisão:** categoria `sqli` do OWASP Benchmark (baseline acima) e gabaritos de treino, comparando padrão atual, Joern e rastreio, com a trava de outras categorias do `benchmark-owasp.py`. **Estado real:** a régua da 003 foi revertida; sem ela, a comparação é manual e o "fica" fica **bloqueado**. O Joern precisa rodar no mesmo conjunto (job `joern` do CI).

## D8. Segunda classe de falha

**Decisão:** adiada até a linha de base da 003 (FR-012). Candidatas hoje, pelo placar do Benchmark: `xpathi` (acerto 0,467), `pathtraver`, `ldapi`. A escolha e o motivo vão num arquivo `segunda-classe.md`. Só definições novas, sem tocar o motor.

## Pendências

1. **Autorização do João:** campo novo no achado (API pública).
2. **Dependência:** 005 (fatias A e B) e 003 (régua) antes da medição.
3. **Valores iniciais dos limites** (profundidade, caminhos, tempo) e o orçamento de tempo (SC-007): dependem da linha de base.
