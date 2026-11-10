# Build a partir da RAIZ do projeto (o módulo depende do pom pai):
#   docker build -f audit-api/Dockerfile -t sentinel-audit-api .
#
# VERSÃO SEM VERIFICAÇÃO DE SSL (para redes com inspeção SSL, sem instalar o CA da empresa).
# Com INSECURE_SSL=true (padrão), a verificação de certificado é DESLIGADA apenas durante o build
# (Maven, apt, curl, pip, npm e git). As configurações inseguras são removidas ao final, então a
# imagem pronta não carrega nada disso. Para voltar ao normal: --build-arg INSECURE_SSL=false
FROM docker.io/maven:3.9-eclipse-temurin-21-alpine AS build
ARG INSECURE_SSL=true
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
WORKDIR /build
# Maven sem verificação de SSL (3.9 usa o transporte nativo; as flags wagon cobrem plugins antigos)
# $MAVEN_OPTS deve ficar literal (expandido só quando o .mavenrc for lido): aspas simples intencionais.
# hadolint ignore=SC2016
RUN if [ "$INSECURE_SSL" = "true" ]; then \
      echo 'export MAVEN_OPTS="$MAVEN_OPTS -Daether.connector.https.securityMode=insecure -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"' > /root/.mavenrc; \
    fi
# Camada de dependências (cache): copia só os poms antes do código-fonte
COPY pom.xml .
COPY audit-api/pom.xml audit-api/pom.xml
COPY audit-cli/pom.xml audit-cli/pom.xml
COPY audit-rules/pom.xml audit-rules/pom.xml
RUN mvn -B -q -pl audit-api -am dependency:go-offline || true
COPY audit-api/src audit-api/src
RUN mvn -B -q -pl audit-api -am package -DskipTests

FROM docker.io/eclipse-temurin:21-jre-jammy
# Pipes nos RUN abaixo (curl | tar, curl | bash) falham o build se o download falhar
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
ARG INSECURE_SSL=true
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
# git: clonar os repositórios auditados. curl: healthcheck.
# Opengrep (SAST, motor padrão das regras): instalado por padrão; para uma imagem menor use
# --build-arg WITH_OPENGREP=false (sem motor nenhum o SAST é ignorado com um aviso no log).
# Mesma versão e mesmos hashes de scripts/instalar-opengrep.sh (usado no CI): motor e portão medem o mesmo Opengrep.
ARG WITH_OPENGREP=true
ARG OPENGREP_VERSION=v1.30.0
ARG OPENGREP_SHA256_AMD64=35779bdd72e92129c8df2a77f0c55e8c08356801ea92591ef32108d6b28d564c
ARG OPENGREP_SHA256_ARM64=a5d5a4a58ba5d46ff51e921663da1c2bba38f4b03987f4aeec87f16c6ad3ecae
# Semgrep é a alternativa (SENTINEL_SAST_ENGINE=semgrep) e fica desligado por padrão: --build-arg WITH_SEMGREP=true.
# Mesma versão do CI (scripts/requirements-ci.txt), usada nos testes das regras (`semgrep --test`).
ARG WITH_SEMGREP=false
ARG SEMGREP_VERSION=1.172.0
# Trivy (vulnerabilidades e IaC) e Gitleaks (segredos + histórico): quando presentes substituem os analisadores
# internos equivalentes. Imagem menor/sem rede na build: --build-arg WITH_TRIVY=false --build-arg WITH_GITLEAKS=false
ARG WITH_TRIVY=true
ARG WITH_GITLEAKS=true
ARG GITLEAKS_VERSION=8.21.2
ARG GITLEAKS_SHA256_AMD64=5bc41815076e6ed6ef8fbecc9d9b75bcae31f39029ceb55da08086315316e3ba
ARG GITLEAKS_SHA256_ARM64=654c935542c89f565aabe7bf7c6c500830f116c114f0aeb509d2460c1ac2e6da
# PMD (lint Java, precisa só do JRE que a imagem já tem) e ESLint (lint TypeScript, precisa de Node 18+, por isso
# vem ligado porque a stack é Angular/React/TypeScript: --build-arg WITH_ESLINT=false tira o Node 20, o ESLint e o Stylelint).
ARG WITH_PMD=true
ARG PMD_VERSION=7.7.0
ARG PMD_SHA256=be8bf68f6c1d66984bd9645a93e631b78a1c2f42f5f0f8719082fead67553940
ARG WITH_ESLINT=true
# Analisadores de mercado do pacote market-tools: njsscan, Checkov e SQLFluff (cada um em venv próprio), Hadolint e kube-linter
# (binários) e Stylelint (CSS/SCSS, precisa do Node: vem junto com WITH_ESLINT). --build-arg WITH_MARKET_TOOLS=false
# deixa a imagem menor.
ARG HADOLINT_VERSION=2.12.0
ARG HADOLINT_SHA256_AMD64=56de6d5e5ec427e17b74fa48d51271c7fc0d61244bf5c90e828aab8362d55010
ARG HADOLINT_SHA256_ARM64=5798551bf19f33951881f15eb238f90aef023f11e7ec7e9f4c37961cb87c5df6
ARG KUBE_LINTER_VERSION=0.7.2
ARG KUBE_LINTER_SHA256_AMD64=8a0f8ba6c28910acd4886cc4d501ed32f3d404a53ac5e72c45a0dd893fcadb03
ARG KUBE_LINTER_SHA256_ARM64=c9e9c0e55f26e1723c23bef933e2004b4b524714613243c98ea581de13060dd7
ARG KUBE_SCORE_VERSION=1.19.0
ARG KUBE_SCORE_SHA256_AMD64=a5b10e509bd845f0bc32a529f4e8165c021877f924ee6f6be66678039f75a761
ARG KUBE_SCORE_SHA256_ARM64=96b24dda719b75fa7b6d14e5423553bffa09688bd6f9400789b623e30f0e3541
ARG CHECKSTYLE_VERSION=10.21.4
ARG CHECKSTYLE_SHA256=3c1d94d6ecc83e02dff587c9ba5b6b4ec4fec38c7a958eb587efec9b28e2f318
ARG WITH_MARKET_TOOLS=true
ARG NJSSCAN_VERSION=1.0.2
ARG CHECKOV_VERSION=3.4.3
ARG SQLFLUFF_VERSION=4.5.0
# Taint entre arquivos com o Joern (lê o código-fonte Java, sem compilar): ligado só com --build-arg WITH_JOERN=true, porque
# pesa cerca de 2 GB e precisa de memória na análise (SENTINEL_JOERN_XMX, padrão 4g). Ver audit-api/joern/.
ARG WITH_JOERN=false
ARG JOERN_VERSION=v4.0.583
ARG JOERN_SHA256=74e072163b7f1fc1a371a265ca6f1a94a54507eb20bb831babb288d40f4d278e

