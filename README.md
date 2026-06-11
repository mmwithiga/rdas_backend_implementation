# Reference Data Aggregation Service (RDAS)

> Single source of truth for country, currency, language and geographical reference data.

RDAS wraps the [CountryInfo SOAP Service](http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL) behind a clean REST/JSON API — eliminating direct SOAP calls from consumers, centralising caching, and providing resilience when the upstream service is unavailable.

---

## Architecture

```
┌──────────────────────────────────────────────┐
│                 Consumers                    │
│  Mobile | Web | Partner APIs | Ops Portal    │
└─────────────────┬────────────────────────────┘
                  │  REST/JSON
┌─────────────────▼────────────────────────────┐
│            RDAS  (Spring Boot 3)             │
│                                              │
│  Controller → Service → SOAP Client         │
│                   │                          │
│              Redis Cache                     │
│          (24h TTL, warm at startup)          │
└─────────────────────────────┬────────────────┘
                               │  SOAP (on cache miss only)
                        CountryInfo SOAP Service
```

See [`docs/architecture.png`](docs/architecture.png) for the full diagram.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| Docker | 24+ |
| Docker Compose | 2.x |
| kubectl | 1.28+ (for K8s deployment) |

---

## Run Locally (Docker Compose — recommended)

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/rdas.git
cd rdas

# Build and start RDAS + Redis
docker compose up --build

# RDAS is now running at http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Actuator:   http://localhost:8080/actuator/health
```

On startup, RDAS automatically warms the cache from the SOAP service (~30-60 seconds).

---

## Run Locally (Maven — requires Redis running separately)

```bash
# Start Redis via Docker
docker run -d -p 6379:6379 redis:7.2-alpine

# Build
mvn clean package -DskipTests

# Run
java -jar target/rdas-1.0.0.jar
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Active profile |

---

## API Endpoints

Full interactive documentation at: `http://localhost:8080/swagger-ui.html`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/countries` | Search countries (filterable, paginated, sortable) |
| `GET` | `/api/v1/countries/{isoCode}` | Get a single country by ISO code |
| `GET` | `/api/v1/countries/{isoCode}/currency` | Get currency info for a country |
| `GET` | `/api/v1/currencies` | List all currencies |
| `GET` | `/api/v1/currencies/{code}/countries` | Countries using a specific currency |
| `GET` | `/api/v1/continents` | List all continents |
| `GET` | `/api/v1/languages` | List all languages |
| `POST` | `/api/v1/admin/cache/refresh` | Force cache refresh (ops) |

### Search Countries — Query Parameters

```
GET /api/v1/countries?name=ken&continent=AF&currency=KES&language=EN&page=0&size=20&sort=name,asc
```

| Param | Type | Description |
|-------|------|-------------|
| `name` | string | Partial match on country name |
| `continent` | string | 2-letter continent code (e.g. `AF`, `EU`) |
| `currency` | string | 3-letter ISO currency code (e.g. `USD`, `KES`) |
| `language` | string | 2-3 letter ISO language code (e.g. `EN`, `FR`) |
| `page` | int | Zero-indexed page number (default: 0) |
| `size` | int | Page size, max 100 (default: 20) |
| `sort` | string | Sort field and direction (e.g. `name,asc`) |

---

## Running Tests

```bash
mvn test
```

---

## Design Decisions

### Why in-memory filtering instead of a database?
Reference data (countries, currencies, languages) is read-heavy and rarely changes. Loading it into Redis at startup and filtering in-memory eliminates DB infrastructure, reduces latency to <5ms per request, and keeps the architecture simple.

### Why Redis over Caffeine?
RDAS is designed to run as multiple replicas behind a load balancer. Caffeine is per-JVM — each replica would cache independently and each would hit SOAP on startup. Redis is shared across all replicas: one warm = all warm.

### Why circuit breaker?
The SOAP service is an external dependency outside our SLA. The circuit breaker prevents a SOAP outage from cascading into RDAS timeouts — it fails fast after the threshold and serves from cache.

### Cache TTL rationale
Country/currency/language data changes at most a few times a year. A 24h TTL means maximum 1 day of staleness, which is acceptable for reference data. The scheduled 12h refresh further reduces actual staleness.

---

## Deployment

See [`docs/kubernetes-deploy-guide.md`](docs/kubernetes-deploy-guide.md) for full K8s deployment instructions.

See [`docs/kubernetes-troubleshooting.md`](docs/kubernetes-troubleshooting.md) for the operations runbook.
