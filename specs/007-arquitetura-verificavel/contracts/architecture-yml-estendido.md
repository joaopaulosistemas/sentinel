# Contrato: `architecture.yml` estendido (compatível)

O arquivo atual continua válido sem mudança. Chaves **opcionais** novas:

```yaml
forbid:
  - from: web
    to: dados
    severity: high
    indirect: true          # novo: segue o caminho por classes intermediárias
    message: "A camada web não acessa dados, nem por intermediário."
cycles: forbid              # novo: ciclo entre módulos é desvio
exceptions:                 # novo
  - from: web
    to: dados
    reason: "Migração do módulo legado, ADR-12"
    registered: 2026-11-13
    expires: 2027-03-31     # obrigatória; vencida, volta a ser desvio
metrics:                    # novo; limites propostos, a calibrar
  max-fan-out: 20
  max-instability: 0.8
```

Validação: `expires` e `reason` obrigatórios em toda exceção; `registered` ≤ `expires`; `from` e `to` existentes em `layers`; chave desconhecida é erro com mensagem útil. Sem as chaves novas, o comportamento é o de hoje.

# Contrato: comando `arquitetura` da CLI (sem rota nova na API)

```
sentinel arquitetura mapa      <repo>   # módulos, camadas inferidas, dependências com evidência, "sem camada", "não resolvida"
sentinel arquitetura desvios   <repo>   # desvios diretos, indiretos, ciclos, exceções vigentes e vencidas
sentinel arquitetura tendencia <repo>   # compara duas auditorias (novas, resolvidas, persistentes; métricas)
sentinel arquitetura medir     <corpus> # violações plantadas: acerto e alarme falso com Wilson 95 %
```

Saída em texto e `--json` determinístico. O relatório e os achados já existentes levam o resultado à API; campo novo na API pública só com autorização do João.

# Contrato: violação plantada (corpus de teste)

`audit-rules/calibracao/arquitetura/<repo>/plantadas.json`: lista de `{ patch, tipo, origem, destino }`; o `patch` é aplicado numa cópia temporária. Repositório limpo acusa 0 desvios fora dos esperados.
