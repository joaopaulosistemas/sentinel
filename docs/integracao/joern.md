# Taint entre arquivos com o Joern

Ferramenta opcional do pacote `market-tools` (`tools/taint.yml`). Lê o código-fonte Java com o frontend `javasrc2cpg`
do [Joern](https://joern.io), **sem compilar e sem baixar dependências**, e grava SARIF.

| Arquivo | Para quê |
|---|---|
| `taint-entre-arquivos.sc` | Script do Joern: entradas (endpoints Spring MVC e JAX-RS), pontos perigosos (SQL, comando, caminho de arquivo), saneadores e saída SARIF |
| `rodar-joern.sh` | Chama o Joern numa pasta temporária (ele grava um `workspace/` no diretório atual) e apaga no fim |
| `testes/` | Casos: `vulneravel-<classe>-*` precisa achar; `seguro-*` não pode achar nada |
| `testar.py` | Roda os casos (`JOERN_HOME=/caminho/joern-cli python3 -I audit-api/joern/testar.py`) |

## Instalar na máquina (fora do Docker)

```bash
curl -fsSLO https://github.com/joernio/joern/releases/download/v4.0.583/joern-cli.zip
echo "74e072163b7f1fc1a371a265ca6f1a94a54507eb20bb831babb288d40f4d278e  joern-cli.zip" | sha256sum -c -
unzip -q joern-cli.zip -d /opt/joern          # precisa de Java 21
JOERN_HOME=/opt/joern/joern-cli audit-api/joern/rodar-joern.sh <projeto> saida.sarif
```

Na imagem Docker: `docker compose build --build-arg WITH_JOERN=true audit-api` (ou `WITH_JOERN=true docker compose build audit-api`).
O pacote `market-tools` precisa estar liberado para rodar programa (`PACKS_ALLOW_TOOLS` ou pacote confiável).

## O que ele relata

Só caminhos que passam por mais de um arquivo. Dentro de um arquivo o Opengrep já cobre; relatar de novo duplicaria o alerta.
Quem não quiser essa regra roda o script com `--param onlyCrossFile=false`.

| Regra | CWE | Entrada | Ponto perigoso |
|---|---|---|---|
| `joern-taint-sqli-entre-arquivos` | 89 | parâmetro anotado de endpoint (`@RequestParam`, `@PathVariable`, `@QueryParam`...) | `JdbcTemplate.query*`/`update`, `createQuery`, `executeQuery`... (1º argumento) |
| `joern-taint-cmdi-entre-arquivos` | 78 | idem | `Runtime.exec`, `ProcessBuilder` |
| `joern-taint-path-traversal-entre-arquivos` | 22 | idem | `File`, `Files`, `Paths`, `FileInputStream`... |

Fluxos cujo argumento no ponto perigoso é só texto fixo (por exemplo `createQuery("... :id")` com `setParameter`) são descartados: o dado passa pelo objeto (`this.em`), não pela consulta. Caso de teste: `audit-api/joern/testes/seguro-jpql-parametrizado`.

Saneadores: `parseInt`, `parseLong`, `parseDouble`, `parseFloat`, `parseBoolean`, `UUID.fromString`, `encodeForSQL`, `encodeForOS`.

## Limites conhecidos

- Só Java. Angular, React e Vue seguem só com o Opengrep.
- Reconhece pontos de entrada e saída por nome e anotação: bibliotecas com outros nomes não aparecem até alguém acrescentar no script.
- Sem o bytecode, tipos de bibliotecas externas ficam sem resolução; o fluxo depende dos nomes dos métodos.
- Não mede falso negativo ainda: os 6 casos de `testes/` provam que o mecanismo funciona, não que acha tudo.

## Como saber que rodou

- **Tela de acompanhamento e tela de resultado:** o card "Ferramentas externas (SARIF)" (analisador SARIF) traz, na primeira linha da nota, a situação do Joern:
  `Joern (taint entre arquivos): ativo, rodou em 12 s com 1 achado.` Os outros casos são "não instalado nesta imagem. Construa com
  WITH_JOERN=true para ligar", "sem permissão para executar programas neste pacote" (libere o pacote ou ative `PACKS_ALLOW_TOOLS`),
  "não se aplica: o projeto não tem arquivos Java", "rodou sem gerar resultado", "falhou" e "desligado neste sistema" (o pacote `market-tools`
  está fora do perfil ou desligado nos ajustes). Em seguida vem a lista das demais ferramentas (`Rodaram: …`, `Não instaladas: …`).
  Se o analisador SARIF inteiro for desligado nos ajustes, nenhuma ferramenta externa roda e a nota não é gerada.
- **Log da API:** `docker compose logs audit-api | grep -i "joern"` mostra `Fonte SARIF 'joern-taint' rodou em N s: M achado(s)`.
- **Achados:** vêm com a ferramenta "Joern (taint entre arquivos)" e regras `joern-taint-*`.
