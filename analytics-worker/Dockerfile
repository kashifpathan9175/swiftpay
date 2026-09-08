FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl analytics-worker -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --uid 1001 swiftpay
COPY --from=build /workspace/analytics-worker/target/analytics-worker-*.jar /app/app.jar
USER swiftpay
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
