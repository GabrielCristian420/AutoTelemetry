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
┌──────────┐ 1     * ┌──────────┐ 1     * ┌──────────┐ 1     * ┌─────────────────────┐ *   * ┌──────────┐
│   User   │─────────│ Vehicle  │─────────│   Trip   │─────────│ TelemetryReading    │───────│ DtcCode  │
│ owns →   │         │ VIN,make │         │ start/end│         │ speed,rpm,temp,gps  │  M:N  │ OBD-II   │
└──────────┘         └──────────┘         └──────────┘         └─────────────────────┘       └──────────┘
   │                    │ (user_id FK)        │ (vehicle_id FK)         │ (trip_id FK)             │
   │                                         │                         └────── reading_dtc_codes ─┘
.email,.role                                                                                       (join table)
```

- **User** (1) ─ owns → (N) **Vehicle** — authenticates via JWT; `USER`/`ADMIN` role
- **Vehicle** (1) ─ has → (N) **Trip** — identified by its VIN
- **Trip** (1) ─ contains → (N) **TelemetryReading** — the high-frequency entity (one sample per second of driving)
- **TelemetryReading** (N) ─ flags → (N) **DtcCode** — many-to-many via `reading_dtc_codes`; a reading can carry several active fault codes. `DtcCode` is read-only reference data (OBD-II standard codes), so the association is only navigable from the reading side.

> Flyway owns the DDL (`V1__init.sql`); Hibernate runs with `ddl-auto=validate`, so the migration is the single source of truth for the database structure.

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
