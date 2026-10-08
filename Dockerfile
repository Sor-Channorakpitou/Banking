# Multi-stage build: a full JDK + Maven to compile, a small JRE-only image to run.
# The final image has no compiler, no sources and no build cache.

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes, so editing Java
# code doesn't re-download the internet on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/core-banking-*.jar extract --layers --launcher --destination extracted

# ---- run ----
FROM eclipse-temurin:21-jre
# Never run as root inside the container.
RUN groupadd --system bank && useradd --system --gid bank --uid 10001 bank
WORKDIR /app

# Spring Boot "layers": rarely changing dependencies first, our own code last,
# so pushing a new version only uploads the small final layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER bank
EXPOSE 8080
# Size the heap from the container's memory limit, not the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
