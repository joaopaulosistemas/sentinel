#!/usr/bin/env bash
# Aquece o cache Maven que o Trivy do Sentinel lê, baixando POMs pai, BOMs e dependências do projeto a ser auditado.
# O audit-api monta esse cache somente leitura (docker-compose.yml, M2_HOST_DIR) e nada na imagem o preenche: sem este
# passo, quando o Maven Central bloqueia o IP (429, Retry-After) a varredura cai para "só pelo cache local" e fica parcial.
#
# Uso: scripts/aquecer-cache-maven.sh <pasta-do-projeto> [pasta-do-cache]
#   pasta-do-cache: onde gravar. Padrão: M2_HOST_DIR (ambiente ou .env da raiz do repositório), senão ./.m2-cache.
#                   É o mesmo caminho que o compose monta em /home/audit/.m2/repository.
# Variáveis opcionais:
#   AQUECER_TENTATIVAS  tentativas antes de desistir (padrão 5)
#   AQUECER_ESPERA      segundos de espera base entre tentativas, multiplicada pelo número da tentativa (padrão 120)
#   MAVEN_MIRROR_URL    espelho do Central (ex.: Nexus da empresa na VPN); sem ele usa o Maven Central
#   INSECURE_SSL=true   desliga a verificação de certificado (redes com inspeção SSL, como o Zscaler); igual ao build da imagem
#   HTTPS_PROXY / HTTP_PROXY / NO_PROXY   repassados ao Maven
# Código de saída: 0 cache aquecido; 1 falhou depois de todas as tentativas; 2 uso incorreto.
set -u

PROJETO="${1:-}"
if [ -z "$PROJETO" ] || [ ! -f "$PROJETO/pom.xml" ]; then
  echo "Uso: $0 <pasta-do-projeto-com-pom.xml> [pasta-do-cache]" >&2
  exit 2
fi
PROJETO="$(cd "$PROJETO" && pwd)"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# M2_HOST_DIR: ambiente primeiro, depois o .env da raiz (sem executá-lo).
if [ -z "${M2_HOST_DIR:-}" ] && [ -f "$RAIZ/.env" ]; then
  M2_HOST_DIR="$(sed -n 's/^M2_HOST_DIR=//p' "$RAIZ/.env" | tail -n 1 | tr -d '"'"'"'\r')"
fi
CACHE="${2:-${M2_HOST_DIR:-$RAIZ/.m2-cache}}"
TENTATIVAS="${AQUECER_TENTATIVAS:-5}"
ESPERA="${AQUECER_ESPERA:-120}"
IMAGEM="docker.io/maven:3.9-eclipse-temurin-21-alpine"

mkdir -p "$CACHE" || { echo "Não consegui criar $CACHE" >&2; exit 2; }
CACHE="$(cd "$CACHE" && pwd)"

OPTS=()
if [ "${INSECURE_SSL:-false}" = "true" ]; then
  OPTS+=("-Daether.connector.https.securityMode=insecure" "-Dmaven.wagon.http.ssl.insecure=true"
         "-Dmaven.wagon.http.ssl.allowall=true" "-Dmaven.wagon.http.ssl.ignore.validity.dates=true")
fi
MVN=(mvn -B -ntp -fae -Dmaven.repo.local=/m2 dependency:resolve)

ENVS=(-e HOME=/tmp -e "MAVEN_OPTS=${OPTS[*]:-}")
for v in HTTPS_PROXY HTTP_PROXY NO_PROXY; do
  [ -n "${!v:-}" ] && ENVS+=(-e "$v=${!v}")
done
ARQS=()
if [ -n "${MAVEN_MIRROR_URL:-}" ]; then
  # settings.xml temporário só com o espelho; o Maven o lê de /tmp/settings.xml dentro do container.
  CFG="$(mktemp)"
  trap 'rm -f "$CFG"' EXIT
  cat > "$CFG" <<XML
<settings><mirrors><mirror><id>sentinel-mirror</id><mirrorOf>central</mirrorOf><url>${MAVEN_MIRROR_URL}</url></mirror></mirrors></settings>
XML
  chmod 644 "$CFG"
  ARQS+=(-v "$CFG:/tmp/settings.xml:ro")
  MVN+=(-s /tmp/settings.xml)
fi

echo "Projeto: $PROJETO"
echo "Cache:   $CACHE"
for n in $(seq 1 "$TENTATIVAS"); do
  echo "Tentativa $n/$TENTATIVAS: mvn dependency:resolve"
  if docker run --rm --user "$(id -u):$(id -g)" "${ENVS[@]}" "${ARQS[@]}" \
      -v "$PROJETO:/project:ro" -v "$CACHE:/m2" -w /project "$IMAGEM" "${MVN[@]}"; then
    echo "Cache aquecido em $CACHE ($(du -sh "$CACHE" 2>/dev/null | cut -f1))."
    echo "Aponte M2_HOST_DIR=$CACHE no .env e rode a auditoria."
    exit 0
  fi
  if [ "$n" -lt "$TENTATIVAS" ]; then
    pausa=$((ESPERA * n))
    echo "Falhou (o Maven Central pode ter limitado o IP). Aguardando ${pausa}s; o que já foi baixado fica no cache." >&2
    sleep "$pausa"
  fi
done
echo "Não foi possível aquecer o cache depois de $TENTATIVAS tentativas. Rode de novo mais tarde ou use MAVEN_MIRROR_URL." >&2
exit 1
