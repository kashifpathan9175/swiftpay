FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl transaction-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --uid 1001 swiftpay
COPY --from=build /workspace/transaction-gateway/target/transaction-gateway-*.jar /app/app.jar
USER swiftpay
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
