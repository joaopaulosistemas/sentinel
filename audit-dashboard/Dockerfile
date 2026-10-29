FROM docker.io/node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM docker.io/nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# O entrypoint oficial do nginx aplica envsubst em /etc/nginx/templates/*.template
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# API_KEY é injetada em tempo de execução (compose/Helm); o script só garante que a variável exista, vazia.
COPY docker-entrypoint-api-key.envsh /docker-entrypoint.d/05-api-key.envsh
USER 101
ENV API_UPSTREAM=http://audit-api:8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
