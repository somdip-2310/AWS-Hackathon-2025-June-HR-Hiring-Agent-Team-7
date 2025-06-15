# Dockerfile for HR Demo Service
# Save this file as "Dockerfile" (no extension) in the root of your HR demo repository

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1000 spring && adduser -u 1000 -G spring -s /bin/sh -D spring

# Set working directory
WORKDIR /app

# Copy JAR file from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership to spring user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose port 8081
EXPOSE 8081

# Health check (adjust the endpoint if your HR demo has a different health check URL)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8081/ || exit 1

# Set JVM options for container environment
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
