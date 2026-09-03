package com.librarysaas.seat.service;

import com.librarysaas.seat.dto.*;

import java.util.List;

/**
 * Seat inventory and allocation for a library.
 *
 * Every method takes the owning libraryId because seats, zones, types and
 * assignments are all tenant-scoped rows; the library is never inferred from a
 * header.
 */
public interface SeatService {

    /* inventory */

    List<SeatResponse> getSeats(Long libraryId, String status, Long zoneId, Long seatTypeId, String search);

    SeatResponse getSeat(Long libraryId, Long seatId);

    SeatResponse createSeat(Long libraryId, SeatRequest request);

    SeatResponse updateSeat(Long libraryId, Long seatId, SeatRequest request);

    /**
     * Takes a seat out of service. There is no SEAT_DELETE permission and
     * assignment history references the row, so this is a status change to
     * INACTIVE rather than a hard delete.
     */
    SeatResponse deactivateSeat(Long libraryId, Long seatId);

    /* allocation */

    SeatAllocationResponse allocateSeat(Long libraryId, Long seatId, SeatAllocationRequest request);

    SeatAllocationResponse releaseSeat(Long libraryId, Long seatId);

    /** Null when the seat is not currently allocated. */
    SeatAllocationResponse getCurrentAllocation(Long libraryId, Long seatId);

    /** The student's active allocation, or null. Scoped by the student's own library. */
    SeatAllocationResponse getStudentAllocation(Long studentId);

    /* reference data, needed to build seat forms */

    List<SeatTypeResponse> getSeatTypes(Long libraryId);

    List<SeatZoneResponse> getSeatZones(Long libraryId);
}
