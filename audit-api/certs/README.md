# Certificados corporativos (inspeção SSL)

Esta pasta (`audit-api/certs/`) recebe o(s) certificado(s) raiz da empresa, para o build do Docker
confiar na rede corporativa. A imagem final instala o CA no sistema (Trivy, Gitleaks, git, curl) e no truststore da JVM.

Ela precisa existir e ter pelo menos um arquivo, senão o `COPY audit-api/certs/` do Dockerfile falha
com `"/audit-api/certs": not found`. Este README já cumpre esse papel. Os `.crt` colocados aqui não são versionados
(`.gitignore`): cada máquina usa o CA da sua própria rede.

## Como colocar o certificado

1. Obtenha o CA raiz da empresa com a infra/segurança. Ou veja quem assina a conexão:

   ```bash
   openssl s_client -connect pypi.org:443 -showcerts </dev/null 2>/dev/null | grep -E "s:|i:"
   ```

   Se o emissor (`i:`) for da empresa, e não Let's Encrypt, DigiCert etc., esse é o CA que precisa entrar.

2. Copie para esta pasta com extensão `.crt` e formato PEM (o arquivo começa com `-----BEGIN CERTIFICATE-----`):

   ```bash
   cp /caminho/do/corp-ca.crt audit-api/certs/corp-ca.crt
   ```

3. Se o certificado for `.cer` ou `.pem`, renomeie para `.crt` (o `update-ca-certificates` só considera `.crt`).
   Se for binário (DER), converta:

   ```bash
   openssl x509 -inform DER -in corp-ca.cer -out audit-api/certs/corp-ca.crt
   ```

Atalho (na raiz do projeto): `./audit-api/setup-certs.sh /caminho/do/certificado` faz tudo isso e valida o resultado.

Um arquivo com vários certificados (por exemplo a cadeia completa do Zscaler: raiz + intermediária) funciona: o build
separa cada um e instala todos.

### Exemplo no WSL com Zscaler

Se o CA já está instalado no WSL (`/usr/local/share/ca-certificates/zscaler.crt`):

```bash
cp /usr/local/share/ca-certificates/zscaler.crt audit-api/certs/zscaler.crt
```

## Build

```bash
docker compose build --no-cache audit-api
```

No log do estágio de runtime, o `update-ca-certificates` deve mostrar **"1 added"** (ou mais) em vez de "0 added".

Para conferir depois de subir:

```bash
docker compose up -d
docker compose exec audit-api trivy image --download-db-only
```

Sem erro de certificado, o Trivy está baixando a base normalmente.

## Sem o certificado ainda

Para destravar o build sem o CA, use uma destas opções (elas só adiam o problema, os outros downloads continuam a falhar):

- `WITH_SEMGREP=false` no `.env` (ou `--build-arg WITH_SEMGREP=false`) pula só o Semgrep.
- Rodar o build fora da VPN ou da rede da empresa resolve todos os passos de uma vez.

## Proxy

Se o build também precisar de proxy, defina no `.env`: `HTTP_PROXY`, `HTTPS_PROXY` e `NO_PROXY`.
O compose repassa esses valores como build args.

## Segurança

Este diretório guarda só certificados públicos (CA raiz). Nunca coloque chaves privadas aqui.
