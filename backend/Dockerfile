FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S balancetrail && adduser -S balancetrail -G balancetrail
WORKDIR /app
COPY --from=build /workspace/target/balancetrail-backend-0.1.0.jar app.jar
RUN mkdir -p /app/data/uploads && chown -R balancetrail:balancetrail /app
USER balancetrail
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
