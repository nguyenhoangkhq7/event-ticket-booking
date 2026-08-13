# Stage 1: Build the application using Maven wrapper
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Grant execution permission to the maven wrapper
RUN chmod +x ./mvnw

# Download dependencies (this step will be cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy the project source code
COPY src src

# Package the application (skip tests to speed up the build)
RUN ./mvnw package -DskipTests

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
