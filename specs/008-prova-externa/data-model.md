# Modelo de dados: prova externa

Tudo em arquivos versionados (Markdown e JSON), nada de banco.

## RelatorioDeProva
`versao`, `data`, `metodo`, `conjuntos[]` (commit fixo por repositório, `reproduzivel`, `publicavel`), `versoes` (Sentinel e cada ferramenta, com SHA), `numeros[]`, `limites[]`, `verificado[]`, `naoVerificado[]`, `historico[]`.

## NumeroPublicado
`pilha`, `categoria`, `taxa`, `k`, `n`, `ic95` (limites), `amostraPequena` (n < 20), ou `semMedida` com o que falta. Toda taxa tem `n` e `ic95` (SC-003).

## AmostraExterna
`semente`, `criterio` (código aberto público; ≥ 100 achados, ≥ 10 repositórios, ≥ 1/3 em Java/Spring ou Angular), `itens[]` (repositório, commit, regra, arquivo, linha, link). **Sem** campos de veredito.

## VereditoExterno
`item`, `veredito` (VP, FP, DUVIDA), `revisor` (identificador, declarando independência do projeto e não da empresa), `data`, `nota`.

## Concordancia
`itensComparados`, `concordantes`, `percentual`, `divergencias[]` (item, veredito interno, veredito externo), `precisaoPorRegra[]` (k, n, ic95), `regrasSemEvidencia[]` (quando concordância < 80 %), `pendentes[]`.

## ComparacaoDeMercado
`ferramenta`, `versao`, `sha`, `mapaRegras` (arquivo e commit anterior à medição), `categoria`, `tpr`, `fpr`, `n`, `ic95`, `licencaConferida`; mesma definição de acerto e de alarme falso para todas, o Sentinel incluído.

## MedicaoFinal
`versao`, `data`, `motivo`, `resultado` (do registro da 003), `totalConsultasReservado`; exatamente uma por versão publicada.

## EvidenciaDeAceite
`servico`, `repositorio` (público de teste), `execucaoUrl`, `alertasExportados` (JSON), `data`, `acaoHumana: true`.

## CorrecaoPublicada
`versao`, `numero`, `antigo`, `novo`, `motivo`, `data`; só se acrescenta (histórico append-only).