# SSL desligado SOMENTE durante o build (removido mais abaixo, antes de USER audit)
RUN if [ "$INSECURE_SSL" = "true" ]; then \
      printf 'insecure\n' > /root/.curlrc \
      && printf 'strict-ssl=false\n' > /root/.npmrc \
      && printf '[global]\ntrusted-host = pypi.org files.pythonhosted.org pypi.python.org\n' > /etc/pip.conf \
      && printf 'Acquire::https::Verify-Peer "false";\nAcquire::https::Verify-Host "false";\n' > /etc/apt/apt.conf.d/99insecure; \
    fi

# Versões do apt não ficam fixas: a imagem base Debian troca os pacotes a cada ponto de release e a fixação quebraria o build.
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl ca-certificates gnupg python3 python3-pip \
    && if [ "$INSECURE_SSL" = "true" ]; then git config --system http.sslVerify false; fi \
    && fetch() { curl -fsSL -o "$1" "$2" && echo "$3  $1" | sha256sum -c -; } \
    && if [ "$WITH_OPENGREP" = "true" ]; then \
         arch="$(dpkg --print-architecture)" \
         && if [ "$arch" = "arm64" ]; then dist=aarch64; sha="$OPENGREP_SHA256_ARM64"; else dist=x86; sha="$OPENGREP_SHA256_AMD64"; fi \
         && curl -fsSL -o /usr/local/bin/opengrep "https://github.com/opengrep/opengrep/releases/download/${OPENGREP_VERSION}/opengrep_manylinux_${dist}" \
         && echo "${sha}  /usr/local/bin/opengrep" | sha256sum -c - \
         && chmod 0755 /usr/local/bin/opengrep; \
       fi \
    && if [ "$WITH_SEMGREP" = "true" ]; then pip3 install --no-cache-dir "semgrep==${SEMGREP_VERSION}"; fi \
    && if [ "$WITH_TRIVY" = "true" ]; then \
         curl -fsSL -o /tmp/trivy.key https://aquasecurity.github.io/trivy-repo/deb/public.key \
         && gpg --dearmor -o /usr/share/keyrings/trivy.gpg /tmp/trivy.key && rm -f /tmp/trivy.key \
         && echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" > /etc/apt/sources.list.d/trivy.list \
         && apt-get update && apt-get install -y --no-install-recommends trivy; \
       fi \
    && if [ "$WITH_GITLEAKS" = "true" ]; then \
         if [ "$(uname -m)" = "aarch64" ]; then ARCH=arm64; sha="$GITLEAKS_SHA256_ARM64"; else ARCH=x64; sha="$GITLEAKS_SHA256_AMD64"; fi \
         && fetch /tmp/gitleaks.tgz "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_${ARCH}.tar.gz" "$sha" \
         && tar -xz -C /usr/local/bin -f /tmp/gitleaks.tgz gitleaks && rm /tmp/gitleaks.tgz; \
       fi \
    && if [ "$WITH_PMD" = "true" ]; then \
         apt-get install -y --no-install-recommends unzip \
         && fetch /tmp/pmd.zip "https://github.com/pmd/pmd/releases/download/pmd_releases%2F${PMD_VERSION}/pmd-dist-${PMD_VERSION}-bin.zip" "$PMD_SHA256" \
         && unzip -q /tmp/pmd.zip -d /opt && rm /tmp/pmd.zip \
         && ln -s /opt/pmd-bin-${PMD_VERSION}/bin/pmd /usr/local/bin/pmd; \
       fi \
    && if [ "$WITH_MARKET_TOOLS" = "true" ]; then \
         apt-get install -y --no-install-recommends python3-venv \
         && for spec in "njsscan:njsscan==${NJSSCAN_VERSION}" "checkov:checkov==${CHECKOV_VERSION}" "sqlfluff:sqlfluff==${SQLFLUFF_VERSION}"; do \
              bin="${spec%%:*}"; pkg="${spec#*:}"; \
              python3 -m venv "/opt/venvs/$bin" \
              && "/opt/venvs/$bin/bin/pip" install --no-cache-dir "$pkg" \
              && ln -s "/opt/venvs/$bin/bin/$bin" "/usr/local/bin/$bin"; \
            done \
         && if [ "$(uname -m)" = "aarch64" ]; then \
              HL_ARCH=arm64; HL_SHA="$HADOLINT_SHA256_ARM64"; KL_SUFFIX=_arm64; KL_SHA="$KUBE_LINTER_SHA256_ARM64"; KS_ARCH=arm64; KS_SHA="$KUBE_SCORE_SHA256_ARM64"; \
            else \
              HL_ARCH=x86_64; HL_SHA="$HADOLINT_SHA256_AMD64"; KL_SUFFIX=; KL_SHA="$KUBE_LINTER_SHA256_AMD64"; KS_ARCH=amd64; KS_SHA="$KUBE_SCORE_SHA256_AMD64"; \
            fi \
         && fetch /usr/local/bin/hadolint "https://github.com/hadolint/hadolint/releases/download/v${HADOLINT_VERSION}/hadolint-Linux-${HL_ARCH}" "$HL_SHA" \
         && chmod 755 /usr/local/bin/hadolint \
         && fetch /tmp/kube-linter.tgz "https://github.com/stackrox/kube-linter/releases/download/v${KUBE_LINTER_VERSION}/kube-linter-linux${KL_SUFFIX}.tar.gz" "$KL_SHA" \
         && tar -xz -C /usr/local/bin -f /tmp/kube-linter.tgz kube-linter && rm /tmp/kube-linter.tgz \
         && fetch /tmp/kube-score.tgz "https://github.com/zegl/kube-score/releases/download/v${KUBE_SCORE_VERSION}/kube-score_${KUBE_SCORE_VERSION}_linux_${KS_ARCH}.tar.gz" "$KS_SHA" \
         && tar -xz -C /usr/local/bin -f /tmp/kube-score.tgz kube-score && rm /tmp/kube-score.tgz \
         && mkdir -p /opt/checkstyle \
         && fetch /opt/checkstyle/checkstyle.jar "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar" "$CHECKSTYLE_SHA256"; \
       fi \
    && if [ "$WITH_JOERN" = "true" ]; then \
         apt-get install -y --no-install-recommends unzip \
         && curl -fsSL -o /tmp/joern-cli.zip "https://github.com/joernio/joern/releases/download/${JOERN_VERSION}/joern-cli.zip" \
         && echo "${JOERN_SHA256}  /tmp/joern-cli.zip" | sha256sum -c - \
         && mkdir -p /opt/joern && unzip -q /tmp/joern-cli.zip -d /opt/joern && rm /tmp/joern-cli.zip; \
       fi \
    && if [ "$WITH_ESLINT" = "true" ]; then \
         curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
         && apt-get install -y --no-install-recommends nodejs; \
       fi \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system audit && useradd --system --gid audit --create-home audit
