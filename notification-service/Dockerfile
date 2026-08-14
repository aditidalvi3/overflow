# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
COPY --from=build /build/target/notificationservice.jar app.jar
USER orderflow
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
