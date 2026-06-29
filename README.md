# AutoTelemetry

A backend platform for ingesting, storing, and analyzing **vehicle telemetry data** (speed, RPM, engine temperature, fuel level, GPS, OBD-II diagnostic codes) over a REST API.

It explores the backend challenges that show up in the connected-car / automotive domain: high-frequency ingestion, time-series storage, trip reconstruction, and OBD-II fault-code decoding.

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
| Build | Maven |
| CI/CD | GitHub Actions |
| Containerization | Docker · docker-compose |

## 🚀 Quick start

Requirements: **Java 21+**, **Docker**.

```bash
# Start PostgreSQL
docker compose up -d

# Run the app (uses the Maven Wrapper, no global Maven needed)
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

```bash
curl http://localhost:8080/api/health
# {"status":"UP","timestamp":"2026-..."}
```

## 🗺️ Domain model

```
User 1───* Vehicle 1───* Trip 1───* TelemetryReading *───* DtcCode
```

- **User** — owns vehicles, authenticates via JWT
- **Vehicle** — a car (VIN, make, model, plate)
- **Trip** — a single drive (start/end time, distance)
- **TelemetryReading** — one sensor sample on a trip (speed, rpm, temp, gps)
- **DtcCode** — OBD-II diagnostic trouble code (e.g. `P0301` = cylinder 1 misfire)

## 📡 API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Service health check |

## 📂 Project structure

<details>
<summary>Click to view the directory tree</summary>

```text
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

</details>
