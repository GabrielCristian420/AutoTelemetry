# AutoTelemetry 🚗

A backend platform for ingesting, storing, and analyzing **vehicle telemetry data** (speed, RPM, engine temperature, fuel level, GPS, OBD-II diagnostic codes) over a REST API.

Built to explore the kind of backend challenges that show up in the **automotive / connected-car** domain — high-frequency ingestion, time-series data, trip reconstruction, and fault-code decoding.

---

## 🧱 Tech stack

| Layer | Technology |
|------|------------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3 |
| Persistence | Spring Data JPA · Hibernate |
| Database | PostgreSQL 16 (Docker) |
| Migrations | Flyway |
| Security | Spring Security + JWT |
| Testing | JUnit 5 · Mockito · Testcontainers |
| Build | Maven (with Maven Wrapper) |
| CI/CD | GitHub Actions |
| Containerization | Docker · docker-compose |

---

## 🗺️ Domain model (high level)

```
User 1───* Vehicle 1───* Trip 1───* TelemetryReading *───* DtcCode
```

- **User** — owns vehicles, authenticates via JWT
- **Vehicle** — a car (VIN, make, model, plate)
- **Trip** — a single drive (start/end time, distance)
- **TelemetryReading** — one sensor sample on a trip (speed, rpm, temp, gps…)
- **DtcCode** — OBD-II diagnostic trouble code (e.g. `P0301` = cylinder 1 misfire)

---

## 🚀 Quick start

> Requirements: **Java 21+**, **Docker** (with Docker Compose plugin).

```bash
# 1. Start PostgreSQL in a container
docker compose up -d

# 2. Run the app (uses the Maven Wrapper, no global Maven needed)
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

Health check:
```bash
curl http://localhost:8080/health
# -> { "status": "UP" }
```

---

## 📦 API overview (work in progress)

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/health` | Service health check |
| _more endpoints coming soon_ | | |

---

## 🛣️ Roadmap

- [x] Project bootstrap (Spring Boot + Maven + CI)
- [ ] JPA domain model + Flyway migrations
- [ ] Telemetry ingestion endpoint
- [ ] Vehicle / Trip CRUD
- [ ] JWT authentication (Spring Security)
- [ ] JUnit + Mockito tests (> 70% coverage)
- [ ] Stats / aggregation endpoint
- [ ] _(stretch)_ Async ingestion via Kafka
- [ ] _(stretch)_ OBD-II DTC decoder
- [ ] _(stretch)_ Live deploy + dashboard

---

## 📂 Project structure

```
src/
├── main/
│   ├── java/com/gabrielbicu/telemetry/
│   │   ├── TelemetryApplication.java
│   │   ├── config/        # SecurityConfig, JwtConfig, ...
│   │   ├── domain/        # JPA entities
│   │   ├── repository/    # Spring Data JPA interfaces
│   │   ├── service/       # business logic
│   │   ├── controller/    # REST controllers
│   │   ├── dto/           # request/response models
│   │   └── exception/     # GlobalExceptionHandler, ...
│   └── resources/
│       ├── application.yml
│       └── db/migration/  # Flyway SQL scripts
└── test/java/...           # mirrors main/
```

---

_Status: 🔨 in development — this is a learning/portfolio project._
