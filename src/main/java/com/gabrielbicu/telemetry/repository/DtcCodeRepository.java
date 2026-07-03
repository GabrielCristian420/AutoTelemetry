package com.gabrielbicu.telemetry.repository;

import com.gabrielbicu.telemetry.domain.DtcCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DtcCodeRepository extends JpaRepository<DtcCode, Long> {

    /** Look up a diagnostic code by its OBD-II identifier (e.g. "P0301"). */
    Optional<DtcCode> findByCode(String code);
}
