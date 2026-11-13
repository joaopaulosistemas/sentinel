# Roteiro de validação: spec 006

1. **Três camadas** (SC-001): repositório de teste Spring (controlador → serviço → repositório, em 3 arquivos) com concatenação; o achado traz origem, passos e destino com arquivo e linha; a versão parametrizada não tem achado.
2. **Quarkus** (FR-002): o mesmo caso com `@QueryParam` e Panache.
3. **Sanitizador** (esclarecimento 2): `Integer.parseInt` ou lista de permitidos declarada encerra o rastro, sem achado; o relatório de cobertura registra o sanitizador; função "bonita" fora da lista não sanitiza.
4. **Propagador** (esclarecimento 3): `StringBuilder.append` e `String.format` seguem o dado; método de biblioteca fora da lista vira rastro perdido.
5. **Rastro perdido** (SC-005): reflexão, `CompletableFuture`, limite de profundidade; 100% no relatório de cobertura com motivo, 0 como achado, 0 como seguro.
6. **Caminho truncado** (História 2): caminho acima do limite vem marcado.
7. **Fusão** (História 5): o mesmo destino achado pelo padrão e pelo rastreio vira um achado com as duas fontes.
8. **Medição** (SC-002, SC-003): categoria `sqli` do Benchmark e gabaritos de treino: padrão × Joern × rastreio; outras categorias dentro da trava; sem a régua da 003, comparação manual e sem "fica".
9. **Classe nova** (SC-006): acrescentar um arquivo de definição sem tocar o motor.
10. **Tempo** (SC-007): tempo adicional por repositório registrado.
