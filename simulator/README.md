# IoT Simulator

Python 3 script that mimics an OBD-II device streaming telemetry to the
AutoTelemetry API. Used for demos and end-to-end testing of the live map
and dashboard.

## Setup

```bash
pip install -r requirements.txt
```

## Run

```bash
python simulator.py --email demo@autotelemetry.dev --password DemoPass123! --vin WBA3A5G59DNP26082
```

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--email` / `--password` | demo creds | API login; auto-registers if account missing |
| `--vin` | WBA3A5G59DNP26082 | Vehicle VIN (created if not present) |
| `--make` / `--model` / `--year` / `--plate` | BMW 320d 2019 | Vehicle metadata |
| `--api` | http://localhost:8080 | API base URL |
| `--rate` | 1.0 | Telemetry samples per second |
| `--duration` | 120 | Trip length in seconds |
| `--speed` | 55.0 | Target cruising speed (km/h) |
| `--dtc-trigger` | none | OBD-II code injected mid-trip (e.g. P0301) |

The script drives a loop around `route_data.json`, computes speed from segment
distance, interpolates RPM, ramps engine temperature toward ~90°C and drains
fuel, then ends the trip on completion or Ctrl+C.
