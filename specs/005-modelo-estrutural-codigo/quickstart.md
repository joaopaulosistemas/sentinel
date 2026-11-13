# Roteiro de validação: spec 005

1. **Backend** (SC-001): repositório de teste Spring e outro Quarkus com estrutura conhecida; `modelo endpoints` e `modelo chamadores` devolvem exatamente o gabarito, sem item extra.
2. **Não resolvido** (História 1, cenário 3): chamada por reflexão aparece com motivo.
3. **Externo** (FR-013): `jdbcTemplate.query` aparece como relação externa com o nome qualificado.
4. **Frontend** (História 2, FR-014): Angular, React e Vue de teste; componentes, rotas e chamadas conferem; caminho `"/clientes/" + id` sai parcial.
5. **Determinismo** (SC-002): duas execuções, `--json` idêntico byte a byte.
6. **Cobertura** (SC-003): arquivo com erro de sintaxe e outro com reflexão aparecem como não modelados, com motivo; o resto segue.
7. **Piloto** (SC-004): para cada uma das 3 regras, versão nova e antiga convivem; medida registrada; sem régua da 003 não há "fica".
8. **Tempo** (SC-005): tempo de construção registrado por repositório; comparado ao orçamento do João quando existir.
9. **Nada quebra** (SC-006): jobs de regras, Semgrep e benchmark verdes; arquivo não modelado ainda coberto pela regra de padrão (FR-007).
