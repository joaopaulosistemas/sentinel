# ruleid: sentinel-docker-imagem-latest
FROM node:latest
# ok: sentinel-docker-imagem-latest
FROM node:20.11-alpine

# ruleid: sentinel-docker-add-url
ADD https://exemplo.com/pacote.tar.gz /tmp/
# ok: sentinel-docker-add-url
ADD pacote.tar.gz /tmp/

# ruleid: sentinel-docker-curl-pipe-shell
RUN curl -fsSL https://exemplo.com/install.sh | sh
# ok: sentinel-docker-curl-pipe-shell
RUN curl -fsSL -o /tmp/install.sh https://exemplo.com/install.sh && sha256sum -c /tmp/install.sh.sha256

# ruleid: sentinel-docker-segredo-em-env
ENV DB_PASSWORD=SuperSecreta123
# ok: sentinel-docker-segredo-em-env
ENV NODE_ENV=production
