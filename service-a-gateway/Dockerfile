# Multi-stage build: compile with Maven, run on a slim JRE
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S swiftpay && adduser -S swiftpay -G swiftpay
COPY --from=build /app/target/*.jar app.jar
USER swiftpay
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
