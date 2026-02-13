# Stage 1: Build stage
# Copy package files
# Install dependencies
# Stage 2: Runtime stage
# Labels for documentation
# Set working directory
# Copy dependencies from builder stage
# Copy application code
# Health check
# Expose port
# Set environment variables
# Run application
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 3002

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:3002/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
