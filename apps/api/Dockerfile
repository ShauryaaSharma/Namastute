# API image, multi-stage (Learning Notes 54.3)

# Stage 1: build the jar with the full JDK
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Dependencies first: this layer is reused until pom.xml changes
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI against real Postgres and Redis, not inside the image build
RUN ./mvnw -B -q package -DskipTests

# Stage 2: only the Java runtime and the jar; no compiler, no source code
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER app
EXPOSE 8080
# Use at most half of the container's memory limit (the server has only 2 GB for everything)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"
ENTRYPOINT ["java", "-jar", "app.jar"]
