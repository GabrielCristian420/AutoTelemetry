# AutoTelemetry Dashboard

React + Vite single-page app that visualizes live vehicle telemetry. Consumes
the Spring Boot API (CORS-enabled for `http://localhost:5173`).

## Stack

- React 18, Vite
- `react-leaflet` + Leaflet (live map, dark CartoDB tiles)
- `chart.js` + `react-chartjs-2` (speed / RPM charts)
- `react-router-dom`, `lucide-react`

## Run

```bash
npm install
npm run dev
```

Open http://localhost:5173, sign in (or register), and open **Live track** on a
vehicle. Start the Python simulator (`../simulator`) to stream data.

## Behaviour notes (verified against the API)

- `GET /api/vehicles/{id}/live` returns the **last 50 readings ascending by
  `readingId`** (oldest first). The current position is the **last** element.
- The map draws the accumulated, deduped trail; charts use the chronological
  window; DTC alerts fire only on a newly-seen code.
