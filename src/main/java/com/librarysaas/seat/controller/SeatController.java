package com.librarysaas.seat.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.seat.dto.*;
import com.librarysaas.seat.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Seats, nested under the library that owns them.
 *
 * Seats, zones, types and assignments all carry library_id, so nesting keeps
 * every request tenant-checkable from the path alone and matches the nesting
 * the membership and address endpoints already use. Allocation is modelled as a
 * sub-resource of the seat: POST creates one, DELETE releases it.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Seats", description = "Seat inventory and allocation within a library")
public class SeatController {

    private final SeatService seatService;

    @Autowired
    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    /* ------------------------------------------------------------- inventory */

    @GetMapping("/libraries/{libraryId}/seats")
    @Operation(summary = "List a library's seats",
            description = "Ordered by seat number, each with its current allocation if any. "
                    + "Optional filters: status (AVAILABLE, OCCUPIED, MAINTENANCE, INACTIVE), "
                    + "zoneId, seatTypeId and a seat-number search. Requires SEAT_VIEW and "
                    + "membership of the library.")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> listSeats(
            @PathVariable Long libraryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long seatTypeId,
            @RequestParam(required = false) String search) {
        List<SeatResponse> seats = seatService.getSeats(libraryId, status, zoneId, seatTypeId, search);
        return ResponseEntity.ok(new ApiResponse<>(true, "Seats retrieved", seats));
    }

    @GetMapping("/libraries/{libraryId}/seats/{seatId}")
    @Operation(summary = "Get one seat", description = "Includes the current allocation if the seat is occupied.")
    public ResponseEntity<ApiResponse<SeatResponse>> getSeat(
            @PathVariable Long libraryId,
            @PathVariable Long seatId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Seat retrieved",
                seatService.getSeat(libraryId, seatId)));
    }

    @PostMapping("/libraries/{libraryId}/seats")
    @Operation(summary = "Create a seat",
            description = "Seat number must be unique within the library. Status defaults to "
                    + "AVAILABLE; OCCUPIED cannot be set directly. Requires SEAT_CREATE.")
    public ResponseEntity<ApiResponse<SeatResponse>> createSeat(
            @PathVariable Long libraryId,
            @Valid @RequestBody SeatRequest request) {
        SeatResponse seat = seatService.createSeat(libraryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Seat created", seat));
    }

    @PutMapping("/libraries/{libraryId}/seats/{seatId}")
    @Operation(summary = "Update a seat",
            description = "An allocated seat cannot have its status changed until it is released. "
                    + "Requires SEAT_UPDATE.")
    public ResponseEntity<ApiResponse<SeatResponse>> updateSeat(
            @PathVariable Long libraryId,
            @PathVariable Long seatId,
            @Valid @RequestBody SeatRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Seat updated",
                seatService.updateSeat(libraryId, seatId, request)));
    }

    @DeleteMapping("/libraries/{libraryId}/seats/{seatId}")
    @Operation(summary = "Take a seat out of service",
            description = "Sets the seat to INACTIVE. The row is kept because assignment history "
                    + "references it, and there is no SEAT_DELETE permission - this requires "
                    + "SEAT_UPDATE. An allocated seat must be released first.")
    public ResponseEntity<ApiResponse<SeatResponse>> deactivateSeat(
            @PathVariable Long libraryId,
            @PathVariable Long seatId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Seat taken out of service",
                seatService.deactivateSeat(libraryId, seatId)));
    }

    /* ------------------------------------------------------------ allocation */

    @GetMapping("/libraries/{libraryId}/seats/{seatId}/allocation")
    @Operation(summary = "Get a seat's current allocation",
            description = "Returns null data when the seat is not allocated.")
    public ResponseEntity<ApiResponse<SeatAllocationResponse>> getAllocation(
            @PathVariable Long libraryId,
            @PathVariable Long seatId) {
        SeatAllocationResponse allocation = seatService.getCurrentAllocation(libraryId, seatId);
        String message = allocation == null ? "Seat is not allocated" : "Allocation retrieved";
        return ResponseEntity.ok(new ApiResponse<>(true, message, allocation));
    }

    @PostMapping("/libraries/{libraryId}/seats/{seatId}/allocation")
    @Operation(summary = "Allocate a seat to a student",
            description = "The seat must be AVAILABLE and the student must belong to this library. "
                    + "A student may hold only one seat and a seat only one student. "
                    + "Requires SEAT_ASSIGN.")
    public ResponseEntity<ApiResponse<SeatAllocationResponse>> allocateSeat(
            @PathVariable Long libraryId,
            @PathVariable Long seatId,
            @Valid @RequestBody SeatAllocationRequest request) {
        SeatAllocationResponse allocation = seatService.allocateSeat(libraryId, seatId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Seat allocated", allocation));
    }

    @DeleteMapping("/libraries/{libraryId}/seats/{seatId}/allocation")
    @Operation(summary = "Release a seat",
            description = "Closes the active assignment and returns the seat to AVAILABLE. The "
                    + "assignment row is retained as history. Requires SEAT_ASSIGN.")
    public ResponseEntity<ApiResponse<SeatAllocationResponse>> releaseSeat(
            @PathVariable Long libraryId,
            @PathVariable Long seatId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Seat released",
                seatService.releaseSeat(libraryId, seatId)));
    }

    @GetMapping("/students/{studentId}/seat-allocation")
    @Operation(summary = "Get a student's current seat",
            description = "Returns null data when the student holds no seat. Scoped to the "
                    + "library that owns the student.")
    public ResponseEntity<ApiResponse<SeatAllocationResponse>> getStudentAllocation(
            @PathVariable Long studentId) {
        SeatAllocationResponse allocation = seatService.getStudentAllocation(studentId);
        String message = allocation == null ? "Student holds no seat" : "Allocation retrieved";
        return ResponseEntity.ok(new ApiResponse<>(true, message, allocation));
    }

    /* -------------------------------------------------------- reference data */

    @GetMapping("/libraries/{libraryId}/seat-types")
    @Operation(summary = "List a library's seat types",
            description = "Read-only reference data for building seat forms.")
    public ResponseEntity<ApiResponse<List<SeatTypeResponse>>> listSeatTypes(
            @PathVariable Long libraryId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Seat types retrieved",
                seatService.getSeatTypes(libraryId)));
    }

    @GetMapping("/libraries/{libraryId}/seat-zones")
    @Operation(summary = "List a library's seat zones",
            description = "Read-only reference data for building seat forms and grouping the grid.")
    public ResponseEntity<ApiResponse<List<SeatZoneResponse>>> listSeatZones(
            @PathVariable Long libraryId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Seat zones retrieved",
                seatService.getSeatZones(libraryId)));
    }
}
