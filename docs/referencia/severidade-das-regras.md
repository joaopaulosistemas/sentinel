# Severidade das regras do Sentinel (critério único)

Criado em 2026-11-06 a partir da revisão de conflitos entre regras. Vale para regras novas e para a revisão das antigas.

| Situação | Severidade | Exemplo |
|---|---|---|
| Falha de segurança com a origem comprovada (taint da requisição até o ponto perigoso) ou segredo/credencial literal | ERROR | `sentinel-sql-injection`, `sentinel-command-injection-requisicao`, `sentinel-senha-fixa-no-codigo`, `sentinel-compose-segredo-em-env` |
| Mesmo ponto perigoso, mas sem prova de que a entrada vem do usuário (concatenação, variável passada direto, uso de API arriscada) | WARNING | `sentinel-command-injection-variable`, `sentinel-node-sql-concatenado`, `sentinel-android-sqlite-concatenado` |
| Ausência de endurecimento (falta `USER`, falta `Secure`, falta cabeçalho) | WARNING | `sentinel-docker-sem-user`, `sentinel-cookie-sem-secure` |
| Estilo, manutenção, qualidade | INFO (ou WARNING quando o defeito é real, como catch vazio) | `sentinel-ts-var`, `sentinel-tsp-*`, `sentinel-cct-*` |

Regras:
- Duas regras do mesmo CWE e da mesma classe da tabela têm a mesma severidade. Uma exceção precisa de `metadata.severity-justificativa` na regra.
- Toda regra `category: security` leva `metadata.cwe`.
- Duas regras Sentinel que apontam o mesmo defeito na mesma linha entram em um tópico `order` do `dedup.yml` (da mais específica para a mais genérica); fica uma só, com a maior severidade.
- Limite de parâmetros por função: 5, igual ao analisador CleanCode e ao guia CLAUDE.md (`sentinel-cct-parametros-demais` só marca 6 ou mais).
- Exceção por nome de variável (`path` no XSS de JSP, `CAMPO_KEY` na chave fixa) só com o limite escrito na mensagem da regra.

Verificação: `python3 scripts/verificar-regras.py` confere ids únicos, CWE nas regras de segurança e se os ids dos tópicos de `dedup.yml` existem. Ainda não está ligado ao CI (exige mexer em `.github/`, decisão do João).
