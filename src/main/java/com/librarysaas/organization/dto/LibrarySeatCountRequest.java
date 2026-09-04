package com.librarysaas.organization.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.librarysaas.seat.json.SeatCountDeserializer;
import com.librarysaas.seat.service.SeatProvisioningService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * New total seat count for a library.
 *
 * The library comes from the path, not the body, so a caller cannot retarget the
 * change at another tenant by editing what it sends. Individual seat numbers are
 * absent by design: they are generated from this figure and are not something a
 * client supplies or alters.
 *
 * The bounds mirror SeatProvisioningService, which re-checks them, so a value
 * accepted here is accepted there.
 */
public class LibrarySeatCountRequest {

    @JsonDeserialize(using = SeatCountDeserializer.class)
    @NotNull(message = "Number of seats is required.")
    @Min(value = SeatProvisioningService.MIN_SEAT_COUNT,
            message = "Number of seats must be greater than 0.")
    @Max(value = SeatProvisioningService.MAX_SEAT_COUNT,
            message = "Number of seats cannot exceed 10000.")
    @Schema(description = "How many seats the library should have. Raising it creates only the "
            + "missing seats; existing seats keep their number and status. Lowering it "
            + "removes or retires the surplus, and is refused when one is in use.",
            example = "120")
    private Integer seatCount;

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }
}
