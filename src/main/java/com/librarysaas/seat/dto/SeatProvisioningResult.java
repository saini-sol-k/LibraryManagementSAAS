package com.librarysaas.seat.dto;

/**
 * What changed when a library's seat count was applied.
 *
 * Carries counts rather than seat rows: a seat-count change can touch thousands
 * of seats, and every caller only needs to report what happened.
 *
 * The four counts are separate because they are four different events, and the
 * distinction is what the user is told:
 *
 * <ul>
 *   <li><b>created</b> - a seat that did not exist before.</li>
 *   <li><b>reactivated</b> - a seat an earlier reduction retired, brought back
 *       with its original id, number and history intact. Counting it as created
 *       would misreport it.</li>
 *   <li><b>removed</b> - a surplus seat that nothing had ever referenced, so the
 *       row could go without losing anything.</li>
 *   <li><b>retired</b> - a surplus seat that had assignment or attendance
 *       history, kept as an INACTIVE row so that history still resolves.</li>
 * </ul>
 */
public class SeatProvisioningResult {

    private final int seatCount;
    private final int seatsCreated;
    private final int seatsReactivated;
    private final int seatsRemoved;
    private final int seatsRetired;
    private final Integer firstSeatNumber;
    private final Integer lastSeatNumber;

    private SeatProvisioningResult(int seatCount, int seatsCreated, int seatsReactivated,
                                   int seatsRemoved, int seatsRetired,
                                   Integer firstSeatNumber, Integer lastSeatNumber) {
        this.seatCount = seatCount;
        this.seatsCreated = seatsCreated;
        this.seatsReactivated = seatsReactivated;
        this.seatsRemoved = seatsRemoved;
        this.seatsRetired = seatsRetired;
        this.firstSeatNumber = firstSeatNumber;
        this.lastSeatNumber = lastSeatNumber;
    }

    public static SeatProvisioningResult unchanged(int seatCount) {
        return new SeatProvisioningResult(seatCount, 0, 0, 0, 0, null, null);
    }

    public static SeatProvisioningResult increased(int seatCount, int created, int reactivated,
                                                   int firstSeatNumber, int lastSeatNumber) {
        return new SeatProvisioningResult(seatCount, created, reactivated, 0, 0,
                firstSeatNumber, lastSeatNumber);
    }

    public static SeatProvisioningResult decreased(int seatCount, int removed, int retired,
                                                   int firstSeatNumber, int lastSeatNumber) {
        return new SeatProvisioningResult(seatCount, 0, 0, removed, retired,
                firstSeatNumber, lastSeatNumber);
    }

    public int getSeatCount() {
        return seatCount;
    }

    public int getSeatsCreated() {
        return seatsCreated;
    }

    public int getSeatsReactivated() {
        return seatsReactivated;
    }

    /** Rows deleted outright, because nothing had ever referenced them. */
    public int getSeatsRemoved() {
        return seatsRemoved;
    }

    /** Rows kept as INACTIVE, because deleting them would have lost history. */
    public int getSeatsRetired() {
        return seatsRetired;
    }

    /** Seats added by this change, however they were added. */
    public int getSeatsAdded() {
        return seatsCreated + seatsReactivated;
    }

    /** Seats taken out of the library by this change, however they were taken out. */
    public int getSeatsWithdrawn() {
        return seatsRemoved + seatsRetired;
    }

    /** First seat number in the affected range, or null when nothing changed. */
    public Integer getFirstSeatNumber() {
        return firstSeatNumber;
    }

    /** Last seat number in the affected range, or null when nothing changed. */
    public Integer getLastSeatNumber() {
        return lastSeatNumber;
    }

    /** e.g. "1 - 100". Null when no seat was touched. */
    public String getSeatRange() {
        if (firstSeatNumber == null || lastSeatNumber == null) {
            return null;
        }
        return firstSeatNumber + " - " + lastSeatNumber;
    }
}
