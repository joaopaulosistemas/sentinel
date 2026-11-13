# Modelo de dados: rastreio de dado inseguro

Em memória durante a auditoria. Só o achado e o resumo de cobertura do rastreio são guardados com o resultado.

## DefinicaoDeClasse (arquivo de dados, `audit-rules/taint/<classe>.yml`)
| Campo | Regra |
|---|---|
| classe | id estável (`sqli`) e CWE |
| origens | lista: anotação de parâmetro ou chamada de leitura de requisição |
| destinos | lista: método qualificado + índice do argumento perigoso |
| sanitizadores | lista declarada; só ela encerra o rastro (esclarecimento 2) |
| propagadores | lista declarada; fora dela o rastro é "não resolvido" (esclarecimento 3) |
| pilhas | `JAVA_SPRING`, `JAVA_QUARKUS` |

## Passo
`arquivo`, `linha`, `acao` (ENTRA, PASSA_PARAMETRO, ATRIBUI, PROPAGA, RETORNA, CHEGA_AO_DESTINO), texto curto.

## Caminho
Lista ordenada de Passos da Origem ao Destino; `truncado` (verdadeiro quando cortado por limite) e `passosOmitidos`.

## AchadoDeRastreio (estende o achado)
| Campo | Regra |
|---|---|
| ruleId | `sentinel-taint-<classe>` |
| caminho | Caminho completo ou marcado como truncado (FR-004) |
| fontes | lista (`rastreio`, `padrao:<regra>`, `joern`); mais de uma = fusão |
| correcao | o que faltou para ficar seguro (sanitizador esperado) |
Identidade para fusão: classe + destino (arquivo e linha da execução perigosa, comparados sem depender de reformatação).

## RastroPerdido (**não** é achado)
`origem`, `ultimoPasso`, `motivo` (REFLEXAO, DINAMICO, ASSINCRONO, LIMITE_PROFUNDIDADE, LIMITE_CAMINHOS, LIMITE_TEMPO, PROPAGADOR_DESCONHECIDO). Vai só ao relatório de cobertura do rastreio.

## CoberturaDoRastreio (guardada com o resultado da auditoria)
`origensEncontradas`, `destinosEncontrados`, `caminhosAchados`, `sanitizadoresUsados` (contagem por sanitizador), `rastrosPerdidos[]` (contagem e lista por motivo), `limiteAtingido`, `tempoMs`.

## Configuração por sistema (`.sentinel.yml`, desligada por padrão)
`taint.enabled`, `taint.max-depth`, `taint.max-paths`, `taint.timeout-seconds`.
