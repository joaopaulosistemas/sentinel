# Modelo de dados: arquitetura verificável

Em memória durante a auditoria. Guardados com o resultado: os achados de desvio (já são achados) e o resumo de métricas.

## Modulo
`id`, `tipo` (BUILD, PACOTE), `raiz`, `pilha`, `sistema` (monorepo: separado por sistema declarado).

## CamadaInferida
`nome` (WEB, SERVICO, DADOS, ENTIDADE), `origem` (anotação ou estereótipo do modelo), classes; classe sem sinal fica em `SEM_CAMADA` (nunca adivinhada).

## DependenciaDoMapa
| Campo | Regra |
|---|---|
| de, para | Modulo ou classe |
| estado | RESOLVIDA, NAO_RESOLVIDA (com motivo) |
| evidencias | pelo menos uma: arquivo e linha de um uso (FR-001) |

## ArquiteturaAlvo (extensão de `ArchitecturePolicy`)
Mantém `layers`, `forbid`, `allow`, `strict`; acrescenta por regra `indirect`, no topo `cycles`, `exceptions[]` e `metrics` (limites).

## Excecao
`from`, `to`, `reason`, `registered`, `expires` (obrigatória). Estado calculado: VIGENTE ou VENCIDA, pela data da auditoria.

## Desvio (vira achado, categoria ARCHITECTURE, em modo sombra até o "fica")
`regra`, `tipo` (DIRETO, INDIRETO, CICLO, DECLARADA_SEM_EVIDENCIA), `caminho[]` (classe a classe, com arquivo e linha), identidade = regra + par origem→destino, sem a linha.

## ViolacaoPlantada (teste)
`repositorio`, `patch`, `tipo`, `origem`, `destino`, `esperado: true` (gabarito em JSON).

## MetricasDoModulo (guardadas como resumo)
`modulo`, `entram`, `saem`, `instabilidade` (saem ÷ (saem + entram), 0 a 1; indefinida e marcada se ambos forem 0), `dependenciasEntreModulos` (contagem bruta), `fanOutMaximo`, `ciclos`.

## TendenciaDeErosao (calculada, não guardada)
Entre duas auditorias: violações novas, resolvidas, persistentes (rota de diferença existente) e a variação das métricas.
