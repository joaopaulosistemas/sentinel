# Build a partir da RAIZ do projeto (o módulo depende do pom pai):
#   docker build -f audit-api/Dockerfile -t sentinel-audit-api .
FROM docker.io/maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
# Camada de dependências (cache): copia só os poms antes do código-fonte
COPY pom.xml .
COPY audit-api/pom.xml audit-api/pom.xml
COPY audit-cli/pom.xml audit-cli/pom.xml
COPY audit-rules/pom.xml audit-rules/pom.xml
RUN mvn -B -q -pl audit-api -am dependency:go-offline || true
COPY audit-api/src audit-api/src
RUN mvn -B -q -pl audit-api -am package -DskipTests

FROM docker.io/eclipse-temurin:21-jre-jammy
# git: clonar os repositórios auditados. curl: healthcheck.
# Semgrep (SAST): instalado por padrão; para uma imagem menor use --build-arg WITH_SEMGREP=false
# (sem ele o SAST é ignorado com um aviso no log).
ARG WITH_SEMGREP=true
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
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl ca-certificates gnupg python3 python3-pip \
    && if [ "$WITH_SEMGREP" = "true" ]; then pip3 install --no-cache-dir semgrep; fi \
    && if [ "$WITH_TRIVY" = "true" ]; then \
         curl -fsSL https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor -o /usr/share/keyrings/trivy.gpg \
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
      cd /opt/eslint && npm install --omit=dev --no-audit --no-fund && ln -s /opt/eslint/node_modules/.bin/eslint /usr/local/bin/eslint \
      && cd /opt/stylelint && npm install --omit=dev --no-audit --no-fund && ln -s /opt/stylelint/node_modules/.bin/stylelint /usr/local/bin/stylelint \
      && cd /opt/jscpd && npm install --omit=dev --no-audit --no-fund \
      && cd /opt/extras && npm install --omit=dev --no-audit --no-fund; \
    fi
RUN mkdir -p /tmp/sentinel-audit-clones && chown -R audit:audit /app /tmp/sentinel-audit-clones
USER audit
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
CMD ["java", "-jar", "/app/quarkus-run.jar"]
