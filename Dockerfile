# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY micronaut-cli.yml ./

# Download dependencies (layer caching)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build shadow JAR and rename it to app.jar
RUN ./gradlew shadowJar --no-daemon && \
    cp build/libs/*-all.jar build/libs/app.jar

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the renamed JAR from build stage
COPY --from=build /app/build/libs/app.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
