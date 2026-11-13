# Contrato: linha de comando `scripts/medicao.py`

Sempre `python3 -I scripts/medicao.py <subcomando> ...`. Somente biblioteca padrão. Saídas de texto em português. Resultado de máquina em JSON com chaves ordenadas e fim de linha `\n`, para que duas execuções com as mesmas entradas deem arquivos idênticos.

## Códigos de saída (valem para todos)

| Código | Significado |
|---|---|
| 0 | sucesso |
| 1 | falha de conteúdo (violação de regra, veredito "sai", dado inconsistente) |
| 2 | uso incorreto (argumento faltando ou inválido) |
| 3 | entrada incomparável ou sem medida (comparação recusada; nada a medir) |

## `relatorio`

```
medicao.py relatorio --conjunto treino|validacao|reservado --medidas <pasta> [--marco <nome>] [--saida <pasta>] [--benchmark <json>] [--precisao <md>]
```

- Lê as medidas por repositório de `--medidas`, os gabaritos do grupo, `repositorios.json` e, se dados, o resultado do OWASP Benchmark e o `precisao.md`.
- Escreve `<id>.json` e `<id>.md` em `--saida` (padrão `docs/evolucao/medicao/`).
- Toda taxa sai com `n` e `ic95`; `n < 20` marca `amostra_pequena`.
- Pilha do escopo sem medida sai como `sem-medida` com motivo, **nunca** como zero e fica fora das médias.
- Para `--conjunto reservado`, exige `--marco` e acrescenta uma linha ao registro de consultas.
- Termina o `.md` com "Verificado" e "NÃO verificado".

## `comparar`

```
medicao.py comparar --proposta <arquivo.json> --antes <medicao.json> --depois <medicao.json> [--saida <arquivo>]
```

- Calcula o `sha256` da proposta e confere com `proposta_sha256` da medição "depois" (a "antes" é a linha de base, anterior à proposta; ela é identificada por `linha_de_base`, que precisa ser o `id` da medição "antes").
- Recusa (saída 3) se `conjunto`, `commit`s ou `versoes_ferramentas` diferirem além da mudança, e diz o que difere.
- Intervalo do ganho por bootstrap pareado com a `semente` da proposta.
- Saída 0 para "fica", 1 para "sai", 3 para "inconclusivo" ou recusado (não é erro, é "ainda não"); o JSON tem o `resultado` explícito.
- Marca `pos_medida` e nunca dá "fica" quando o hash falta ou não confere.

## `reservado`

```
medicao.py reservado consultas [--versao <v>]     # total de consultas por versão
medicao.py reservado promover <repo> --motivo <texto>   # ato explícito e irreversível: reservado → treino, registra e atualiza o hash
```

`promover` pede confirmação no terminal (`--sim` para os testes) e altera `conjuntos.json`, `repositorios.json`, `estado.json` e o registro.

## `amostra`

```
medicao.py amostra --codigo-real <json> --achados <pasta> --n <int> --semente <int> --saida <md> [--cego]
```

Sorteio reproduzível (mesma semente, mesma amostra), com cobertura das regras de mais achados. `--cego` omite a coluna do veredito do agente (para o revisor externo).

## `revisao`

```
medicao.py revisao --arquivo <revisao-humana.md> [--minimo-amostra 20]
```

Precisão por regra **só** sobre linhas com veredito humano, com `ic95`, contagem de pendentes, "sem evidência" abaixo do mínimo e concordância agente × pessoa com lista das divergências. Saída 1 se a concordância ficar abaixo de 0,80 (mesma regra do script atual).

## `lacunas`

```
medicao.py lacunas --medicao <json>
```

Lista, por pilha do escopo sem medida, o que falta e a prioridade (pilhas com mais repositórios `real` e nenhuma medida primeiro).

## Extensões em scripts existentes

| Script | Mudança |
|---|---|
| `verificar-separacao.py` | checagens 5 a 8 (reservado); mesmos códigos de saída; opções atuais mantidas |
| `auditoria-completa.sh` | acrescenta `Duração (s)` e `Arquivos de código` ao resumo, sem mudar as linhas atuais |

Nenhum script existente muda de interface de forma incompatível (FR-012).
