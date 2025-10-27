########## BUILD ##########
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# nur pom.xml zuerst -> Layer-Caching für Dependencies
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# danach der Quellcode
COPY src ./src
# Paket bauen (Spring Boot repackage)
RUN mvn -B -DskipTests package

########## RUNTIME ##########
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Nicht-root Nutzer
RUN addgroup --system app && adduser --system --ingroup app app
USER app

# Artefakt übernehmen (jar unbekannt → generisch)
COPY --from=build /workspace/target /app/target
RUN set -eu; JAR="$(ls -1 /app/target/*.jar | head -n1)"; mv "$JAR" /app/app.jar; rm -rf /app/target

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

# Render setzt $PORT → an Spring durchreichen
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]