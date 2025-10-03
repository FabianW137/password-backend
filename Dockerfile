FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package
RUN bash -lc 'set -e; jar=$(ls target/*-SNAPSHOT.jar 2>/dev/null || ls target/*.jar | head -n1); cp "$jar" target/app.jar'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/app.jar /app/app.jar
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
