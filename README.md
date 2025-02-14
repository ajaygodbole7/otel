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

## Setup & Installation

### 1. Clone the Repository
```bash
git clone <repository-url>
cd otel-demo
```

### 2. Download OpenTelemetry Java Agent
```bash
curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar > opentelemetry-javaagent.jar
```

### 3. Start Infrastructure Services
```bash
docker-compose up -d
```

This will start:
- PostgreSQL database
- Kafka broker
- OpenTelemetry Collector
- Grafana
- Prometheus
- Tempo
- Loki

### 4. Run the Application
```bash
JAVA_TOOL_OPTIONS="-javaagent:./opentelemetry-javaagent.jar" \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Testing the Application

### Testing Customer API Endpoints

1. Create Customer (POST):
```bash
curl -X POST http://localhost:8080/api/v1/customers \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{
  "type": "INDIVIDUAL",
  "firstName": "John",
  "lastName": "Doe",
  "emails": [
    {
      "primary": true,
      "email": "john.doe@goaway.com",
      "type": "PERSONAL"
    }
  ],
  "phones": [
    {
      "type": "MOBILE",
      "countryCode": "+1",
      "number": "5555555555"
    }
  ],
  "addresses": [
    {
      "type": "HOME",
      "line1": "123 Main St",
      "city": "Springfield",
      "state": "IL",
      "postalCode": "62701",
      "country": "USA"
    }
  ]
}'
```
2. Get Customer by ID (GET):
```bash
curl http://localhost:8080/api/v1/customers/[ID] \
-H "Accept: application/json"
```

3. Get All Customers (GET):
```bash
curl http://localhost:8080/api/v1/customers \
-H "Accept: application/json"
```

4. Update Customer (PUT):
```bash
curl -X PUT http://localhost:8080/api/v1/customers/[ID] \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{
  "id": [ID],
  "type": "INDIVIDUAL",
  "firstName": "John",
  "lastName": "Smith",
  "emails": [
    {
      "primary": true,
      "email": "john.smith@example.com",
      "type": "PERSONAL"
    }
  ],
  "phones": [
    {
      "type": "MOBILE",
      "countryCode": "+1",
      "number": "5555555555"
    }
  ],
  "addresses": [
    {
      "type": "HOME",
      "line1": "456 Oak Avenue",
      "city": "Springfield",
      "state": "IL",
      "postalCode": "62701",
      "country": "USA"
    }
  ],
  "createdAt": "[original createdAt timestamp]"
}'
```

5. Delete Customer (DELETE):
```bash
curl -X DELETE http://localhost:8080/api/v1/customers/[ID] \
-H "Accept: application/json"
```

Add `-v` to any command to see detailed request/response information:
```bash
curl -v http://localhost:8080/api/v1/customers \
-H "Accept: application/json"
```

Note: Replace [ID] with an actual customer ID from your system. You'll get this ID from the create customer response.
## Observability Components

### Grafana
- URL: http://localhost:3000
- Default Access: Anonymous auth enabled with Admin role


## Configuration Files

### application-local.yml
Spring Boot application and OpenTelemetry configuration:
```yaml
otel:
  service:
    name: otel-demo
  exporter:
    otlp:
      endpoint: http://localhost:4318
```

### otel-collector.yml
OpenTelemetry Collector configuration defining:
- Receivers
- Processors
- Exporters
- Service pipelines

### datasources.yaml
Grafana data sources configuration:
- Prometheus for metrics
- Tempo for traces
- Loki for logs

## Architecture

```
Spring Boot App (OTLP) -> OpenTelemetry Collector -> Prometheus (metrics)
                                                 -> Tempo (traces)
                                                 -> Loki (logs)
```
