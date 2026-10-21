FROM maven:3.9-eclipse-temurin-21 AS build
USER builder
RUN mvn -q package

# ruleid: sentinel-docker-sem-user
FROM eclipse-temurin:21-jre
COPY --from=build /app/target/app.jar /app.jar
CMD ["java", "-jar", "/app.jar"]
