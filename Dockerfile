########## BUILD ##########
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 1) Dependencies cachen
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# 2) Quellcode & Build
COPY src ./src
RUN mvn -B -DskipTests package

########## RUNTIME ##########
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root User anlegen
RUN addgroup --system app && adduser --system --ingroup app app

# Artefakt als root kopieren
COPY --from=build /workspace/target /app/target

# Jar als root umbenennen und aufräumen (sonst fehlende Schreibrechte)
RUN set -eu; \
    JAR="$(ls -1 /app/target/*.jar | head -n1)"; \
    mv "$JAR" /app/app.jar; \
    rm -rf /app/target

# Rechte übergeben und erst JETZT als 'app' laufen
RUN chown -R app:app /app
USER app

# Port-Deklaration (Cloud Run setzt PORT env)
EXPOSE 8080

# JVM- und Spring-Defaults
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
# Aktiviert application-docker.yml im Container (für Cloud Run)
ENV SPRING_PROFILES_ACTIVE=docker

# Start-Kommando: bindet auf Cloud-Run-PORT oder 8080
CMD ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
