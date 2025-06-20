# Dockerfile for HR Demo Service
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

# Health check pointing to actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# SendGrid API Key (optional - can be overridden at runtime)
ENV SENDGRID_API_KEY=""
ENV SENDGRID_ENABLED=true
ENV SENDGRID_FROM_EMAIL=hr-agent@somdip.dev
ENV APPLICATION_URL=https://demos.somdip.dev/hr-agent

# Single instance mode indicator
ENV SINGLE_INSTANCE_MODE=true
  
# Set JVM options for container environment with better memory management
# Adjusted for single instance with potentially more memory available
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Fix file upload size limits - Set to 10MB
ENV SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=5MB
ENV SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=5MB

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
