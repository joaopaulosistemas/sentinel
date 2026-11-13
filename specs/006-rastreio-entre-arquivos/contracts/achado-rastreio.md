# Contrato: achado de rastreio (**API pública: precisa de autorização do João**)

Campos opcionais novos no achado, ausentes quando `taint.enabled` está desligado:

- `trace`: `{ truncado, passos: [{ arquivo, linha, acao, texto }] }`
- `sources`: lista de textos (`rastreio`, `padrao:<regra>`, `joern`)
- `fix`: texto curto do que faltou para ficar seguro

Sem a chave ligada, a resposta é igual à de hoje. No relatório e no comentário do PR, o caminho aparece como lista ordenada; caminho truncado traz `truncado: true` e quantos passos foram omitidos.
