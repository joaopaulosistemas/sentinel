# `.sentinel.yml`: ajustes do projeto dentro do próprio repositório

Um arquivo `.sentinel.yml` na **raiz** do repositório auditado diz ao Sentinel, com revisão de código como qualquer outra mudança, o que não deve contar na nota. Não precisa cadastrar nada no dashboard.

```yaml
version: 1

# Caminhos (glob) que não são código do produto. Não entram na auditoria.
exclude:
  - "docs/**"
  - "src/test/fixtures/**"
  - "*.generated.ts"        # sem barra: vale em qualquer pasta
  - "vendor/"               # barra no fim: a pasta inteira

# Achados aceitos. Continuam na lista, marcados como suprimidos, com o motivo; só deixam de pesar na nota.
suppress:
  - rule: sentinel-java-retorna-null-colecao   # id exato, sufixo depois do ponto ou prefixo com *; maiúsculas não contam
    path: "src/main/java/pack/Yamls.java"      # opcional (glob); sem ele vale para o projeto todo
    reason: "null é o sinal de valor inválido" # obrigatório
    until: 2027-06-30                          # opcional; depois da data a supressão deixa de valer
```

## Como o Sentinel trata o arquivo

- **Transparência.** Cada auditoria informa quantos arquivos saíram por `exclude` e quantos achados foram suprimidos. Achado suprimido aparece na lista com a linha "Suprimido em .sentinel.yml: motivo".
- **Motivo obrigatório.** Supressão sem `rule` ou sem `reason` é ignorada e vira aviso no log da auditoria.
- **Validade.** Com `until`, a supressão expira sozinha; depois da data o achado volta a pesar e o aviso diz qual regra expirou.
- **Segurança.** O repositório auditado é dado não confiável: o arquivo só é lido se for comum (nunca link simbólico), tem no máximo 256 KB e 200 entradas, e o YAML é lido apenas com tipos básicos. Arquivo inválido nunca derruba a auditoria: o que não der para ler é ignorado e os problemas são registrados.
- **Ordem.** A política do perfil e a deduplicação vêm antes; depois `exclude`, depois `suppress`, e só então a confiança e a nota.

## Dicas

- Comece pequeno: exclua só o que claramente não é produto (fixtures, código gerado, vendor).
- Prefira `suppress` com `until` a excluir: o achado continua visível e volta a pesar quando a data passa.
- O próprio Sentinel usa o arquivo: veja o `.sentinel.yml` da raiz deste repositório.
