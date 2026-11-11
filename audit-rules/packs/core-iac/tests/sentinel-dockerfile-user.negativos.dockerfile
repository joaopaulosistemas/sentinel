# ok: sentinel-docker-sem-user
FROM eclipse-temurin:21-jre
COPY app.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
