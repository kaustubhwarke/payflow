# ---- Build stage ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage -------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Run as an unprivileged user (security hardening).
RUN useradd --system --uid 10001 payflow
COPY --from=build /build/target/payflow-api.jar app.jar
USER payflow
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
