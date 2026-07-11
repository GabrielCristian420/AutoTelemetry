import argparse
import json
import math
import os
import time

import requests

ROUTE_FILE = os.path.join(os.path.dirname(__file__), "route_data.json")


def haversine(lat1, lng1, lat2, lng2):
    r = 6371.0
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def interpolate(waypoints, t):
    n = len(waypoints)
    if n == 1:
        return waypoints[0], 0.0
    seg = t * (n - 1)
    i = min(int(seg), n - 2)
    f = seg - i
    lat = waypoints[i][0] + (waypoints[i + 1][0] - waypoints[i][0]) * f
    lng = waypoints[i][1] + (waypoints[i + 1][1] - waypoints[i][1]) * f
    return (lat, lng), haversine(waypoints[i][0], waypoints[i][1], waypoints[i + 1][0], waypoints[i + 1][1])


class Simulator:
    def __init__(self, args):
        self.base = args.api.rstrip("/")
        self.session = requests.Session()
        self.email = args.email
        self.password = args.password
        self.vin = args.vin
        self.make = args.make
        self.model = args.model
        self.year = args.year
        self.plate = args.plate
        self.rate = args.rate
        self.duration = args.duration
        self.dtc_trigger = args.dtc_trigger
        self.speed_profile = args.speed
        self.waypoints = json.load(open(ROUTE_FILE))
        self.vehicle_id = None
        self.trip_id = None
        self.engine_temp = 22.0
        self.fuel = 100.0

    def auth(self):
        r = self.session.post(
            f"{self.base}/api/auth/login",
            json={"email": self.email, "password": self.password},
        )
        if r.status_code == 401:
            self.session.post(
                f"{self.base}/api/auth/register",
                json={"email": self.email, "password": self.password, "fullName": "Demo Driver"},
            )
            r = self.session.post(
                f"{self.base}/api/auth/login",
                json={"email": self.email, "password": self.password},
            )
        r.raise_for_status()
        token = r.json()["token"]
        self.session.headers.update({"Authorization": f"Bearer {token}"})

    def ensure_vehicle(self):
        r = self.session.get(f"{self.base}/api/vehicles")
        r.raise_for_status()
        for v in r.json():
            if v["vin"] == self.vin:
                self.vehicle_id = v["id"]
                return
        r = self.session.post(
            f"{self.base}/api/vehicles",
            json={
                "vin": self.vin,
                "make": self.make,
                "model": self.model,
                "year": self.year,
                "plate": self.plate,
            },
        )
        r.raise_for_status()
        self.vehicle_id = r.json()["id"]

    def start_trip(self):
        r = self.session.post(f"{self.base}/api/trips", json={"vehicleId": self.vehicle_id})
        r.raise_for_status()
        self.trip_id = r.json()["id"]

    def end_trip(self):
        if self.trip_id is None:
            return
        try:
            r = self.session.post(f"{self.base}/api/trips/{self.trip_id}/end")
            print(f"Trip ended: {r.status_code}")
        except requests.RequestException as e:
            print(f"Failed to end trip: {e}")

    def run(self):
        self.auth()
        self.ensure_vehicle()
        self.start_trip()
        steps = int(self.duration * self.rate)
        try:
            for s in range(steps):
                t = s / steps
                (lat, lng), seg_km = interpolate(self.waypoints, t)
                speed = self.speed_profile * (0.6 + 0.4 * math.sin(t * math.pi * 2))
                rpm = int(900 + speed * 32 + 200 * math.sin(t * math.pi * 6))
                self.engine_temp += (90.0 - self.engine_temp) * 0.05
                self.fuel = max(0.0, self.fuel - (seg_km * 0.08) - 0.01)
                dtc = []
                if self.dtc_trigger and s == int(steps * 0.4):
                    dtc = [self.dtc_trigger]
                payload = {
                    "tripId": self.trip_id,
                    "recordedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                    "speedKmh": round(speed, 1),
                    "rpm": max(800, rpm),
                    "engineTempC": round(self.engine_temp, 1),
                    "fuelLevelPct": round(self.fuel, 1),
                    "lat": lat,
                    "lng": lng,
                    "dtcCodes": dtc,
                }
                r = self.session.post(f"{self.base}/api/telemetry", json=payload)
                print(f"[{s + 1}/{steps}] {r.status_code} speed={speed:.1f} temp={self.engine_temp:.1f} fuel={self.fuel:.1f} dtc={dtc}")
                time.sleep(1.0 / self.rate)
        except KeyboardInterrupt:
            print("Interrupted by user")
        finally:
            self.end_trip()


def main():
    p = argparse.ArgumentParser(description="AutoTelemetry OBD-II IoT simulator")
    p.add_argument("--email", default="demo@autotelemetry.dev")
    p.add_argument("--password", default="DemoPass123!")
    p.add_argument("--vin", default="WBA3A5G59DNP26082")
    p.add_argument("--make", default="BMW")
    p.add_argument("--model", default="320d")
    p.add_argument("--year", type=int, default=2019)
    p.add_argument("--plate", default="SB-01-ABC")
    p.add_argument("--api", default="http://localhost:8080")
    p.add_argument("--rate", type=float, default=1.0, help="telemetry samples per second")
    p.add_argument("--duration", type=int, default=120, help="trip duration in seconds")
    p.add_argument("--speed", type=float, default=55.0, help="target cruising speed km/h")
    p.add_argument("--dtc-trigger", default=None, help="OBD-II code to inject mid-trip, e.g. P0301")
    args = p.parse_args()
    Simulator(args).run()


if __name__ == "__main__":
    main()
