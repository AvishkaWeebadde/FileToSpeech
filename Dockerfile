# multi stage docker build
FROM maven:3.8.7-eclipse-temurin-17 AS build

# Set working directory for the application
WORKDIR /pdf-reader

# Copy the pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Start new stage from official JDK image
FROM openjdk:17-jdk-slim

# SET working directory for running the app
WORKDIR /pdf-reader

# Copy the JAR file from the build stage
COPY --from=build /pdf-reader/target/aw.reader-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the Spring boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
