FROM eclipse-temurin:21.0.8_9-jdk-jammy AS verify

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY lease-api/pom.xml lease-api/pom.xml
COPY interest-rate-service/pom.xml interest-rate-service/pom.xml
COPY lease-api/src lease-api/src
COPY interest-rate-service/src interest-rate-service/src

RUN ./mvnw -B verify
