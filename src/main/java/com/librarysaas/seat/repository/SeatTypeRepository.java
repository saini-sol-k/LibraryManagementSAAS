package com.librarysaas.seat.repository;

import com.librarysaas.seat.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Library-scoped, so a seat cannot be pointed at another tenant's type. */
public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {

    List<SeatType> findByLibraryLibraryIdOrderByName(Long libraryId);

    Optional<SeatType> findBySeatTypeIdAndLibraryLibraryId(Long seatTypeId, Long libraryId);
}