ENV SEMGREP_SEND_METRICS=off SEMGREP_ENABLE_VERSION_CHECK=0 LC_ALL=C.UTF-8 TRIVY_CACHE_DIR=/home/audit/.cache/trivy
WORKDIR /app
# Ordem pensada para o cache: o que quase nunca muda (lint, npm install, CA) vem primeiro; o que muda a cada commit
# (a aplicação compilada e os packs de regras) vem por último, logo antes do chown. Assim uma mudança de código
# não refaz os npm install nem a importação do CA.
COPY audit-rules/lint/eslint /opt/eslint
COPY audit-rules/lint/stylelint /opt/stylelint
COPY audit-rules/lint/jscpd /opt/jscpd
COPY audit-rules/lint/extras /opt/extras
COPY audit-api/joern/taint-entre-arquivos.sc audit-api/joern/rodar-joern.sh /opt/sentinel/joern/
COPY audit-rules/lint/checkstyle/checks.xml /opt/checkstyle/checks.xml
COPY audit-rules/lint/checkstyle/google-checks.xml /opt/checkstyle/google-checks.xml
RUN if [ "$WITH_ESLINT" = "true" ]; then \
      npm --prefix /opt/eslint install --omit=dev --no-audit --no-fund && ln -s /opt/eslint/node_modules/.bin/eslint /usr/local/bin/eslint \
      && npm --prefix /opt/stylelint install --omit=dev --no-audit --no-fund && ln -s /opt/stylelint/node_modules/.bin/stylelint /usr/local/bin/stylelint \
      && npm --prefix /opt/jscpd install --omit=dev --no-audit --no-fund \
      && npm --prefix /opt/extras install --omit=dev --no-audit --no-fund; \
    fi
