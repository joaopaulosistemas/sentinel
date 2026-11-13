# Modelo de dados: modelo estrutural

Em memória durante a auditoria; nada de tabela nova. Só o resumo (último item) é guardado.

## Unidade
| Campo | Regra |
|---|---|
| id | `arquivo#símbolo`, estável e ordenável |
| tipo | CLASSE, METODO, FUNCAO, COMPONENTE |
| arquivo, linhaInicio, linhaFim | localização |
| anotacoes | lista (Java) ou decoradores (TypeScript) |
| pilha | JAVA_SPRING, JAVA_QUARKUS, ANGULAR, REACT, VUE, TS_JS |

## Relacao
| Campo | Regra |
|---|---|
| origem, destino | ids de Unidade; destino pode ser nome qualificado externo |
| tipo | CHAMA, USA, HERDA, IMPORTA |
| estado | RESOLVIDA, EXTERNA, NAO_RESOLVIDA, PARCIAL |
| motivo | obrigatório quando NAO_RESOLVIDA (reflexão, injeção por nome, proxy, geração por anotação) |

## PontoDeEntrada
rota HTTP (método, caminho, classe e método que atende; caminho `PARCIAL` quando montado por partes), tarefa agendada ou evento.

## AcessoADados
unidade, tipo (CONSULTA, ESCRITA, REPOSITORIO), alvo externo (nome qualificado), linha.

## Cobertura (guardada com o resultado da auditoria)
| Campo | Regra |
|---|---|
| repositorio | chave |
| porConstrucao | mapa tipo → {modelado, naoModelado, geradoExcluido, motivos} |
| tempoConstrucaoMs | medido por repositório |
| limiteAtingido | verdadeiro quando tempo ou memória estourou, com o que ficou de fora |

Validação: percentuais por tipo, sem média única; arquivo sem sintaxe válida é `naoModelado` com motivo e o restante segue.
