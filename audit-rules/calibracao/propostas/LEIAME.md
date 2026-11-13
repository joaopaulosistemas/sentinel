# Propostas de mudança

Uma proposta é o que se promete **antes** de medir: qual métrica deve melhorar, quanto, e o que não pode piorar. O veredito (`fica`, `sai` ou `inconclusivo`) sai de `scripts/medicao.py comparar`, que calcula o `sha256` do arquivo da proposta. Se a medição "depois" não carrega esse hash, o veredito vira `pos_medida` e nunca é "fica": critério escrito depois de ver o número não vale.

## Formato

```json
{
 "id": "006-rastreio-sqli",
 "titulo": "Rastreio entre arquivos para injeção de SQL",
 "spec": "006",
 "linha_de_base": "20261112-linha-de-base-treino",
 "semente": 20261112,
 "criterios": {
  "metrica": "sqli",
  "ganho_minimo": 0.05,
  "alarme_falso_max_aumento": 0.0,
  "tempo_max_aumento_pct": 20,
  "tolerancia_categoria": 0.02
 }
}
```

- `linha_de_base`: id da medição "antes"; precisa ser exatamente a medição passada em `--antes`.
- `metrica`: nome de uma categoria, ou `*` para todas juntas.
- `tempo_max_aumento_pct`: `null` quando não há limite; o veredito só destaca o aumento.
- `tolerancia_categoria`: queda máxima aceita em qualquer categoria (padrão do projeto: 0,02).
- `semente`: inteiro do bootstrap pareado; mesma semente, mesmo intervalo.

## Como usar

1. Escreva e versione a proposta aqui (`<id>.json`) **antes** de medir.
2. Meça "depois" com o hash do arquivo (campo `proposta_sha256` da medição).
3. `python3 -I scripts/medicao.py comparar --proposta <arquivo> --antes <medicao> --depois <medicao>`
4. Saída 0 = fica, 1 = sai, 3 = inconclusivo ou recusado (entradas incomparáveis). O veredito fica em `docs/evolucao/medicao/vereditos/<id>.json`.

"Fica" exige: critérios todos atendidos, amostra de pelo menos 20 casos e limite inferior do intervalo de 95 % do ganho acima de zero.

As propostas reais nascem nas specs 004 a 007. O arquivo `scripts/fixtures/medicao/proposta.json` é só de teste.
