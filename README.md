# AutoTelemetry

A backend platform for ingesting, storing, and analyzing **vehicle telemetry data** (speed, RPM, engine temperature, fuel level, GPS, OBD-II diagnostic codes) over a REST API.

It explores the backend challenges that show up in the connected-car / automotive domain: high-frequency ingestion, time-series storage, trip reconstruction, real-time streaming via Kafka, and OBD-II fault-code decoding — surfaced through a live React map and dashboard.

## 🌐 Live Demo

Try the live platform directly in your browser (no local installation required):

- **Live Web Dashboard:** [https://auto-telemetry.vercel.app](https://auto-telemetry.vercel.app)
- **Demo Account Credentials:**
  - **Email:** `demo@autotelemetry.dev`
  - **Password:** `DemoPass123!`
- **Production Architecture:** Deployed on **Vercel** (Frontend), **Render** (Java 21 Spring Boot API), **Neon.tech** (Serverless PostgreSQL), and **Aiven** (Managed Apache Kafka). Features an **Active-on-Demand** reactive simulator that streams live telemetry only when visitors view the map.

## 🧱 Tech stack

| Layer | Technology |
|------|------------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4 |
| Persistence | Spring Data JPA · Hibernate |
| Database | PostgreSQL 16 (Docker) |
| Migrations | Flyway |
| Messaging | Apache Kafka (Confluent 7.6.0) — telemetry event stream |
| Security | Spring Security + JWT |
| Testing | JUnit 5 · Mockito · Testcontainers |
| Build | Maven |
| CI/CD | GitHub Actions |
| Containerization | Docker · docker-compose |
| Frontend | React 18 · Vite · Leaflet · Chart.js |
| Simulator | Python 3 (OBD-II telemetry streamer) |

## 🚀 Quick start

Requirements: **Java 21+**, **Docker**, **Node 18+**, **Python 3**.

```bash
# 1. Start infrastructure (PostgreSQL, Zookeeper, Kafka)
docker compose up -d

# 2. Start the backend
./mvnw spring-boot:run          # API on http://localhost:8080

# 3. Start the frontend (new terminal)
cd frontend && npm install && npm run dev   # UI on http://localhost:5173

# 4. Stream a demo trip (new terminal)
cd simulator && pip install -r requirements.txt
python simulator.py --dtc-trigger P0301
```

### Demo credentials

The simulator auto-registers a demo account on first run:

- **Email:** `demo@autotelemetry.dev`
- **Password:** `DemoPass123!`

Log in to the dashboard, open the **Live** track for the demo vehicle, and watch the marker, trail, and charts update in real time.

```bash
curl http://localhost:8080/api/health
# {"status":"UP","timestamp":"2026-..."}
```

## 🗺️ Domain model

```mermaid
erDiagram
    User ||--o{ Vehicle : owns
    Vehicle ||--o{ Trip : has
    Trip ||--o{ TelemetryReading : contains
    TelemetryReading }o--o{ DtcCode : flags

    User {
        Long id PK
        String email UK
        String passwordHash
        String fullName
        Role role
        Instant createdAt
    }

    Vehicle {
        Long id PK
        Long user_id FK
        String vin UK
        String make
        String model
        Integer year
        String plate
        Instant createdAt
    }

    Trip {
        Long id PK
        Long vehicle_id FK
        Instant startedAt
        Instant endedAt
        Double distanceKm
        Instant createdAt
    }

    TelemetryReading {
        Long id PK
        Long trip_id FK
        Instant recordedAt
        Double speedKmh
        Integer rpm
        Double engineTempC
        Double fuelLevelPct
        Double lat
        Double lng
        Instant createdAt
    }

    DtcCode {
        Long id PK
        String code UK
        String description
        Instant createdAt
    }
```

- **User** (1) ─ owns → (N) **Vehicle** — authenticates via JWT; `USER`/`ADMIN` role
- **Vehicle** (1) ─ has → (N) **Trip** — identified by its VIN
- **Trip** (1) ─ contains → (N) **TelemetryReading** — the high-frequency entity (one sample per second of driving)
- **TelemetryReading** (N) ─ flags → (N) **DtcCode** — many-to-many via `reading_dtc_codes`; a reading can carry several active fault codes. `DtcCode` is read-only reference data (OBD-II standard codes), so the association is only navigable from the reading side.

> Flyway owns the DDL (`V1__init.sql`); Hibernate runs with `ddl-auto=validate`, so the migration is the single source of truth for the database structure.

### Live telemetry flow

Each reading is published to Kafka (`telemetry-events`) by `TelemetryEventProducer`, then consumed and held in a per-vehicle ring buffer (last 50 samples) by `LiveTelemetryService`. `GET /api/vehicles/{id}/live` returns that buffer ordered oldest→newest, with the latest sample (`lat`/`lng`, speed, RPM, fuel, active DTCs) exposed for the live map.

## 📡 API

All endpoints are under the `/api` base path. Mutation endpoints require a `Bearer` JWT from `/api/auth/login`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | Public | Register a user |
| `POST` | `/api/auth/login` | Public | Authenticate, returns a JWT |
| `GET` | `/api/health` | Public | Service health check |
| `POST` | `/api/telemetry` | JWT | Ingest a telemetry reading (also emits a Kafka event) |
| `POST` | `/api/trips` | JWT | Start a trip |
| `POST` | `/api/trips/{id}/end` | JWT | End a trip |
| `GET` | `/api/trips/{id}/readings` | JWT | Readings for a trip |
| `POST` | `/api/vehicles` | JWT | Register a vehicle |
| `GET` | `/api/vehicles` | JWT | List vehicles |
| `GET` | `/api/vehicles/{id}` | JWT | Get a vehicle |
| `DELETE` | `/api/vehicles/{id}` | JWT | Delete a vehicle |
| `GET` | `/api/vehicles/{id}/trips` | JWT | Trips for a vehicle |
| `GET` | `/api/vehicles/{id}/stats` | JWT | Aggregated stats (avg speed, fuel drop, distance) |
| `GET` | `/api/vehicles/{id}/live` | JWT | Live ring buffer (last 50 samples) + latest position |

## 📂 Project structure

<details open>
  <summary><b>AutoTelemetry/</b> <i>(Click to expand/collapse)</i></summary>
  <ul>
    <li><code>docker-compose.yml</code> — PostgreSQL, Zookeeper, Kafka</li>
    <li><code>pom.xml</code> — Maven build (Spring Boot)</li>
    <li><code>ARCHITECTURE.md</code> — C4 model + ADRs (source of truth)</li>
    <li>
      <details>
        <summary><b>docs/diagrams/</b></summary>
        <ul>
          <li><code>...</code> — C4 draw.io + exported PNGs</li>
        </ul>
      </details>
    </li>
    <li>
      <details open>
        <summary><b>src/</b></summary>
        <ul>
          <li>
            <details open>
              <summary><b>main/</b></summary>
              <ul>
                <li>
                  <details open>
                    <summary><b>java/com/gabrielbicu/telemetry/</b></summary>
                    <ul>
                      <li><code>TelemetryApplication.java</code> — Entry point</li>
                      <li><code>config/</code> — SecurityConfig, JwtConfig, Kafka config</li>
                      <li><code>controller/</code> — Auth, Health, Telemetry, Trip, Vehicle</li>
                      <li><code>domain/</code> — JPA entities (User, Vehicle, Trip, ...)</li>
                      <li><code>dto/</code> — Request/response models (incl. LiveTelemetryResponse)</li>
                      <li><code>exception/</code> — GlobalExceptionHandler</li>
                      <li><code>mapper/</code> — Entity &lt;-&gt; DTO mappers</li>
                      <li><code>repository/</code> — Spring Data JPA interfaces</li>
                      <li><code>service/</code> — Business logic, Kafka producer, LiveTelemetryService</li>
                    </ul>
                  </details>
                </li>
                <li>
                  <details>
                    <summary><b>resources/</b></summary>
                    <ul>
                      <li><code>application.yml</code> — Spring Boot Configuration</li>
                      <li><code>db/migration/</code> — Flyway SQL scripts</li>
                    </ul>
                  </details>
                </li>
              </ul>
            </details>
          </li>
          <li>
            <details>
              <summary><b>test/java/...</b></summary>
              <ul>
                <li><code>...</code> — Unit and Integration Tests (Mirrors main/)</li>
              </ul>
            </details>
          </li>
        </ul>
      </details>
    </li>
    <li>
      <details open>
        <summary><b>frontend/</b> — React 18 + Vite dashboard</summary>
        <ul>
          <li>
            <details open>
              <summary><b>src/</b></summary>
              <ul>
                <li><code>api/client.js</code> — Fetch wrapper + JWT interceptor</li>
                <li><code>contexts/AuthContext.jsx</code> — JWT Auth state</li>
                <li><code>pages/</code> — Dashboard, LiveTracking, Login</li>
                <li>
                  <details>
                    <summary><b>components/</b></summary>
                    <ul>
                      <li><code>charts/TelemetryChart.jsx</code> — Chart.js live charts</li>
                      <li><code>layout/Navbar.jsx</code> — Navigation bar</li>
                      <li><code>map/VehicleMap.jsx</code> — Leaflet dark map + trail</li>
                    </ul>
                  </details>
                </li>
              </ul>
            </details>
          </li>
        </ul>
      </details>
    </li>
    <li>
      <details open>
        <summary><b>simulator/</b> — Python OBD-II telemetry streamer</summary>
        <ul>
          <li><code>simulator.py</code> — Main simulator script</li>
          <li><code>route_data.json</code> — Synthetic GPS waypoints</li>
          <li><code>requirements.txt</code> — Python dependencies</li>
          <li><code>README.md</code> — Simulator documentation</li>
        </ul>
      </details>
    </li>
  </ul>
</details>

## 🚗 IoT simulator

`simulator/simulator.py` mimics an OBD-II device: it logs in (auto-registering if needed), drives a loop around `route_data.json`, then POSTs one telemetry reading per second to `/api/telemetry`. It interpolates RPM from speed, ramps engine temperature toward ~90°C, and drains fuel. Pass `--dtc-trigger P0301` to inject a fault mid-trip and exercise the live DTC alert.

See `simulator/README.md` for the full option list (`--rate`, `--duration`, `--speed`, `--vin`, ...).

## 💻 Frontend

The dashboard is a React 18 + Vite single-page app:

- **Login** — JWT auth via `AuthContext`.
- **Dashboard** — per-vehicle stats (avg speed, fuel drop %, distance) from `/stats`.
- **LiveTracking** — polls `/live` every second, renders the vehicle marker and trail on a dark Leaflet map (`VehicleMap`) and time-series speed/fuel/RPM charts (`TelemetryChart`).

Run with `cd frontend && npm run dev` (serves on `http://localhost:5173`; CORS for this origin is pinned in `SecurityConfig`).
