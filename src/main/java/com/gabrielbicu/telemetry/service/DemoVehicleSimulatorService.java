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
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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

    /**
     * Real driving route loaded from {@code demo-route.csv} (Sibiu → Râmnicu Vâlcea via DN7 Valea Oltului).
     * 983 waypoints at ~100m resolution, precisely following actual road curves from OSRM.
     */
    private double[][] routeWaypoints;

    @PostConstruct
    void init() {
        loadRoute();
        closeOpenDemoTrips();
    }

    private void loadRoute() {
        try {
            ClassPathResource resource = new ClassPathResource("demo-route.csv");
            List<double[]> points = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",");
                    points.add(new double[]{
                            Double.parseDouble(parts[0].trim()),
                            Double.parseDouble(parts[1].trim())
                    });
                }
            }
            routeWaypoints = points.toArray(new double[0][]);
            log.info("Loaded {} waypoints for demo route simulation", routeWaypoints.length);
        } catch (Exception e) {
            log.error("Failed to load demo-route.csv, falling back to minimal route: {}", e.getMessage());
            routeWaypoints = new double[][]{
                    {45.7925, 24.1524}, {45.7931, 24.1538}, {45.7940, 24.1552},
                    {45.7952, 24.1569}, {45.7961, 24.1588}, {45.7928, 24.1517}
            };
        }
    }

    private void closeOpenDemoTrips() {
        try {
            List<Trip> openTrips = tripRepository.findAll().stream()
                    .filter(t -> t.getEndedAt() == null)
                    .toList();
            for (Trip t : openTrips) {
                t.setEndedAt(Instant.now());
                tripRepository.save(t);
            }
            log.info("Closed {} stale demo trips on startup for clean route rendering", openTrips.size());
        } catch (Exception e) {
            log.warn("Failed to auto-close stale demo trips: {}", e.getMessage());
        }
    }

    private final Map<Long, Long> lastActivityMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> routeProgressMap = new ConcurrentHashMap<>(); // fractional index along route
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
        double progress = routeProgressMap.computeIfAbsent(vehicleId, k -> {
            Optional<TelemetryReading> lastReading = readingRepository.findFirstByTripIdOrderByIdDesc(tripId);
            if (lastReading.isPresent() && lastReading.get().getLat() != null) {
                return (double) findClosestWaypointIndex(lastReading.get().getLat(), lastReading.get().getLng());
            }
            return 0.0;
        });

        double currentFuel = fuelMap.computeIfAbsent(vehicleId, k -> 75.0);
        double currentTemp = tempMap.computeIfAbsent(vehicleId, k -> 85.0);

        // Determine speed based on segment characteristics (longer segments = highway = faster)
        int baseIndex = ((int) progress) % routeWaypoints.length;
        int nextWaypoint = (baseIndex + 1) % routeWaypoints.length;
        double segmentDistKm = haversineDistance(
                routeWaypoints[baseIndex][0], routeWaypoints[baseIndex][1],
                routeWaypoints[nextWaypoint][0], routeWaypoints[nextWaypoint][1]);

        // Dynamic speed based on road type: highways vs national roads (DN7) vs urban segments
        double speed;
        if (segmentDistKm > 3.0) {
            speed = 110.0 + Math.sin(progress * 0.5) * 20.0; // Highway: 90-130 km/h
        } else if (segmentDistKm > 0.4) {
            speed = 72.0 + Math.sin(progress * 1.1) * 15.0;  // National road (DN7): 57-87 km/h
        } else {
            speed = 45.0 + Math.sin(progress * 2.3) * 10.0;  // Urban / sharp turns: 35-55 km/h
        }

        // Advance position along route based on speed (tick = 2 seconds)
        double distanceThisTickKm = speed * (2.0 / 3600.0);
        double remaining = distanceThisTickKm;

        while (remaining > 0) {
            int curBase = ((int) progress) % routeWaypoints.length;
            double curFraction = progress - (int) progress;
            int curNext = (curBase + 1) % routeWaypoints.length;

            double curSegDist = haversineDistance(
                    routeWaypoints[curBase][0], routeWaypoints[curBase][1],
                    routeWaypoints[curNext][0], routeWaypoints[curNext][1]);

            if (curSegDist < 0.001) {
                // Skip zero-length segments
                progress = (int) progress + 1;
                continue;
            }

            double distToEndOfSeg = curSegDist * (1.0 - curFraction);

            if (remaining <= distToEndOfSeg) {
                // We stop within this segment
                progress += remaining / curSegDist;
                remaining = 0;
            } else {
                // We pass this segment entirely, move to next
                remaining -= distToEndOfSeg;
                progress = (int) progress + 1;
            }
        }

        // Wrap around when we reach the end of the route
        if (progress >= routeWaypoints.length) {
            progress -= routeWaypoints.length;
        }

        routeProgressMap.put(vehicleId, progress);

        // Interpolate lat/lng between the two surrounding waypoints
        int interpBase = ((int) progress) % routeWaypoints.length;
        double interpFrac = progress - (int) progress;
        int interpNext = (interpBase + 1) % routeWaypoints.length;

        double lat = routeWaypoints[interpBase][0] + interpFrac * (routeWaypoints[interpNext][0] - routeWaypoints[interpBase][0]);
        double lng = routeWaypoints[interpBase][1] + interpFrac * (routeWaypoints[interpNext][1] - routeWaypoints[interpBase][1]);

        // Smooth physics metrics
        int rpm = (int) (1500 + (speed / 130.0) * 2500);
        currentTemp = Math.min(95.0, Math.max(82.0, currentTemp + (Math.random() - 0.45) * 0.3));
        currentFuel = Math.max(5.0, currentFuel - 0.02);

        tempMap.put(vehicleId, currentTemp);
        fuelMap.put(vehicleId, currentFuel);

        TelemetryReadingRequest request = new TelemetryReadingRequest();
        request.setTripId(openTrip.getId());
        request.setRecordedAt(Instant.now());
        request.setSpeedKmh(Math.round(speed * 10.0) / 10.0);
        request.setRpm(rpm);
        request.setEngineTempC(Math.round(currentTemp * 10.0) / 10.0);
        request.setFuelLevelPct(Math.round(currentFuel * 10.0) / 10.0);
        request.setLat(Math.round(lat * 1_000_000.0) / 1_000_000.0);
        request.setLng(Math.round(lng * 1_000_000.0) / 1_000_000.0);

        // Inject occasional DTC for demo interest every ~50 waypoints
        if (interpBase % 50 == 10) {
            request.setDtcCodes(List.of("P0301"));
        } else {
            request.setDtcCodes(List.of());
        }

        telemetryService.ingestReading(request, userId);
    }

    /** Haversine formula — returns distance in kilometers between two GPS coordinates. */
    private static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                  * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private int findClosestWaypointIndex(double lat, double lng) {
        int bestIdx = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < routeWaypoints.length; i++) {
            double dLat = routeWaypoints[i][0] - lat;
            double dLng = routeWaypoints[i][1] - lng;
            double dist = dLat * dLat + dLng * dLng;
            if (dist < minDistance) {
                minDistance = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
