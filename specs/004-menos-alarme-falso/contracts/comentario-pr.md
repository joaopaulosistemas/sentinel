# Contrato: comentário do PR (CLI `--pr-comment`)

Com `sentinel.audit.pr.classify` ligado, o arquivo gerado tem, nesta ordem:

1. Título e resumo: `N novos, M piorados`.
2. Aviso de base (só quando há arquivo INDECIDIDO ou nenhuma auditoria base): `Sem base confiável para K arquivos: mostrando todos os achados deles.`
3. Tabela dos novos e piorados: severidade, regra, arquivo:linha, faixa da regra (`alta`, `média`, `baixa`, `sem medida`), e, para piorado, `piorou: severidade` ou `+N ocorrências`.
4. Linha única `N achados antigos fora deste comentário` (omitida quando N = 0). Os antigos seguem no painel e no relatório completo.

Regras: nunca omitir achado de segurança novo; achado em código gerado só aparece se for de segurança; sem a chave ligada o arquivo sai idêntico ao de hoje (teste de regressão byte a byte).

# Contrato: API (aditivo, **precisa de autorização do João**: princípio IX)

- Achado ganha campos opcionais `prClass` (NOVO, PIORADO, EXISTENTE, INDECIDIDO), `ruleBand`, `ruleBandSample`. Ausentes quando as chaves estão desligadas.
- Listagem de achados ganha o parâmetro opcional `groupBy=cause`; resposta traz `groups[]` (chave, contagem, locais, severidade). Sem o parâmetro, resposta igual à de hoje.
