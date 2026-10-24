# API image, multi-stage (Learning Notes 54.3). Ubuntu ("noble"), not Alpine, since the ID checks (Learning Notes 82):
# OpenCV's and Tesseract's compiled libraries are built for glibc, which Alpine doesn't have.

# Stage 1: build the jar with the full JDK. "package" also downloads the model files and checks their SHA-256 (pom.xml).
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Dependencies first: this layer is reused until pom.xml changes
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI against real Postgres and Redis, not inside the image build
RUN ./mvnw -B -q package -DskipTests

# Stage 2: only the Java runtime, the jar and the models; no compiler, no source code
FROM eclipse-temurin:21-jre-noble
# libtesseract5: the OCR engine Tess4J calls. wget: the healthcheck in docker-compose.prod.yml.
# The links: Tess4J asks for "libleptonica.so" and "libtesseract.so"; Ubuntu names them liblept.so.5 and libtesseract.so.5
RUN apt-get update && apt-get install -y --no-install-recommends libtesseract5 wget && rm -rf /var/lib/apt/lists/* \
    && cd /usr/lib/$(uname -m)-linux-gnu && ln -s liblept.so.5 libleptonica.so && ln -s libtesseract.so.5 libtesseract.so \
    && groupadd --system app && useradd --system --gid app app
WORKDIR /app
# shortcut: the jar carries OpenCV's library for every platform (image 981 MB), strip the others if pulls get slow
COPY --from=build /src/target/*.jar app.jar
COPY --from=build /src/target/models models
USER app
EXPOSE 8080
ENV MODELS_DIR=/app/models
# Use at most half of the container's memory limit (the server has only 2 GB for everything)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"
ENTRYPOINT ["java", "-jar", "app.jar"]
