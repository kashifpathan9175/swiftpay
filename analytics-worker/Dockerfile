FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/analytics-worker-*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
