# syntax=docker/dockerfile:1

########## BUILD ##########
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Leverage layer caching for dependencies
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# Build application
COPY src ./src
RUN mvn -B -DskipTests clean package

########## RUNTIME ##########
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create non-root user (Cloud Run best practice)
RUN addgroup --system app && adduser --system --ingroup app app

# Copy the built JAR (rename to a fixed name)
COPY --from=build /workspace/target /app/target
RUN set -eu; JAR="$(ls -1 /app/target/*.jar | head -n1)";     mv "$JAR" /app/app.jar; rm -rf /app/target

# Default profile and JVM opts; you can override on deploy
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

# Run as non-root
USER app

# Cloud Run provides $PORT and expects the service to listen on 0.0.0.0:$PORT
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
