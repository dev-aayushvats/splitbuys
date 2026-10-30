# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# dependencies first, so source-only changes reuse this layer (and the cache mount keeps downloads between builds)
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# tests run in CI, not on every image build
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package && cp target/backend-*.jar /app.jar

# ---------------------------------------------------------------- run
FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin app \
 && mkdir -p /data/backups \
 && chown -R app:app /data
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app

# Sized from the container's memory limit. The default database is a SQLite file on the /data volume;
# set SPRING_PROFILES_ACTIVE=postgres and DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD to use PostgreSQL (Neon).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    DATABASE_URL="jdbc:sqlite:/data/splitbuys.db?foreign_keys=on" \
    BACKUP_DIR="/data/backups"
VOLUME /data
EXPOSE 8080

# liveness only: the database or SMTP being briefly away must not get the container killed
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# extra arguments reach the app, e.g. a restore:  docker compose run --rm backend --app.restore.file=...
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
