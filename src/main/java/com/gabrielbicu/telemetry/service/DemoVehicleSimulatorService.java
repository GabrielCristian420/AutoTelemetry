package com.gabrielbicu.telemetry.service;

import com.gabrielbicu.telemetry.domain.TelemetryReading;
import com.gabrielbicu.telemetry.domain.Trip;
import com.gabrielbicu.telemetry.domain.Vehicle;
import com.gabrielbicu.telemetry.dto.TelemetryReadingRequest;
import com.gabrielbicu.telemetry.repository.TelemetryReadingRepository;
import com.gabrielbicu.telemetry.repository.TripRepository;
import com.gabrielbicu.telemetry.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in cloud simulator for zero-friction recruiter demos.
 *
 * <p>Activated only when the {@code demo-active} Spring profile is active.
 * Implements an <b>Active-on-Demand</b> workflow:
 * <ul>
 *   <li>When a client polls {@code GET /api/vehicles/{id}/live},
 *       {@link #recordActivity(Long)} stores the timestamp.</li>
 *   <li>The {@link #simulateTick()} task runs every 2 seconds. If a vehicle has
 *       been polled within the last 10 seconds, it generates the next realistic
 *       telemetry sample and ingests it.</li>
 *   <li>If no client is polling (e.g. tab closed), simulation pauses after 10s,
 *       allowing serverless databases (Neon) to scale to zero and preserve CU-hours.</li>
 *   <li>State Continuity: Resumes from the last saved GPS coordinate and metrics
 *       stored in Postgres or memory so the vehicle never teleports on cold starts.</li>
 * </ul>
 */
@Service
@Profile("demo-active")
@EnableScheduling
public class DemoVehicleSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(DemoVehicleSimulatorService.class);
    private static final long ACTIVITY_TIMEOUT_MS = 10_000L;

    // Synthetic GPS route around Sibiu / Transylvania
    private static final double[][] ROUTE_WAYPOINTS = {
            {45.7925, 24.1524}, {45.7931, 24.1538}, {45.7940, 24.1552},
            {45.7952, 24.1569}, {45.7961, 24.1588}, {45.7970, 24.1601},
            {45.7982, 24.1610}, {45.7995, 24.1618}, {45.8007, 24.1625},
            {45.8018, 24.1629}, {45.8026, 24.1621}, {45.8031, 24.1608},
            {45.8030, 24.1592}, {45.8024, 24.1576}, {45.8013, 24.1561},
            {45.8001, 24.1549}, {45.7989, 24.1539}, {45.7976, 24.1528},
            {45.7962, 24.1519}, {45.7948, 24.1513}, {45.7935, 24.1511},
            {45.7928, 24.1517}
    };

    private final Map<Long, Long> lastActivityMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> routeIndexMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> fuelMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> tempMap = new ConcurrentHashMap<>();

    private final TelemetryService telemetryService;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final TelemetryReadingRepository readingRepository;

    public DemoVehicleSimulatorService(TelemetryService telemetryService,
                                       VehicleRepository vehicleRepository,
                                       TripRepository tripRepository,
                                       TelemetryReadingRepository readingRepository) {
        this.telemetryService = telemetryService;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.readingRepository = readingRepository;
    }

    /** Called whenever a client polls live telemetry for a vehicle. */
    public void recordActivity(Long vehicleId) {
        if (vehicleId != null) {
            lastActivityMap.put(vehicleId, System.currentTimeMillis());
        }
    }

    @Scheduled(fixedRate = 2000)
    public void simulateTick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : lastActivityMap.entrySet()) {
            Long vehicleId = entry.getKey();
            Long lastActive = entry.getValue();

            if (now - lastActive <= ACTIVITY_TIMEOUT_MS) {
                try {
                    processVehicleSimulation(vehicleId);
                } catch (Exception e) {
                    log.error("Error running active-on-demand demo simulation for vehicle {}: {}", vehicleId, e.getMessage());
                }
            }
        }
    }

    private void processVehicleSimulation(Long vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
        if (vehicleOpt.isEmpty()) {
            return;
        }
        Vehicle vehicle = vehicleOpt.get();
        Long userId = vehicle.getUser().getId();

        // Get or create an open trip
        List<Trip> trips = tripRepository.findByVehicleId(vehicleId);
        Trip openTrip = trips.stream()
                .filter(t -> t.getEndedAt() == null)
                .findFirst()
                .orElse(null);

        if (openTrip == null) {
            openTrip = new Trip();
            openTrip.setVehicle(vehicle);
            openTrip.setStartedAt(Instant.now());
            openTrip = tripRepository.save(openTrip);
        }

        // State continuity: recover position & metrics if not in memory
        final Long tripId = openTrip.getId();
        int routeIndex = routeIndexMap.computeIfAbsent(vehicleId, k -> {
            Optional<TelemetryReading> lastReading = readingRepository.findFirstByTripIdOrderByIdDesc(tripId);
            if (lastReading.isPresent() && lastReading.get().getLat() != null) {
                return findClosestWaypointIndex(lastReading.get().getLat(), lastReading.get().getLng());
            }
            return 0;
        });

        double currentFuel = fuelMap.computeIfAbsent(vehicleId, k -> 75.0);
        double currentTemp = tempMap.computeIfAbsent(vehicleId, k -> 85.0);

        // Advance waypoint index
        int nextIndex = (routeIndex + 1) % ROUTE_WAYPOINTS.length;
        routeIndexMap.put(vehicleId, nextIndex);

        double lat = ROUTE_WAYPOINTS[nextIndex][0];
        double lng = ROUTE_WAYPOINTS[nextIndex][1];

        // Smooth physics metrics
        double speed = 45.0 + Math.sin(nextIndex) * 15.0;
        int rpm = (int) (1500 + (speed / 120.0) * 2000);
        currentTemp = Math.min(90.0, currentTemp + 0.1);
        currentFuel = Math.max(10.0, currentFuel - 0.05);

        tempMap.put(vehicleId, currentTemp);
        fuelMap.put(vehicleId, currentFuel);

        TelemetryReadingRequest request = new TelemetryReadingRequest();
        request.setTripId(openTrip.getId());
        request.setRecordedAt(Instant.now());
        request.setSpeedKmh(Math.round(speed * 10.0) / 10.0);
        request.setRpm(rpm);
        request.setEngineTempC(Math.round(currentTemp * 10.0) / 10.0);
        request.setFuelLevelPct(Math.round(currentFuel * 10.0) / 10.0);
        request.setLat(lat);
        request.setLng(lng);

        // Inject occasional DTC for demo interest when at waypoint index 10
        if (nextIndex == 10) {
            request.setDtcCodes(List.of("P0301"));
        } else {
            request.setDtcCodes(List.of());
        }

        telemetryService.ingestReading(request, userId);
    }

    private int findClosestWaypointIndex(double lat, double lng) {
        int bestIdx = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < ROUTE_WAYPOINTS.length; i++) {
            double dLat = ROUTE_WAYPOINTS[i][0] - lat;
            double dLng = ROUTE_WAYPOINTS[i][1] - lng;
            double dist = dLat * dLat + dLng * dLng;
            if (dist < minDistance) {
                minDistance = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
