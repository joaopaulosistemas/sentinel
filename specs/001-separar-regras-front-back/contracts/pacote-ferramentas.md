# Contrato: chave `side` nas ferramentas dos pacotes

Aplica-se a `audit-rules/packs/*/tools/*.yml`. A chave é opcional.

```yaml
- id: njsscan
  name: "njsscan (Node.js)"
  category: security
  side: back                      # novo: back | front | common
  applies-to: ["**/*.js", "**/*.mjs", "**/*.cjs", "**/*.ts"]
  command: [...]
```

| Valor | Efeito com o lado desligado |
|---|---|
| `back` | não roda se o back-end estiver desligado no sistema |
| `front` | não roda se o front-end estiver desligado |
| `common` | roda sempre (achados por arquivo são tratados como na `Duplicação`, ver `research.md` R4) |
| ausente | deduzido de `applies-to` por `RuleSide.infer`; na dúvida, `common` |

Erros de carregamento: valor fora de `back|front|common` falha o carregamento da ferramenta com a mesma mensagem de chave inválida dos outros campos.

Ferramentas embutidas e o lado final (de `research.md` R3): `njsscan` back; `squawk`, `sqlfluff` e `jscpd` common; as demais, deduzidas.
