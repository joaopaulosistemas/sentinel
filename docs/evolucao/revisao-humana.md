# Revisão humana por amostragem

Objetivo: dar ao Sentinel uma verdade que não venha de um modelo de linguagem. A cada rodada da Descoberta o agente acrescenta
aqui **20 achados** sorteados entre os repositórios auditados (de preferência de severidade média ou alta e de regras diferentes),
com o link para a linha. A pessoa que revisa preenche a coluna **Veredito** com `VP` (verdadeiro positivo), `FP` (falso positivo)
ou `?` (duvidoso) e, se quiser, a **Nota**. Cinco minutos por rodada bastam.

O agente, na rodada seguinte, calcula a concordância entre o veredito dele (`precisao.md`) e o humano, publica o número no relatório
e **corrige o `precisao.md` quando houver divergência**. Concordância abaixo de 80% significa que a revisão do agente não é confiável
e que as taxas de `precisao.md` devem ser lidas com desconfiança.

| Rodada | Repositório | Regra | Local | Veredito do agente | Veredito humano | Nota |
|---|---|---|---|---|---|---|
| (a próxima rodada da Descoberta preenche as 20 primeiras linhas) | | | | | | |
