import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend
);

export default function TelemetryChart({ data }) {
  const labels = data.map((r) => r.readingId);
  const chartData = {
    labels,
    datasets: [
      {
        label: "Speed km/h",
        data: data.map((r) => r.speedKmh),
        borderColor: "#10b981",
        tension: 0.3,
        pointRadius: 0,
      },
      {
        label: "RPM",
        data: data.map((r) => r.rpm),
        borderColor: "#38bdf8",
        tension: 0.3,
        pointRadius: 0,
        yAxisID: "y1",
      },
    ],
  };
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    scales: {
      y: { ticks: { color: "#94a3b8" }, grid: { color: "#1e293b" } },
      y1: {
        position: "right",
        ticks: { color: "#94a3b8" },
        grid: { drawOnChartArea: false },
      },
      x: {
        ticks: { color: "#94a3b8", maxTicksLimit: 8 },
        grid: { color: "#1e293b" },
      },
    },
    plugins: { legend: { labels: { color: "#e2e8f0" } } },
  };
  return (
    <div style={{ height: 240 }}>
      <Line data={chartData} options={options} />
    </div>
  );
}
