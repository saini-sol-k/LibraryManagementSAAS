package com.librarysaas.seat.repository;

import com.librarysaas.seat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every lookup is library-scoped. Fetching a seat by id alone would let a
 * caller probe another tenant's seat ids, so the library is always part of the
 * query rather than something checked afterwards.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    Optional<Seat> findBySeatIdAndLibraryLibraryId(Long seatId, Long libraryId);

    boolean existsByLibraryLibraryIdAndSeatNumber(Long libraryId, String seatNumber);

    Optional<Seat> findByLibraryLibraryIdAndSeatNumber(Long libraryId, String seatNumber);

    /**
     * Ordered by length first, then lexicographically.
     *
     * seat_number is a VARCHAR, so a plain alphabetical sort lists generated
     * seats as 1, 10, 100, 11 - which is unreadable once a library has more than
     * nine of them. Sorting on length first makes equal-width numbers compare
     * correctly, giving 1..9, 10..99, 100.., and it leaves lettered seats
     * (A001, B002) grouped as before.
     */
    @Query("SELECT s FROM Seat s "
            + "LEFT JOIN FETCH s.zone "
            + "LEFT JOIN FETCH s.seatType "
            + "WHERE s.library.libraryId = :libraryId "
            + "AND (:status IS NULL OR s.status = :status) "
            + "AND (:zoneId IS NULL OR s.zone.zoneId = :zoneId) "
            + "AND (:seatTypeId IS NULL OR s.seatType.seatTypeId = :seatTypeId) "
            + "AND (:search IS NULL OR LOWER(s.seatNumber) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY LENGTH(s.seatNumber), s.seatNumber")
    List<Seat> search(@Param("libraryId") Long libraryId,
                      @Param("status") String status,
                      @Param("zoneId") Long zoneId,
                      @Param("seatTypeId") Long seatTypeId,
                      @Param("search") String search);

    long countByLibraryLibraryIdAndStatus(Long libraryId, String status);

    /**
     * Bulk lookup for seat-count changes, which reason about a contiguous block of
     * seat numbers at once rather than one seat at a time.
     */
    List<Seat> findByLibraryLibraryIdAndSeatNumberIn(Long libraryId, Collection<String> seatNumbers);
}