# CA corporativo (inspeção SSL, ex.: Zscaler): instalado no sistema (Trivy, Gitleaks, git, curl) e no truststore da JVM.
# Sem ele a imagem final verifica o SSL e o Trivy falha ao baixar a base de vulnerabilidades ("x509: certificate signed by
# unknown authority"). Coloque os .crt (PEM) em audit-api/certs/ antes do build (veja audit-api/certs/README.md).
# Arquivo com vários certificados (cadeia completa) é separado em um certificado por arquivo.
COPY audit-api/certs/ /tmp/corp-certs/
RUN set -eu; \
    dest=/usr/local/share/ca-certificates/corp; mkdir -p "$dest"; \
    fontes=0; \
    for f in /tmp/corp-certs/*.crt; do \
      [ -f "$f" ] || continue; fontes=$((fontes + 1)); \
      awk -v out="$dest/$(basename "$f" .crt)" \
        '/-----BEGIN CERTIFICATE-----/{n++; file=out "-" n ".crt"} file{print > file} /-----END CERTIFICATE-----/{close(file); file=""}' "$f"; \
    done; \
    instalados=$(find "$dest" -name '*.crt' | wc -l); \
    if [ "$fontes" -gt 0 ] && [ "$instalados" -eq 0 ]; then \
      echo "ERRO: nenhum certificado PEM válido em audit-api/certs/ (converta DER com: openssl x509 -inform DER -in x.cer -out x.crt)" >&2; exit 1; \
    fi; \
    if [ "$instalados" -gt 0 ]; then \
      update-ca-certificates; \
      if command -v keytool >/dev/null 2>&1; then \
        for c in "$dest"/*.crt; do \
          alias="corp-$(basename "$c" .crt)"; \
          keytool -delete -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1 || true; \
          keytool -importcert -noprompt -trustcacerts -cacerts -storepass changeit -alias "$alias" -file "$c"; \
        done; \
      fi; \
    else \
      echo "AVISO: audit-api/certs/ sem .crt; se a rede tem inspeção SSL o Trivy não conseguirá baixar a base de vulnerabilidades" >&2; \
    fi; \
    rm -rf /tmp/corp-certs

# Remove as configurações inseguras: a imagem final volta a verificar SSL normalmente
RUN rm -f /root/.curlrc /root/.npmrc /etc/pip.conf /etc/apt/apt.conf.d/99insecure \
    && (git config --system --unset-all http.sslVerify || true)

# Camadas que mudam a cada commit (devem ficar por último, depois de tudo que é caro de refazer)
COPY --from=build /build/audit-api/target/quarkus-app/ /app/
COPY audit-rules/packs /app/audit-rules/packs
COPY audit-rules/bin/sentinel-pack /usr/local/bin/sentinel-pack
RUN chmod 755 /usr/local/bin/sentinel-pack \
    && mkdir -p /tmp/sentinel-audit-clones /var/lib/sentinel \
    && chown -R audit:audit /app /tmp/sentinel-audit-clones /var/lib/sentinel
USER audit
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]