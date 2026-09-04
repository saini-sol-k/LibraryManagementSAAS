package com.librarysaas.seat.service;

import com.librarysaas.library.entity.Library;
import com.librarysaas.seat.dto.SeatProvisioningResult;

/**
 * Keeps a library's seat rows in step with its configured seat count.
 *
 * This is internal plumbing, not an API surface: it performs no authorisation
 * of its own and is never reached from a controller. Callers - customer
 * onboarding and the library seat-count endpoint - decide who is allowed to ask,
 * then hand over an already-authorised library. Every method here is deliberately
 * bound to a Library instance rather than an id, so there is no code path that
 * provisions seats without a caller having resolved and checked the tenant
 * first.
 *
 * It also runs inside the caller's transaction rather than opening its own, so
 * a failure anywhere in onboarding rolls the seats back with everything else.
 */
public interface SeatProvisioningService {

    /** Smallest seat count that can be configured. Zero seats is not a library. */
    int MIN_SEAT_COUNT = 1;

    /**
     * Largest seat count that can be configured.
     *
     * <b>Documented business maximum.</b> The schema imposes no limit, so this is
     * an application decision taken to protect the system from an accidental
     * huge request - a typo of 1000000 would otherwise try to write a million
     * rows in one transaction.
     *
     * 10,000 is roughly two orders of magnitude above the largest real study
     * centre, so it constrains nobody in practice, while a single change still
     * writes at most 10,000 rows - well inside normal statement and lock
     * timeouts with JDBC batching.
     */
    int MAX_SEAT_COUNT = 10000;

    /**
     * Brings the library's seats in line with {@code newSeatCount} and updates the
     * library's stored count to match.
     *
     * <p>Increasing creates only the missing numbers above the current seat count;
     * seats that already exist are left exactly as they are, including their
     * status. A seat that a previous reduction deactivated is reactivated rather
     * than duplicated.
     *
     * <p>Decreasing never deletes. Seats above the new seat count are taken out of
     * service, and only when that is safe: if any of them is currently allocated
     * or carries attendance history, the whole operation is refused so no record
     * is stranded behind an inactive seat.
     *
     * @param library      an already-authorised library
     * @param newSeatCount  target seat count, validated against the bounds above
     * @param actorUserId  user credited with the change, may be null
     * @return what was created, reactivated or deactivated
     */
    SeatProvisioningResult applySeatCount(Library library, int newSeatCount, Long actorUserId);
}
