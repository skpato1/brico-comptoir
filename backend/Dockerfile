# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
ENV MAVEN_OPTS="-Xmx512m"
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S brico && adduser -S brico -G brico
WORKDIR /app
COPY --from=build /workspace/target/bricocomptoir-0.0.1-SNAPSHOT.jar app.jar
USER brico
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
