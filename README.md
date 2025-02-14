# OpenTelemetry Demo with Spring Boot

## Introduction
Demonstration application showcasing OpenTelemetry integration with Spring Boot for observability, including distributed tracing, metrics collection, and logging. Features REST APIs, PostgreSQL database integration, and Kafka message publishing.

## Technology Stack
- Java 21
- Spring Boot 3.4.2
- OpenTelemetry Java Agent
- PostgreSQL
- Apache Kafka (KRaft mode)
- CloudEvents
- OpenTelmetry Observability Stack
  - Grafana
  - Prometheus
  - Tempo
  - Loki

## Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.x
- cURL (for testing)

## Summary

This app exposes REST API Endpoints to manage a Customer Resource.

The API creates CRUD operations that are saved in Postgres and a corresponding CloudEvent is published to Kafka.

The OpenTelemetry Java Agent instruments the Spring Boot app and sends logs, traces and metrics to the OpenTelemetry Observability Stack running in Grafana.

## API Documentation
### Endpoints
- `POST /api/v1/customers` - Create customer
- `PUT /api/v1/customers/{id}` - Update customer
- `GET /api/v1/customers/{id}` - Get customer
- `DELETE /api/v1/customers/{id}` - Delete customer
