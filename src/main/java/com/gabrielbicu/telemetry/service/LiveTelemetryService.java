package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.dto.TelemetryEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory "live" read model of recent telemetry, fed by the Kafka
 * consumer. This is the CQRS-style payoff of adding a message broker: the
 * synchronous ingestion path writes to Postgres (the system of record), while
 * a separate consumer maintains a cheap, fast, in-memory window of the latest
 * readings per vehicle for dashboards / live views — without ever hitting the
 * database.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>A {@link ConcurrentHashMap} keyed by vehicle id, each holding a bounded
 *       {@link ArrayDeque} (ring buffer) of the last N events. Bounding the
 *       size prevents unbounded memory growth for chatty vehicles.</li>
 *   <li>This is intentionally NOT persistent — it's an ephemeral cache that
 *       repopulates as events stream in. Restarting the app clears it; that is
 *       fine for a live view.</li>
 * </ul>
 */
@Service
public class LiveTelemetryService {

    /** Max number of recent readings kept per vehicle in the live buffer. */
    private static final int WINDOW_SIZE = 50;

    private final Map<Long, Deque<TelemetryEvent>> liveBuffer = new ConcurrentHashMap<>();

    /** Called by the Kafka consumer for every ingested reading. */
    public void record(TelemetryEvent event) {
        if (event == null || event.vehicleId() == null) {
            return;
        }
        Deque<TelemetryEvent> buffer = liveBuffer.computeIfAbsent(
                event.vehicleId(), k -> new ArrayDeque<>(WINDOW_SIZE));
        synchronized (buffer) {
            buffer.addLast(event);
            while (buffer.size() > WINDOW_SIZE) {
                buffer.removeFirst();
            }
        }
    }

    /**
     * Returns the most recent readings for a vehicle (oldest first), or an
     * empty list if the live buffer has no data yet for that vehicle.
     */
    public List<TelemetryEvent> getRecent(Long vehicleId) {
        Deque<TelemetryEvent> buffer = liveBuffer.get(vehicleId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    /** Number of vehicles currently tracked in the live buffer. */
    public int trackedVehicleCount() {
        return liveBuffer.size();
    }
}
