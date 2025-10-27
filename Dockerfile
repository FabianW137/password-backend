########## BUILD ##########
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

########## RUNTIME ##########
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Nutzer anlegen
RUN addgroup --system app && adduser --system --ingroup app app

# Artefakt kopieren und umbenennen (als root!)
COPY --from=build /workspace/target /app/target
RUN set -eu; JAR="$(ls -1 /app/target/*.jar | head -n1)"; \
    mv "$JAR" /app/app.jar; rm -rf /app/target

# Besitz an app übergeben, dann erst Nutzer wechseln
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
CMD ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
