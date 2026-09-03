package com.librarysaas.seat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a seat.
 *
 * libraryId is deliberately absent: it comes from the path, so a caller cannot
 * move a seat into another tenant by editing the body.
 */
public class SeatRequest {

    @NotBlank(message = "Seat number is required")
    @Size(max = 50, message = "Seat number must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 _/-]{0,49}$",
            message = "Seat number may only contain letters, digits, spaces and - _ /")
    private String seatNumber;

    /** Optional. Must belong to the same library when supplied. */
    private Long zoneId;

    /** Optional. Must belong to the same library when supplied. */
    private Long seatTypeId;

    /**
     * Optional on create (defaults to AVAILABLE). OCCUPIED is not settable
     * directly - it is a consequence of allocating the seat.
     */
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Long getSeatTypeId() {
        return seatTypeId;
    }

    public void setSeatTypeId(Long seatTypeId) {
        this.seatTypeId = seatTypeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
