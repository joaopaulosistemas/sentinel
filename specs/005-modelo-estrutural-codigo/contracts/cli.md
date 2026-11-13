# Contrato: comando `modelo` da CLI

Sem rota nova na API e sem tela (esclarecimento 1 da spec). Executado por `scripts/sentinel-local modelo ...` (ponto de entrada local do `audit-api`, no Docker); o `audit-cli` HTTP não muda. Abaixo, `sentinel modelo` é o nome do comando. Toda saída aceita `--json` determinístico.

```
sentinel modelo endpoints  <repo>                 # método HTTP, caminho, classe, método
sentinel modelo chamadores <repo> <símbolo>       # chamadores diretos, inclusive de outros módulos
sentinel modelo anotacao   <repo> <anotação>      # elementos com a anotação
sentinel modelo dados      <repo>                 # acessos a dados
sentinel modelo cobertura  <repo>                 # modelado, não modelado, gerado excluído, motivos
```

Regras: chamada não resolvida aparece com `estado` e `motivo`; caminho parcial vem com `parcial: true`; duas execuções iguais dão bytes iguais; o código do repositório nunca é compilado nem executado; sem Node, o frontend sai como `naoModelado` com o motivo.
