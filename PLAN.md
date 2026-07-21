# AutoTelemetry — Implementation Plan

> **This plan delivers the FULL architecture described in `ARCHITECTURE.md`**
> (C4 containers, ADRs, live-map dashboard, Python IoT simulator, Kafka CQRS).
>
> The earlier `full_context.md` week-by-week was **backend-only** and never
> scheduled the frontend, simulator, or Kafka. This document supersedes it for
> execution purposes and reconciles the two by adding the missing phases
> (Phase 4 Kafka, Phase 5 Simulator, Phase 6 Dashboard) while keeping the
> already-completed backend work (Phases 0–3).

---

## How this plan maps to `ARCHITECTURE.md`

| C4 Container (§2.2) | Delivered by | ADR |
|---------------------|--------------|-----|
| **API Backend** (Java 21, Spring Boot) | Phases 0–4 | ADR-1 JWT, ADR-2 Flyway, ADR-3 Kafka, ADR-4 CQRS |
| **PostgreSQL 16 + Flyway** | Phase 0 | ADR-2 |
| **Apache Kafka** (Confluent 7.6.0) | Phase 4 | ADR-3 |
| **IoT Simulator** (Python 3) | Phase 5 | — (demo data source) |
| **Web Dashboard** (React, Leaflet.js, Chart.js) | Phase 6 | — (CQRS live read model consumer) |

API contract followed exactly: auth (`/api/auth/*`), vehicles, trips,
`POST /api/telemetry`, `GET /api/vehicles/{id}/live` (§4.5), stats (§4.6).

---

## Phase 0 — Foundation ✅ DONE (Weeks 1–2)
- Git, Spring Boot scaffold, `pom.xml`, Docker Compose (PostgreSQL 16)
- `HealthController` + test, GitHub Actions CI
- Flyway `V1__init.sql` (5 tables + join + indexes)
- `BaseEntity` + 5 JPA entities + 5 repositories
- README / ARCHITECTURE.md / C4 diagrams

## Phase 1 — Ingestion Layer 🟡 NEXT (Week 3)
- Add MapStruct + `lombok-mapstruct-binding` to `pom.xml`
- DTOs: `CreateVehicleRequest`, `VehicleResponse`, `StartTripRequest`,
  `TripResponse`, `TelemetryReadingRequest`, `TelemetryReadingResponse`
- MapStruct mappers: `VehicleMapper`, `TripMapper`, `TelemetryMapper`
- Services: `VehicleService`, `TripService`, `TelemetryService` (resolve DTC codes)
- Controllers: `VehicleController`, `TripController`, `TelemetryController`
- Open endpoints (auth arrives in Phase 2)
- Branch: `feature/telemetry-ingest`

## Phase 2 — Auth & Tests (Week 4)
- Spring Security + JWT (`JwtAuthFilter`, `BCryptPasswordEncoder`)
- `AuthController`: register / login → JWT
- `GlobalExceptionHandler` (`@RestControllerAdvice`, `ApiError` §4.7)
- Protect endpoints + per-user ownership checks
- Mockito unit tests + `@SpringBootTest` integration tests
- Branch: `feature/auth-jwt`

## Phase 3 — Stats & Validation (Week 5)
- `GET /api/vehicles/{id}/stats` — native SQL with CTEs + `LEAD()` (§4.6)
- Bean Validation on DTOs (`@Valid`, `@NotNull`, `@Pattern` VIN, etc.)
- Flyway `V2__add_readings_recorded_at_index.sql` (§3.2)
- Pagination: `Page<TelemetryReading>` + `Pageable` (§4.3 `GET /trips/{id}/readings`)
- Testcontainers integration tests, Jacoco (target 70%+)
- Branch: `feature/stats-validation`

## Phase 4 — Event Streaming & Live Buffer (Kafka CQRS) ⭐ NEW
> Implements **ADR-3** and **ADR-4** from `ARCHITECTURE.md`.

### 4.1 Infra
- Extend `docker-compose.yml`: **Zookeeper + Kafka (Confluent 7.6.0)**
- **CORS** (required before any frontend work): add `CorsConfigurationSource`
  bean to `SecurityConfig` — origins `http://localhost:5173`, methods
  `GET,POST,PUT,DELETE,OPTIONS`, headers `Authorization,Content-Type`,
  allow credentials `true`. (Browser Same-Origin Policy: API :8080, UI :5173.)

### 4.2 Streaming
- `TelemetryEvent` record (readingId, tripId, vehicleId, recordedAt,
  speedKmh, rpm, engineTempC, fuelLevelPct, dtcCodes)
- `TelemetryEventProducer` — `JsonSerializer`, key = reading ID (per-reading
  ordering), fire-and-forget with async callback
- `TelemetryEventConsumer` — `JsonDeserializer` with trusted packages,
  feeds in-memory buffer
- `LiveTelemetryService` — `ConcurrentHashMap<Long, ArrayDeque<TelemetryEvent>>`
  (ring buffer of 50 per vehicle); ephemeral, clears on restart
- `GET /api/vehicles/{id}/live` endpoint (§4.5)
- Graceful degradation: Kafka down → reading still persisted, HTTP 201 succeeds
- Branch: `feature/kafka-live`

## Phase 5 — IoT Simulator (Python 3) ⭐ NEW
> Implements the **IoT Simulator** container (§2.2) — demo data source.

### 5.1 Structure
```text
simulator/
├── requirements.txt      # requests, argparse
├── simulator.py          # main script
├── route_data.json       # predefined GPS route (e.g. Sibiu)
└── README.md
```

