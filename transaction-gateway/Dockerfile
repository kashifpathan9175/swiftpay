FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd --system --create-home --uid 1001 swiftpay

COPY transaction-gateway/target/transaction-gateway-0.1.0-SNAPSHOT.jar app.jar

RUN chown swiftpay:swiftpay app.jar

USER swiftpay

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]