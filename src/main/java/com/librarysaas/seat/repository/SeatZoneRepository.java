package com.librarysaas.seat.repository;

import com.librarysaas.seat.entity.SeatZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Library-scoped, so a seat cannot be pointed at another tenant's zone. */
public interface SeatZoneRepository extends JpaRepository<SeatZone, Long> {

    List<SeatZone> findByLibraryLibraryIdOrderByName(Long libraryId);

    Optional<SeatZone> findByZoneIdAndLibraryLibraryId(Long zoneId, Long libraryId);
}
