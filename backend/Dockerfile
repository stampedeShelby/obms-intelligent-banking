# Multi-stage build: the JDK and Maven cache stay out of the runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved in their own layer, so a source-only change does
# not re-download the world.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
COPY spotbugs-exclude.xml .
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Runs as a non-root user: a container process that does not need root should
# not have it.
RUN addgroup -S obms && adduser -S obms -G obms
COPY --from=build /build/target/*.jar app.jar
RUN chown -R obms:obms /app
USER obms

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
