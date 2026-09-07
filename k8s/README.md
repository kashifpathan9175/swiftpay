# Kubernetes manifests

This directory contains the initial deployment scaffolding for the SwiftPay platform.

Recommended layout:
- namespace.yaml
- transaction-gateway/deployment.yaml
- ledger-service/deployment.yaml
- analytics-worker/deployment.yaml
- shared ConfigMap and Secret references where needed

No production secrets or environment-specific values are committed here.
