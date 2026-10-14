FROM eclipse-temurin:21.0.8_9-jdk-jammy@sha256:adb9b2d15adf1833d9dae0bdc1cff61ef5a804dc58dfbfb34269f32432b2e5dc AS verify

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY lease-api/pom.xml lease-api/pom.xml
COPY interest-rate-service/pom.xml interest-rate-service/pom.xml
COPY lease-api/src lease-api/src
COPY interest-rate-service/src interest-rate-service/src

RUN ./mvnw -B verify

FROM eclipse-temurin:21.0.8_9-jre-jammy@sha256:db1689535962d757a5adabf57387584ed543d38c0b9d1fe870123ea362ad73b0 AS runtime-base

WORKDIR /app

RUN groupadd --system application \
    && useradd --system --gid application --home-dir /app --no-create-home application \
    && mkdir /data \
    && chown application:application /data

USER application:application

FROM runtime-base AS lease-api

COPY --from=verify --chown=application:application /workspace/lease-api/target/lease-api-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS interest-rate-service

COPY --from=verify --chown=application:application /workspace/interest-rate-service/target/interest-rate-service-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
