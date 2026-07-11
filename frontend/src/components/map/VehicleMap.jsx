import { MapContainer, TileLayer, Polyline, Marker, Popup, useMap } from "react-leaflet";
import { useEffect } from "react";
import L from "leaflet";

const DARK_TILES =
  "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png";

const dotIcon = L.divIcon({
  className: "",
  html:
    '<div style="width:16px;height:16px;border-radius:50%;background:#10b981;box-shadow:0 0 0 6px rgba(16,185,129,0.35);"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
});

function Recenter({ position }) {
  const map = useMap();
  useEffect(() => {
    if (position) map.setView(position, Math.max(map.getZoom(), 14));
  }, [position, map]);
  return null;
}

export default function VehicleMap({ trail, latest }) {
  const positions = trail
    .filter((r) => r.lat != null && r.lng != null)
    .map((r) => [r.lat, r.lng]);
  const current =
    latest && latest.lat != null && latest.lng != null
      ? [latest.lat, latest.lng]
      : null;
  const center =
    current ||
    (positions.length ? positions[positions.length - 1] : [45.7925, 24.1524]);

  return (
    <div className="map-wrap">
      <MapContainer center={center} zoom={14} style={{ height: "100%" }}>
        <TileLayer
          url={DARK_TILES}
          attribution="&copy; OpenStreetMap &copy; CARTO"
        />
        {positions.length > 1 && (
          <Polyline positions={positions} pathOptions={{ color: "#10b981", weight: 3 }} />
        )}
        {current && (
          <Marker position={current} icon={dotIcon}>
            <Popup>Speed {latest.speedKmh} km/h · RPM {latest.rpm}</Popup>
          </Marker>
        )}
        <Recenter position={current} />
      </MapContainer>
    </div>
  );
}
