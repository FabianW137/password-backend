# syntax=docker/dockerfile:1

########## BUILD ##########
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 1) Dependencies cachen über Layer (ohne BuildKit)
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# 2) Quellcode & Build
COPY src ./src
RUN mvn -B -DskipTests package

########## RUNTIME ##########
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# App-User
RUN addgroup --system app && adduser --system --ingroup app app

# JAR kopieren (genauer Glob ggf. anpassen)
COPY --chown=app:app --from=build /workspace/target/*-SNAPSHOT.jar /app/app.jar

USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

# Cloud Run gibt $PORT vor
CMD ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
