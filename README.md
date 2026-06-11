<p align="center">
  <h1 align="center">Reference Data Aggregation Service</h1>
  <p align="center">A production-grade REST API that consolidates country, currency, language, and geographical reference data from a legacy SOAP provider into a single, high-performance JSON interface.</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Redis-7.2-DC382D?style=flat-square&logo=redis&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Kubernetes-Ready-326CE5?style=flat-square&logo=kubernetes&logoColor=white" />
</p>

---

## Overview

Multiple client channels — mobile applications, web applications, partner APIs, and internal operations portals — previously consumed a third-party SOAP service directly, resulting in duplicated integration logic, inconsistent responses, no caching strategy, and exposed credentials across systems.

RDAS was built to address these challenges. It acts as an **Anti-Corruption Layer** between consumers and the upstream SOAP provider, exposing a clean REST/JSON interface while handling all integration complexity internally. All reference data is eagerly cached at startup and refreshed on a scheduled interval, meaning zero SOAP calls occur during normal consumer request handling.

---

## Key Features

- **Unified REST API** — replaces direct SOAP consumption across all channels with a single versioned endpoint
- **Redis caching** — reference data loaded into Redis at startup; all consumer requests served from cache with sub-5ms latency
- **Dynamic filtering** — search countries by name, continent, currency, and language with composable query parameters
- **Pagination & sorting** — all list endpoints support page/size/sort with configurable defaults and a hard max of 100 items per page
- **Circuit breaker** — Resilience4j circuit breaker prevents SOAP outages from cascading into consumer-facing failures
- **Retry with backoff** — transient SOAP failures are retried up to 3 times with exponential backoff before the circuit opens
- **Structured error responses** — consistent RFC 7807-aligned error shape with trace IDs for log correlation
- **Input validation** — Jakarta Bean Validation enforced at the controller boundary; sanitised errors returned to callers
- **Observability** — Micrometer metrics exposed via Actuator for Prometheus scraping; circuit breaker state visible via health endpoint

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                     Consumers                        │
│   Mobile App  │  Web App  │  Partner API  │  Ops     │
└──────────────────────┬───────────────────────────────┘
                       │  REST / JSON
         ┌─────────────▼──────────────┐
         │             RDAS           │
         │   ┌─────────────────────┐  │
         │   │   API Layer         │  │
         │   │   Controllers       │  │
         │   └──────────┬──────────┘  │
         │   ┌──────────▼──────────┐  │
         │   │   Service Layer     │  │
         │   │   Filter/Sort/Page  │  │
         │   └──────────┬──────────┘  │
         │         ┌────┴─────┐       │
         │      Cache      Circuit    │
         │      Redis      Breaker    │
         │         └────┬─────┘       │
         │   ┌──────────▼──────────┐  │
         │   │   Integration Layer │  │
         │   │   SOAP Client       │  │
         └───┴─────────────────────┴──┘
                        │  SOAP / XML  (cache miss only)
             ┌──────────▼──────────┐
             │  CountryInfo SOAP   │
             │  webservices.       │
             │  oorsprong.org      │
             └─────────────────────┘
```

See [`docs/architecture.png`](docs/architecture_diagram.pdf) for the full annotated diagram.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 17 |
| Framework | Spring Boot 3.2 |
| Caching | Redis 7.2 via Spring Cache + Lettuce |
| Resilience | Resilience4j (Circuit Breaker), Spring Retry |
| API Docs | Springdoc OpenAPI 2.x (Swagger UI) |
| Observability | Micrometer, Spring Actuator, Prometheus |
| Containerisation | Docker, Docker Compose |
| Orchestration | Kubernetes (manifests in `k8s/`) |
| Build | Maven 3.9 |

---

## Getting Started

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| Docker | 24+ |
| Docker Compose | 2.x |

### Run with Docker Compose (recommended)

The simplest way to run the full stack locally. Docker Compose starts both RDAS and Redis with a single command.

```bash
git clone https://github.com/YOUR_USERNAME/rdas.git
cd rdas

