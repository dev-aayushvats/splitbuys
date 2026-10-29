# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home app && mkdir /data && chown app /data
USER app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod DATABASE_URL="jdbc:sqlite:/data/splitbuys.db?foreign_keys=on"
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
