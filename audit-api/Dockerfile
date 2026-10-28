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
ARG INSECURE_SSL=true
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
# git: clonar os repositórios auditados. curl: healthcheck.
# Semgrep (SAST): instalado por padrão; para uma imagem menor use --build-arg WITH_SEMGREP=false
# (sem ele o SAST é ignorado com um aviso no log).
ARG WITH_SEMGREP=true
# Mesma versão do CI (scripts/requirements-ci.txt) e do benchmark: motor e portão medem o mesmo Semgrep.
ARG SEMGREP_VERSION=1.172.0
# Trivy (vulnerabilidades e IaC) e Gitleaks (segredos + histórico): quando presentes substituem os analisadores
# internos equivalentes. Imagem menor/sem rede na build: --build-arg WITH_TRIVY=false --build-arg WITH_GITLEAKS=false
ARG WITH_TRIVY=true
ARG WITH_GITLEAKS=true
ARG GITLEAKS_VERSION=8.21.2
# PMD (lint Java, precisa só do JRE que a imagem já tem) e ESLint (lint TypeScript, precisa de Node 18+, por isso
# vem ligado porque a stack é Angular/React/TypeScript: --build-arg WITH_ESLINT=false tira o Node 20, o ESLint e o Stylelint).
ARG WITH_PMD=true
ARG PMD_VERSION=7.7.0
ARG WITH_ESLINT=true
# Analisadores de mercado do pacote market-tools: njsscan, Checkov e SQLFluff (cada um em venv próprio), Hadolint e kube-linter
# (binários) e Stylelint (CSS/SCSS, precisa do Node: vem junto com WITH_ESLINT). --build-arg WITH_MARKET_TOOLS=false
# deixa a imagem menor.
ARG HADOLINT_VERSION=2.12.0
ARG KUBE_LINTER_VERSION=0.7.2
ARG KUBE_SCORE_VERSION=1.19.0
ARG CHECKSTYLE_VERSION=10.21.4
ARG WITH_MARKET_TOOLS=true

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
    && if [ "$WITH_SEMGREP" = "true" ]; then pip3 install --no-cache-dir "semgrep==${SEMGREP_VERSION}"; fi \
    && if [ "$WITH_TRIVY" = "true" ]; then \
         curl -fsSL -o /tmp/trivy.key https://aquasecurity.github.io/trivy-repo/deb/public.key \
         && gpg --dearmor -o /usr/share/keyrings/trivy.gpg /tmp/trivy.key && rm -f /tmp/trivy.key \
         && echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" > /etc/apt/sources.list.d/trivy.list \
         && apt-get update && apt-get install -y --no-install-recommends trivy; \
       fi \
    && if [ "$WITH_GITLEAKS" = "true" ]; then \
         ARCH="$(uname -m | sed 's/x86_64/x64/;s/aarch64/arm64/')" \
         && curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_${ARCH}.tar.gz" \
            | tar -xz -C /usr/local/bin gitleaks; \
       fi \
    && if [ "$WITH_PMD" = "true" ]; then \
         apt-get install -y --no-install-recommends unzip \
         && curl -fsSL -o /tmp/pmd.zip "https://github.com/pmd/pmd/releases/download/pmd_releases%2F${PMD_VERSION}/pmd-dist-${PMD_VERSION}-bin.zip" \
         && unzip -q /tmp/pmd.zip -d /opt && rm /tmp/pmd.zip \
         && ln -s /opt/pmd-bin-${PMD_VERSION}/bin/pmd /usr/local/bin/pmd; \
       fi \
    && if [ "$WITH_MARKET_TOOLS" = "true" ]; then \
         apt-get install -y --no-install-recommends python3-venv \
         && for spec in "njsscan:njsscan" "checkov:checkov" "sqlfluff:sqlfluff"; do \
              bin="${spec%%:*}"; pkg="${spec#*:}"; \
              python3 -m venv "/opt/venvs/$bin" \
              && "/opt/venvs/$bin/bin/pip" install --no-cache-dir "$pkg" \
              && ln -s "/opt/venvs/$bin/bin/$bin" "/usr/local/bin/$bin"; \
            done \
         && HL_ARCH="$(uname -m | sed 's/aarch64/arm64/')" \
         && curl -fsSL -o /usr/local/bin/hadolint "https://github.com/hadolint/hadolint/releases/download/v${HADOLINT_VERSION}/hadolint-Linux-${HL_ARCH}" \
         && chmod 755 /usr/local/bin/hadolint \
         && KL_SUFFIX="$(uname -m | sed 's/x86_64//;s/aarch64/_arm64/')" \
         && curl -fsSL "https://github.com/stackrox/kube-linter/releases/download/v${KUBE_LINTER_VERSION}/kube-linter-linux${KL_SUFFIX}.tar.gz" \
            | tar -xz -C /usr/local/bin kube-linter \
         && KS_ARCH="$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')" \
         && curl -fsSL "https://github.com/zegl/kube-score/releases/download/v${KUBE_SCORE_VERSION}/kube-score_${KUBE_SCORE_VERSION}_linux_${KS_ARCH}.tar.gz" \
            | tar -xz -C /usr/local/bin kube-score \
         && mkdir -p /opt/checkstyle \
         && curl -fsSL -o /opt/checkstyle/checkstyle.jar "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar"; \
       fi \
    && if [ "$WITH_ESLINT" = "true" ]; then \
         curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
         && apt-get install -y --no-install-recommends nodejs; \
       fi \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system audit && useradd --system --gid audit --create-home audit
ENV SEMGREP_SEND_METRICS=off SEMGREP_ENABLE_VERSION_CHECK=0 TRIVY_CACHE_DIR=/home/audit/.cache/trivy
WORKDIR /app
COPY --from=build /build/audit-api/target/quarkus-app/ /app/
COPY audit-rules/packs /app/audit-rules/packs
COPY audit-rules/bin/sentinel-pack /usr/local/bin/sentinel-pack
RUN chmod 755 /usr/local/bin/sentinel-pack
COPY audit-rules/lint/eslint /opt/eslint
COPY audit-rules/lint/stylelint /opt/stylelint
COPY audit-rules/lint/jscpd /opt/jscpd
COPY audit-rules/lint/extras /opt/extras
COPY audit-rules/lint/checkstyle/checks.xml /opt/checkstyle/checks.xml
RUN if [ "$WITH_ESLINT" = "true" ]; then \
      npm --prefix /opt/eslint install --omit=dev --no-audit --no-fund && ln -s /opt/eslint/node_modules/.bin/eslint /usr/local/bin/eslint \
      && npm --prefix /opt/stylelint install --omit=dev --no-audit --no-fund && ln -s /opt/stylelint/node_modules/.bin/stylelint /usr/local/bin/stylelint \
      && npm --prefix /opt/jscpd install --omit=dev --no-audit --no-fund \
      && npm --prefix /opt/extras install --omit=dev --no-audit --no-fund; \
    fi
# Remove as configurações inseguras: a imagem final volta a verificar SSL normalmente
RUN rm -f /root/.curlrc /root/.npmrc /etc/pip.conf /etc/apt/apt.conf.d/99insecure \
    && (git config --system --unset-all http.sslVerify || true)
RUN mkdir -p /tmp/sentinel-audit-clones /var/lib/sentinel && chown -R audit:audit /app /tmp/sentinel-audit-clones /var/lib/sentinel
USER audit
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]