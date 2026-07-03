package com.gabrielbicu.telemetry.repository;

import com.gabrielbicu.telemetry.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** All vehicles that belong to a given user. */
    List<Vehicle> findByUserId(Long userId);

    /** Used to load a vehicle only if it belongs to a specific user (ownership check). */
    Optional<Vehicle> findByIdAndUserId(Long id, Long userId);

    boolean existsByVin(String vin);
}
