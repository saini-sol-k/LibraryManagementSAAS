package com.librarysaas.organization.dto;

import com.librarysaas.seat.dto.SeatProvisioningResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a seat-count change actually did.
 *
 * The previous count is included so the caller can show the change rather than
 * only its result. The four counts are kept apart because they are four
 * different events, and the client phrases them differently: seats added are
 * either created or brought back, and seats withdrawn are either deleted
 * outright or kept as inactive rows because history points at them.
 *
 * seatsAdded and seatsWithdrawn are the totals the UI actually reports
 * ("20 seats added successfully"), served here rather than summed on the client
 * so both sides agree on what the change amounted to.
 */
@Schema(description = "Outcome of a seat-count change.")
public class LibrarySeatCountResponse {

    private final Long libraryId;
    private final String libraryName;
    private final Integer previousSeatCount;
    private final Integer seatCount;
    private final int seatsCreated;
    private final int seatsReactivated;
    private final int seatsRemoved;
    private final int seatsRetired;
    private final int seatsAdded;
    private final int seatsWithdrawn;
    private final String seatRange;

    public LibrarySeatCountResponse(Long libraryId, String libraryName,
                                    Integer previousSeatCount,
                                    SeatProvisioningResult result) {
        this.libraryId = libraryId;
        this.libraryName = libraryName;
        this.previousSeatCount = previousSeatCount;
        this.seatCount = result.getSeatCount();
        this.seatsCreated = result.getSeatsCreated();
        this.seatsReactivated = result.getSeatsReactivated();
        this.seatsRemoved = result.getSeatsRemoved();
        this.seatsRetired = result.getSeatsRetired();
        this.seatsAdded = result.getSeatsAdded();
        this.seatsWithdrawn = result.getSeatsWithdrawn();
        this.seatRange = result.getSeatRange();
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public Integer getPreviousSeatCount() {
        return previousSeatCount;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    @Schema(description = "Seats newly created by this change.")
    public int getSeatsCreated() {
        return seatsCreated;
    }

    @Schema(description = "Seats an earlier reduction had retired and this change brought back, "
            + "with their original number and history intact.")
    public int getSeatsReactivated() {
        return seatsReactivated;
    }

    @Schema(description = "Surplus seats deleted because nothing had ever referenced them.")
    public int getSeatsRemoved() {
        return seatsRemoved;
    }

    @Schema(description = "Surplus seats kept as INACTIVE rows because allocation or attendance "
            + "history points at them. Never deleted, so no record is orphaned.")
    public int getSeatsRetired() {
        return seatsRetired;
    }

    @Schema(description = "Total seats added: created plus reactivated.")
    public int getSeatsAdded() {
        return seatsAdded;
    }

    @Schema(description = "Total seats withdrawn: removed plus retired.")
    public int getSeatsWithdrawn() {
        return seatsWithdrawn;
    }

    @Schema(description = "Inclusive range of seat numbers this change touched, e.g. \"101 - 120\". "
            + "Null when the seat count was already the requested value.")
    public String getSeatRange() {
        return seatRange;
    }
}