### 5.2 Execution flow (1 Hz)
1. Parse CLI: `--email --password --vin --dtc-trigger --duration`
2. `POST /api/auth/login` → extract JWT
3. `GET /api/vehicles`; if VIN missing → `POST /api/vehicles`
4. `POST /api/trips` → `tripId`
5. Loop over `route_data.json`: compute speed from segment distance/time,
   interpolate RPM (gear shifts), ramp engine temp → ~90°C, drain fuel slowly,
   optional DTC injection → `POST /api/telemetry` with Bearer JWT → sleep 1s
6. Graceful shutdown (Ctrl+C / route end): `POST /api/trips/{id}/end`
- Branch: `feature/iot-simulator`

## Phase 6 — Web Dashboard (React + Leaflet + Chart.js) ⭐ NEW
> Implements the **Web Dashboard** container (§2.2): live map, analytics,
> DTC alerts — the CQRS live read-model consumer on the frontend.

### 6.1 Tech stack
- React 18 + Vite, `react-router-dom` v6
- `leaflet` + `react-leaflet` (map), `chart.js` + `react-chartjs-2` (graphs)
- `lucide-react` (icons), vanilla CSS with CSS variables (no Tailwind)

### 6.2 Structure
```text
frontend/
├── index.html
├── package.json
└── src/
    ├── main.jsx                 # React root + Router
    ├── App.jsx                  # Layout
    ├── index.css                # design tokens, CSS vars
    ├── api/client.js            # fetch wrapper + JWT interceptor
    ├── contexts/AuthContext.jsx # user + JWT in localStorage
    ├── pages/
    │   ├── Login.jsx
    │   ├── Dashboard.jsx        # fleet overview + stats
    │   └── LiveTracking.jsx     # flagship map + telemetry
    └── components/
        ├── ui/  layout/  map/VehicleMap.jsx  charts/TelemetryChart.jsx
```

### 6.3 UI / UX (premium automotive dark mode)
- Background `#0f172a`, glassmorphism panels `rgba(30,41,59,0.7)` +
  `backdrop-filter: blur(10px)`, accent emerald `#10b981` (normal) /
  crimson `#ef4444` (DTC alerts)
- Font: Inter / Outfit; dark map tiles (CartoDB Dark Matter) so the bright
  telemetry polyline pops; pulsing live marker; sliding DTC notifications

### 6.4 Functionality
- **Auth:** `AuthContext` stores JWT in localStorage; `client.js` attaches
  `Authorization: Bearer` and redirects to Login on 401
- **Dashboard (`/`):** `GET /api/vehicles` + `GET /api/vehicles/{id}/stats`
  (avg speed, max RPM, active DTCs)
- **Live Tracking (`/vehicle/:id/live`):**
  - **Polling 1s** `GET /api/vehicles/{id}/live` (resolves the open question:
    polling matches the ephemeral CQRS buffer; SSE only if push is wanted later)
  - move marker to latest lat/lng + append to polyline trail
  - feed speedKmh/rpm into scrolling Chart.js datasets
- Branch: `feature/web-dashboard`

## Phase 7 — Demo & Deploy (Stretch)
- Full `docker-compose.yml`: postgres + kafka + simulator + dashboard
- README architecture diagram + 1-min LinkedIn demo video
- Deploy backend + Postgres (Railway/Render)
- Branch: `feature/stretch-goals`

---

## Execution order
0 → 1 → 2 → 3 establish the backend (mostly per original plan).
4 → 5 → 6 deliver the **architecture's** frontend + simulator + streaming,
closing the gap between `ARCHITECTURE.md` (vision) and the build (reality).

## Verification (end-to-end demo)
1. Backend: apply CORS, run `.\mvnw.cmd clean verify`
2. `docker compose up -d` (postgres + kafka)
3. `.\mvnw.cmd spring-boot:run`
4. Frontend: `cd frontend; npm install; npm run dev`
5. Simulator: `cd simulator; pip install -r requirements.txt;
   python simulator.py --email test@example.com --password pw --vin 1234`
6. Open `http://localhost:5173`, login, select vehicle `1234`,
   watch map + charts update live as the simulator prints "Payload sent".

> Note: Gemini's spec placed the frontend in `frontend/` and simulator in
> `simulator/`. This plan adopts those paths (the earlier draft used
> `dashboard/` — consolidated to `frontend/`).

---

## Appendix A — Verified `GET /api/vehicles/{id}/live` behavior (read before coding the dashboard)

Confirmed against `LiveTelemetryService` + `VehicleController`:

1. **Order is ASCENDING (oldest first, newest last).** `getRecent()` returns the
   `ArrayDeque` head→tail (`addLast` newest, `removeFirst` oldest). The
   `ARCHITECTURE.md` §4.5 example (readingId 42 then 41) is illustrative only.
   → **Do NOT `.reverse()`** for Chart.js or polyline. **Current marker = last
   element** (`data[data.length-1]`), NOT the first.
2. **Ring buffer of 50** (`WINDOW_SIZE = 50`, per vehicle). Old readings drop out.
   → Use `/live` only for current marker + short trail + charts. For the FULL
   trip trail, fetch `GET /api/trips/{id}/readings` (paginated) separately.
3. **Dedup across polls by `readingId`** (consecutive 1s polls overlap):
   `Array.from(new Map(merged.map(r => [r.readingId, r])).values())
    .sort((a,b) => a.readingId - b.readingId)`
4. **DTC alerts:** trigger only on a NEW code (diff of `Set`s between polls),
   never on every poll while a fault persists.
5. **Recommended `LiveTracking.jsx` state:** `latestReading` (last element),
   `chartWindow` (last N, chronological), `tripTrail` (accumulated + deduped).
   Poll via `react-query` `refetchInterval: 1000` or `setInterval`.
