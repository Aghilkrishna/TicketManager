# Multi-stage build for Spring Boot application
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies without building
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
# Install tzdata for timezone configuration
RUN apk add --no-cache tzdata
WORKDIR /app
# Set environment variables for Spring Boot
ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_UPLOAD_DIR=/app/uploads
ENV TZ=Asia/Kolkata

# Create uploads directory and set permissions
RUN mkdir -p /app/uploads && chmod 777 /app/uploads

# Copy the built jar from the build stage
COPY --from=build /app/target/ticket-manager-*.jar app.jar

# Expose the application port
EXPOSE 9090

# Entrypoint to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
