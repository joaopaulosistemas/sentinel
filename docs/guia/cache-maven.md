# Cache Maven do Trivy

Aviso no relatório: *"dependências Maven resolvidas só pelo cache local (o Maven Central limitou as requisições)"*.
O resultado é marcado como **parcial**, porque o Sentinel não sabe se o cache cobre o projeto: se cobre, nada ficou de fora; se não,
o que faltava no cache ficou sem análise.

## Por que acontece

O Trivy resolve POMs pai, BOMs e dependências transitivas. Quando o Maven Central bloqueia o IP (HTTP 429, `Retry-After`),
o Sentinel repete a varredura sem rede (`--offline-scan`) e usa só o que está em `~/.m2/repository`. O compose monta esse
cache em `/home/audit/.m2/repository` **somente leitura**, e nada na imagem o preenche. Cache vazio, varredura parcial.

## Como resolver

Popule o cache uma vez por projeto grande, na máquina onde a auditoria roda:

```bash
scripts/aquecer-cache-maven.sh /caminho/do/projeto-auditado
```

O script roda `mvn dependency:resolve` num container Maven e grava em `M2_HOST_DIR` (ambiente ou `.env`); sem ele, em
`./.m2-cache`, que é o padrão do compose. Se o Central bloquear no meio, ele espera e tenta de novo (o que já foi baixado
fica no cache). Depois, reexecute a auditoria: o aviso deve sumir.

## Cache aquecido: `TRIVY_OFFLINE=true`

Com o cache aquecido, o Trivy ainda tenta o Maven Central primeiro, leva o 429 e só então repete sem rede; por isso o aviso
aparece mesmo com o cache completo. Quem mantém o cache aquecido pode pular essa tentativa:

```
TRIVY_OFFLINE=true   # no .env; docker compose up -d
```

O Trivy passa a rodar sempre com `--offline-scan`, só com o cache de `M2_HOST_DIR`: sem 429 e sem o aviso. O custo é que o
Sentinel deixa de avisar quando o cache não cobre o projeto; dependências ausentes ficam sem análise sem nota. Rode o script
de aquecimento para cada projeto novo antes de ligar essa opção. O padrão é `false`.

`M2_HOST_DIR` é por máquina (o `.env` não é versionado). Em casa e na VPN do trabalho cada uma tem o seu cache.

## Variáveis do script

| Variável | Efeito |
|---|---|
| `AQUECER_TENTATIVAS` / `AQUECER_ESPERA` | Tentativas (padrão 5) e espera base em segundos, multiplicada pela tentativa (padrão 120) |
| `MAVEN_MIRROR_URL` | Espelho do Central, por exemplo o Nexus da empresa na VPN |
| `INSECURE_SSL=true` | Desliga a verificação de certificado (inspeção SSL, como o Zscaler); a mesma ressalva do build da imagem |
| `HTTPS_PROXY`, `HTTP_PROXY`, `NO_PROXY` | Repassados ao Maven |

Se o repositório do projeto for privado, o cache só terá o que o Central e o espelho configurado servem.