docker compose up --build
```

| Service | URL |
|---------|-----|
| API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health check | `http://localhost:8080/actuator/health` |

On startup, RDAS performs an eager cache warm from the SOAP provider. Allow approximately 30–60 seconds before the first request.

### Run with Maven

If you have Redis running separately:

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7.2-alpine

# Build and run
mvn clean package -DskipTests
java -jar target/rdas-1.0.0.jar
```

---

## Configuration

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Active Spring profile |

All cache TTLs and resilience thresholds are configurable in `src/main/resources/application.yml`.

---

## API Reference

Full interactive documentation: `http://localhost:8080/swagger-ui.html`

Static offline documentation: [`docs/api-documentation.html`](docs/api-documentation.html)

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/countries` | Search countries — filterable, paginated, sortable |
| `GET` | `/api/v1/countries/{isoCode}` | Retrieve a country by ISO 3166-1 alpha-2 code |
| `GET` | `/api/v1/countries/{isoCode}/currency` | Retrieve currency information for a country |
| `GET` | `/api/v1/currencies` | List all currencies |
| `GET` | `/api/v1/currencies/{code}/countries` | List countries using a given currency |
| `GET` | `/api/v1/continents` | List all continents |
| `GET` | `/api/v1/languages` | List all languages |
| `POST` | `/api/v1/admin/cache/refresh` | Manually trigger a full cache refresh |

### Search Countries

```
GET /api/v1/countries?name=ken&continent=AF&currency=KES&page=0&size=20&sort=name,asc
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | `string` | Partial, case-insensitive country name match |
| `continent` | `string` | 2-letter continent code — `AF`, `EU`, `NA`, `AS`, `OC`, `SA` |
| `currency` | `string` | ISO 4217 currency code — e.g. `USD`, `KES`, `EUR` |
| `language` | `string` | ISO 639 language code — e.g. `EN`, `FR`, `SW` |
| `page` | `integer` | Zero-indexed page number (default: `0`) |
| `size` | `integer` | Results per page, max `100` (default: `20`) |
| `sort` | `string` | Sort expression — e.g. `name,asc` or `isoCode,desc` |

---

## Running Tests

```bash
mvn test
```

Tests cover service-layer filtering, pagination, sorting logic, and controller-layer input validation using MockMvc.

---

## Deployment

Kubernetes manifests are provided in the `k8s/` directory covering namespace, ConfigMap, Secret, Redis deployment, application deployment with rolling update strategy, and a HorizontalPodAutoscaler.

```
k8s/
├── namespace.yaml
├── configmap.yaml
├── secret.yaml
├── redis-deployment.yaml
├── deployment.yaml
└── hpa.yaml
```

See [**Kubernetes Deployment Guide**](docs/kubernetes-deploy-guide.md) for step-by-step instructions.

See [**Kubernetes Troubleshooting Guide**](docs/kubernetes-troubleshooting.md) for the operations runbook.

---

## Design Decisions

**In-memory filtering over a relational database**
Reference data is read-heavy and changes infrequently — at most a few times per year. Materialising the full dataset into Redis at startup and performing filtering in the service layer eliminates database infrastructure, reduces per-request latency to under 5ms, and removes a failure domain from the stack.

**Redis over Caffeine for caching**
The service is designed to run as multiple replicas behind a load balancer. A Caffeine (in-process) cache would require each replica to independently warm its cache from SOAP on startup, multiplying SOAP traffic by the replica count and creating inconsistency windows. Redis provides a single shared cache — one warm cycle covers all replicas.

**Circuit breaker on the SOAP integration**
The upstream SOAP provider is an external dependency outside the service's SLA boundary. Without a circuit breaker, SOAP timeouts propagate directly to consumer requests. The Resilience4j circuit breaker trips after a configurable failure threshold, failing fast and serving from cache rather than queueing further failing calls.

**24-hour cache TTL with 12-hour scheduled refresh**
Country, currency, and language data has an extremely low mutation rate. A 24-hour TTL guarantees that any given entry survives at least one full scheduled refresh cycle before expiry, avoiding cold-start conditions even if the SOAP provider is intermittently unavailable.
