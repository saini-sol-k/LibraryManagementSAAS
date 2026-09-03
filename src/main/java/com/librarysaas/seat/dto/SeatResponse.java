package com.librarysaas.seat.dto;

import com.librarysaas.seat.entity.Seat;
import com.librarysaas.seat.entity.SeatAssignment;

import java.time.LocalDateTime;

/**
 * A seat as the API exposes it, with its current allocation folded in so a seat
 * grid can be rendered from one call.
 *
 * created_by / updated_by are internal user ids and are deliberately omitted.
 */
public class SeatResponse {

    private Long seatId;
    private Long libraryId;
    private String seatNumber;
    private String status;

    private Long zoneId;
    private String zoneName;
    private String floor;

    private Long seatTypeId;
    private String seatTypeName;

    /** Null when the seat has no active allocation. */
    private SeatAllocationResponse currentAllocation;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SeatResponse from(Seat seat, SeatAssignment activeAssignment) {
        SeatResponse response = new SeatResponse();
        response.seatId = seat.getSeatId();
        response.libraryId = seat.getLibrary() != null ? seat.getLibrary().getLibraryId() : null;
        response.seatNumber = seat.getSeatNumber();
        response.status = seat.getStatus();

        if (seat.getZone() != null) {
            response.zoneId = seat.getZone().getZoneId();
            response.zoneName = seat.getZone().getName();
            response.floor = seat.getZone().getFloor();
        }
        if (seat.getSeatType() != null) {
            response.seatTypeId = seat.getSeatType().getSeatTypeId();
            response.seatTypeName = seat.getSeatType().getName();
        }

        response.currentAllocation =
                activeAssignment == null ? null : SeatAllocationResponse.from(activeAssignment);
        response.createdAt = seat.getCreatedAt();
        response.updatedAt = seat.getUpdatedAt();
        return response;
    }

    public Long getSeatId() {
        return seatId;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public String getStatus() {
        return status;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public String getFloor() {
        return floor;
    }

    public Long getSeatTypeId() {
        return seatTypeId;
    }

    public String getSeatTypeName() {
        return seatTypeName;
    }

    public SeatAllocationResponse getCurrentAllocation() {
        return currentAllocation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
