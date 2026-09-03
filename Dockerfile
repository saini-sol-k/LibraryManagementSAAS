# Runtime-only image. The jar is produced by `mvn package` in the Jenkins pipeline,
# so the image build stays fast and does not re-resolve the Maven dependency tree.
#
# Build:  docker build --build-arg JAR_FILE=target/library-saas-backend-0.0.1-SNAPSHOT.jar -t library-saas-backend:<tag> .
#
# All configuration arrives through environment variables. No secret is baked in:
# JWT_SECRET and the database password are injected at run time by Kubernetes.

FROM eclipse-temurin:21-jre

# curl is only for the container HEALTHCHECK below; Kubernetes uses its own probes.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Run unprivileged.
RUN groupadd --system --gid 1001 appuser \
 && useradd --system --uid 1001 --gid appuser --create-home appuser

WORKDIR /app

ARG JAR_FILE=target/library-saas-backend-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} /app/app.jar
RUN chown appuser:appuser /app/app.jar

USER appuser

# The application reads server.port from SERVER_PORT, defaulting to 8080.
ENV SERVER_PORT=8080
EXPOSE 8080

# Container-level check for plain `docker run`. Kubernetes overrides this with
# readiness/liveness probes against the same endpoint.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
