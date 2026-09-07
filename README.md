# SwiftPay – Real-Time Payment Ledger

This repository contains the initial production-oriented project scaffold for the SwiftPay platform.

## Modules

- transaction-gateway — ingress API layer and external request handling
- ledger-service — authoritative ledger and transactional accounting service
- analytics-worker — event-driven analytics and reporting worker

## Stack

- Java 21
- Spring Boot 3.3.x
- Maven
- PostgreSQL
- Flyway
- Apache Kafka
- Redis
- OpenAPI/Swagger
- JUnit 5
- Mockito
- Testcontainers
- Docker
- Kubernetes
- GitHub Actions

## Project structure

```text
.
├── analytics-worker/
├── docs/
├── k8s/
├── ledger-service/
├── performance/
├── transaction-gateway/
├── .github/
├── pom.xml
├── docker-compose.yml
├── README.md
└── .gitignore
```

## Scaffold state

The current stage focuses on project structure, dependency hygiene, Spring Boot application initialization, and compile-ready service skeletons.

Business functionality, Kafka consumers, payment processing, Redis logic, and database schema implementation are intentionally deferred until the foundational architecture is validated.

## Build

```bash
mvn test
```

or, per service:

```bash
mvn -f transaction-gateway/pom.xml test
mvn -f ledger-service/pom.xml test
mvn -f analytics-worker/pom.xml test
```
