# Contrato: definição de classe de falha (dados)

Arquivo `audit-rules/taint/<classe>.yml`. Classe nova = arquivo novo; o motor não muda (FR-003, SC-006).

```yaml
classe: sqli
cwe: 89
pilhas: [JAVA_SPRING, JAVA_QUARKUS]
origens:
  - anotacao-parametro: [RequestParam, PathVariable, RequestBody, QueryParam, PathParam, HeaderParam]
  - chamada: "javax.servlet.http.HttpServletRequest#getParameter"
destinos:
  - { metodo: "org.springframework.jdbc.core.JdbcTemplate#query", argumento: 0 }
  - { metodo: "jakarta.persistence.EntityManager#createNativeQuery", argumento: 0 }
sanitizadores: ["java.lang.Integer#parseInt", "java.util.UUID#fromString"]
propagadores: ["java.lang.String#concat", "java.lang.StringBuilder#append", "java.lang.String#format"]
```

Validação (teste de esquema): `classe` única, `argumento` inteiro ≥ 0, método qualificado, listas não vazias, sem entrada duplicada.

# Contrato: relatório de cobertura do rastreio

Comando da CLI `sentinel rastreio cobertura <repo>` (texto e `--json`, determinístico): origens, destinos, caminhos, sanitizadores usados, rastros perdidos por motivo e limite atingido. Sem rota nova na API.
