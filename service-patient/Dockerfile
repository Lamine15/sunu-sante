# Étape 1 : compilation avec Maven (le cache des dépendances est partagé entre les services)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,id=maven,sharing=locked mvn -q -B package -DskipTests

# Étape 2 : image d'exécution légère, sans les outils de compilation
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 sunu && mkdir -p /data && chown sunu /data
COPY --from=build /app/target/service-patient.jar app.jar
ENV STOCKAGE_REPERTOIRE=/data
USER sunu
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
