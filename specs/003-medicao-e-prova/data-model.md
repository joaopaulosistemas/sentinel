# Modelo de dados (Fase 1): Medição e prova

Os dados são arquivos versionados. Campos em português, no estilo dos arquivos que já existem em `audit-rules/calibracao/`. Formatos completos com exemplo em [contracts/arquivos.md](contracts/arquivos.md).

## Entidades

### Repositório medido (`audit-rules/calibracao/repositorios.json`)

| Campo | Regra |
|---|---|
| `repo` | `dono/nome`; único em todo o arquivo |
| `grupo` | `treino`, `validacao` ou `reservado`; **exatamente um**; deve coincidir com `conjuntos.json` |
| `origem` | `proposital` (vulnerável de propósito) ou `real` |
| `pilhas` | lista não vazia, só valores do escopo fixo (princípio VI): `JAVA`, `SPRING`, `QUARKUS`, `ANGULAR`, `REACT`, `VUE`, `TYPESCRIPT`, `JAVASCRIPT`, `HTML`, `CSS`, `DOCKER`, `KUBERNETES`, `SQL` |
| `commit` | SHA completo de 40 caracteres |

Todo repositório que aparece em `conjuntos.json` precisa ter linha aqui (a checagem falha se faltar).

### Conjunto (`conjuntos.json`, existente, estendido)

Ganha o grupo `reservado` com `repositorios_fixos`, `regra` (texto de conduta) e `marcos` (nomes dos marcos em que pode ser medido). O hash da composição dos três grupos fica em `docs/evolucao/estado.json` (mecanismo atual, estendido).

### Gabarito (`gabaritos/`, `gabaritos/validacao/`, novo `gabaritos/reservado/`)

Formato atual (`repo`, `falhas[]` com `arquivo`, `linha`, `tolerancia`, `regras`, `cwe`, `nota`, `fonte`). Para validação e reservado, `fonte` é obrigatória em cada falha. Acrescenta-se `historico[]` opcional: `{data, motivo, antes, depois}` para correções (FR-015). Falha pode ter `lacuna-sem-regra: true` (já existente).

### Medida de um repositório (saída existente de `medir-volta.py extrair`, estendida)

Acrescenta: `commit`, `inicio`, `fim`, `duracao_s`, `arquivos`, `versao_sentinel`, `versoes_ferramentas`, `completa` (bool) e `motivo_incompleta`. Medida incompleta nunca vira zero (FR do spec, caso de borda).

### Medição (`docs/evolucao/medicao/<id>.json`)

| Campo | Regra |
|---|---|
| `id` | `AAAAMMDD-<rótulo>`; único |
| `conjunto` | `treino`, `validacao` ou `reservado` |
| `marco` | obrigatório quando o conjunto é `reservado` |
| `versao_sentinel`, `versoes_ferramentas` | as do momento |
| `proposta_sha256` | hash da proposta vigente; ausente se não havia proposta |
| `por_pilha` | mapa pilha → `{estado: "medida" | "sem-medida", motivo, categorias}` |
| `categorias[nome]` | `{casos, achados, tp, fp, taxa_acerto{valor, ic95, n, amostra_pequena}, taxa_alarme_falso{...}}` |
| `tempo` | por repositório `{duracao_s, arquivos}` e agregados |
| `excluidos` | contagem de código gerado ou de terceiros fora da conta |
| `verificado`, `nao_verificado` | listas de texto (FR-016) |

### Linha de base (`docs/evolucao/medicao/linha-de-base.json` e `.md`)

É a primeira medição completa antes do Premium: uma `Medição` por conjunto mais a seção `lacunas[]` (`{pilha, falta, prioridade}`), gerada a partir delas. Marca `versao_sentinel` e `commit` do repositório do Sentinel.

### Proposta de mudança (`audit-rules/calibracao/propostas/<id>.json`)

| Campo | Regra |
|---|---|
| `id`, `titulo`, `spec` | identificação (ex.: `004`, `006`) |
| `criterios.metrica` | categoria ou conjunto a avaliar |
| `criterios.ganho_minimo` | número ≥ 0 |
| `criterios.alarme_falso_max_aumento` | número ≥ 0 |
| `criterios.tempo_max_aumento_pct` | número ≥ 0, ou `null` (sem limite declarado: só destaca) |
| `criterios.tolerancia_categoria` | queda máxima aceita por categoria (padrão do projeto: 0,02) |
| `semente` | inteiro para o bootstrap |
| `linha_de_base` | id da medição usada como "antes" |

O `sha256` do arquivo (bytes) é o identificador da proposta.

### Veredito (`docs/evolucao/medicao/vereditos/<id-proposta>.json`)

`{proposta, proposta_sha256, antes, depois, ganho{valor, ic95}, criterios[{nome, ok, valor}], pos_medida(bool), resultado: "fica"|"sai"|"inconclusivo", recusado(bool), motivo}`.

### Amostra de revisão e veredito humano

Reaproveita as linhas de `docs/evolucao/revisao-humana.md` (`| rodada | repositório | regra | local | agente | humano |`). A amostra é sorteada de `codigo-real.json` com semente, e o arquivo guarda, para o revisor externo (spec 008), uma versão **sem** a coluna do agente.

### Registro de consulta do reservado (`audit-rules/calibracao/reservado/consultas.json`)

Lista só-acrescenta de `{data, versao_sentinel, marco, motivo, medicao}`; o total por versão é calculado, não editado.

## Relações e invariantes

- Um repositório pertence a um único grupo; `repositorios.json` ↔ `conjuntos.json` ↔ `estado.json` (hash) precisam concordar.
- Todo gabarito aponta para um repositório existente do grupo certo; o do reservado só mora em `gabaritos/reservado/`.
- Veredito exige medição "antes" e "depois" com o mesmo `conjunto`, os mesmos `commit`s e as mesmas `versoes_ferramentas`, exceto a mudança.
- "fica" exige `pos_medida = false`, `recusado = false`, todos os critérios `ok` e `ganho.ic95[0] > 0`.
- Correção de número ou gabarito acrescenta `historico`; nada é sobrescrito sem registro.

## Estados

- **Medida de repositório**: `completa` → entra nas contas; `incompleta` → fica fora, com motivo, e aparece na lista de pendências.
- **Pilha no relatório**: `medida` ou `sem-medida` (sem gabarito, sem repositório ou todas as medidas incompletas).
- **Veredito**: `fica`, `sai`, `inconclusivo`, ou recusado (entradas incomparáveis).
- **Repositório reservado**: `reservado` → (ato explícito, irreversível) → `treino`; nunca volta.
