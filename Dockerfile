# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1) Dependencies cachen über Layers (ohne BuildKit)
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# 2) Quellcode kopieren und bauen
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/app.jar /app/app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
CMD ["java","-jar","/app/app.jar"]
