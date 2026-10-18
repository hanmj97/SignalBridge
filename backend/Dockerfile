# Build with Java 21 and Maven inside Docker: no host JDK/Maven installation needed.
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Unit tests must pass before the image is produced.
RUN mvn -B -q clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --uid 10001 --create-home signalbridge
COPY --from=builder /workspace/target/signalbridge-api-0.2.0.jar /app/app.jar
USER signalbridge
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
