FROM node:20.11-alpine
# ruleid: sentinel-docker-chmod-777
RUN chmod -R 777 /app
# ok: sentinel-docker-chmod-777
RUN chmod -R 755 /app
# ruleid: sentinel-docker-sudo
RUN sudo apt-get clean
# ok: sentinel-docker-sudo
RUN apt-get clean
# ruleid: sentinel-docker-segredo-em-arg
ARG DB_PASSWORD=admin123
# ok: sentinel-docker-segredo-em-arg
ARG APP_VERSION=1.0.0
# ruleid: sentinel-docker-expose-ssh
EXPOSE 22
# ok: sentinel-docker-expose-ssh
EXPOSE 8080 2222
# ruleid: sentinel-docker-apt-sem-no-recommends
RUN apt-get install -y curl
# ok: sentinel-docker-apt-sem-no-recommends
RUN apt-get install -y --no-install-recommends curl
# ruleid: sentinel-docker-apt-update-sozinho
RUN apt-get update
# ok: sentinel-docker-apt-update-sozinho
RUN apt-get update && apt-get install -y --no-install-recommends curl
