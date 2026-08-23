# Etap 1: Budowanie aplikacji w środowisku Maven z JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Etap 2: Lekki obraz produkcyjny JRE (bez zbędnych narzędzi SDK)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Uruchamianie aplikacji jako dedykowany użytkownik nieuprzywilejowany (Security Best Practice)
RUN addgroup -S fintech && adduser -S ledger -G fintech
USER ledger

COPY --from=build /app/target/ledger-transfer-engine-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]