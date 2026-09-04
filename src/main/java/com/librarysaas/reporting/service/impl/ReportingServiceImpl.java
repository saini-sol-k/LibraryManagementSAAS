package com.librarysaas.reporting.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.reporting.dto.CollectionReportResponse;
import com.librarysaas.reporting.dto.DashboardSummaryResponse;
import com.librarysaas.reporting.dto.ExpiringMembershipResponse;
import com.librarysaas.reporting.dto.OutstandingSummaryResponse;
import com.librarysaas.reporting.repository.ReportingRepository;
import com.librarysaas.reporting.service.ReportingService;
import com.librarysaas.security.TenantAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting rules.
 *
 * Three properties are load bearing.
 *
 * <ul>
 *   <li><b>The tenant filter lives in the query, not in Java.</b> Every method
 *       passes the requested library id into an aggregate that carries a
 *       {@code library_id} predicate. Nothing reads several libraries and
 *       narrows afterwards. A cross-tenant aggregate leaks a number derived from
 *       every library at once, and a wrong total is far harder to spot than a
 *       wrong row.</li>
 *   <li><b>Caller authorisation is separate from that filter.</b> Access is
 *       decided by TenantAuthorizationService, which a super admin passes for
 *       every library. That is correct and changes nothing about which rows are
 *       aggregated: the query still receives the one library id from the path.
 *       There is no branch anywhere that widens or drops the filter for a
 *       privileged caller.</li>
 *   <li><b>"Today" is the library's day.</b> The reporting date comes from
 *       {@code library.timezone}, never from the server clock, so a library is
 *       reported against its own midnight wherever the process happens to run.
 *       This is contained here; no other module's date behaviour changes.</li>
 * </ul>
 */
@Service
public class ReportingServiceImpl implements ReportingService {

    /** Seat statuses, as the seat module defines them. Nothing new is invented. */
    private static final String SEAT_OCCUPIED = "OCCUPIED";
    private static final String SEAT_AVAILABLE = "AVAILABLE";

    private static final String MEMBERSHIP_ACTIVE = "ACTIVE";
    private static final String PAYMENT_SUCCESS = "SUCCESS";

    /**
     * The established product requirement, captured in the original dashboard
     * before any reporting existed: "show memberships expiring within 15 days".
     * Overridable per request, never silently changed.
     */
    static final int DEFAULT_EXPIRY_WINDOW_DAYS = 15;

    /** A window has to be a real future span, and long ranges are refused. */
    private static final int MAX_EXPIRY_WINDOW_DAYS = 365;

    /** Bounds the day breakdown so one request cannot ask for an unbounded report. */
    private static final long MAX_COLLECTION_RANGE_DAYS = 366;

    /**
     * Used when a library somehow carries a zone the JVM cannot resolve. The
     * column is NOT NULL DEFAULT 'Asia/Kolkata', so this is a guard rather than
     * an expected path, and it matches the schema default rather than inventing
     * a different one.
     */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Kolkata");

    private final ReportingRepository reportingRepository;
    private final LibraryRepository libraryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public ReportingServiceImpl(ReportingRepository reportingRepository,
                                LibraryRepository libraryRepository,
                                TenantAuthorizationService tenantAuthorizationService) {
        this.reportingRepository = reportingRepository;
        this.libraryRepository = libraryRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /* ------------------------------------------------------------- dashboard */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public DashboardSummaryResponse getDashboardSummary(Long libraryId) {
        Library library = requireLibrary(libraryId);
        ZoneId zone = zoneOf(library);
        LocalDate today = LocalDate.now(zone);

        DashboardSummaryResponse summary = DashboardSummaryResponse.of(
                library.getLibraryId(), library.getName(), zone.getId(), today);

        summary.setTotalStudents(reportingRepository.countStudents(libraryId));
        summary.setStudentsByStatus(toCountMap(reportingRepository.countStudentsByStatus(libraryId)));

        Map<String, Long> seats = toCountMap(reportingRepository.countSeatsByStatus(libraryId));
        summary.setSeatsByStatus(seats);
        summary.setTotalSeats(seats.values().stream().mapToLong(Long::longValue).sum());
        summary.setOccupiedSeats(seats.getOrDefault(SEAT_OCCUPIED, 0L));
        summary.setAvailableSeats(seats.getOrDefault(SEAT_AVAILABLE, 0L));

        summary.setActiveMemberships(
                reportingRepository.countMembershipsByStatus(libraryId, MEMBERSHIP_ACTIVE));

        summary.setAttendanceToday(reportingRepository.countAttendanceOnDay(libraryId, today));
        summary.setStudentsCurrentlyInside(
                reportingRepository.countCurrentlyInside(libraryId, today));

        // The payment timestamp is compared against the instants that bound the
        // library's local day, so a payment taken at 23:59 local counts today
        // and one at 00:01 local counts tomorrow.
        LocalDateTime dayStart = startOfLocalDay(today, zone);
        LocalDateTime dayEnd = startOfLocalDay(today.plusDays(1), zone);

        summary.setCollectionToday(orZero(reportingRepository.sumPaymentsBetween(
                libraryId, PAYMENT_SUCCESS, dayStart, dayEnd)));
        summary.setPaymentsToday(reportingRepository.countPaymentsBetween(
                libraryId, PAYMENT_SUCCESS, dayStart, dayEnd));

        return summary;
    }

    /* --------------------------------------------------- expiring memberships */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<ExpiringMembershipResponse> getExpiringMemberships(Long libraryId, Integer days) {
        Library library = requireLibrary(libraryId);
        ZoneId zone = zoneOf(library);
        LocalDate today = LocalDate.now(zone);

        int window = days == null ? DEFAULT_EXPIRY_WINDOW_DAYS : days;
        if (window < 1 || window > MAX_EXPIRY_WINDOW_DAYS) {
            throw new BusinessException(
                    "The window must be between 1 and " + MAX_EXPIRY_WINDOW_DAYS + " days",
                    "INVALID_REPORT_WINDOW");
        }

        // Closed range from today to the far edge of the window. Memberships that
        // already ended are not "expiring", so the range starts at today.
        return reportingRepository
                .findExpiringMemberships(libraryId, MEMBERSHIP_ACTIVE, today, today.plusDays(window))
                .stream()
                .map(membership -> ExpiringMembershipResponse.from(membership, today))
                .toList();
    }

    /* ------------------------------------------------------ collection report */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public CollectionReportResponse getCollectionReport(Long libraryId, LocalDate from, LocalDate to) {
        Library library = requireLibrary(libraryId);
        ZoneId zone = zoneOf(library);
        LocalDate today = LocalDate.now(zone);

        LocalDate fromDate = from == null ? today.minusDays(29) : from;
        LocalDate toDate = to == null ? today : to;

        if (toDate.isBefore(fromDate)) {
            throw new BusinessException("The end date must not be before the start date",
                    "INVALID_REPORT_RANGE");
        }
        if (fromDate.plusDays(MAX_COLLECTION_RANGE_DAYS).isBefore(toDate)) {
            throw new BusinessException(
                    "The range must not exceed " + MAX_COLLECTION_RANGE_DAYS + " days",
                    "INVALID_REPORT_RANGE");
        }

        // The range covers whole local days: from the start of `fromDate` up to,
        // but not including, the start of the day after `toDate`.
        LocalDateTime rangeStart = startOfLocalDay(fromDate, zone);
        LocalDateTime rangeEnd = startOfLocalDay(toDate.plusDays(1), zone);

        CollectionReportResponse report =
                CollectionReportResponse.of(library.getLibraryId(), zone.getId(), fromDate, toDate);

        report.setTotalCollected(orZero(reportingRepository.sumPaymentsBetween(
                libraryId, PAYMENT_SUCCESS, rangeStart, rangeEnd)));
        report.setPaymentCount(reportingRepository.countPaymentsBetween(
                libraryId, PAYMENT_SUCCESS, rangeStart, rangeEnd));

        List<CollectionReportResponse.MethodCollection> byMethod = new ArrayList<>();
        for (Object[] row : reportingRepository.sumPaymentsByMethod(
                libraryId, PAYMENT_SUCCESS, rangeStart, rangeEnd)) {
            byMethod.add(new CollectionReportResponse.MethodCollection(
                    (String) row[0], toLong(row[1]), orZero(toBigDecimal(row[2]))));
        }
        report.setByMethod(byMethod);

        // Grouping by the library's local day happens in the database, using the
        // zone's offset at the start of the range. See the query's own note on
        // daylight-saving transitions.
        int offsetMinutes = bucketShiftMinutes(fromDate, zone);
        List<CollectionReportResponse.DailyCollection> byDay = new ArrayList<>();
        for (Object[] row : reportingRepository.sumPaymentsByLocalDay(
                libraryId, PAYMENT_SUCCESS, rangeStart, rangeEnd, offsetMinutes)) {
            byDay.add(new CollectionReportResponse.DailyCollection(
                    toLocalDate(row[0]), toLong(row[1]), orZero(toBigDecimal(row[2]))));
        }
        report.setByDay(byDay);

        return report;
    }

    /* ----------------------------------------------------- outstanding summary */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public OutstandingSummaryResponse getOutstandingSummary(Long libraryId) {
        Library library = requireLibrary(libraryId);
        ZoneId zone = zoneOf(library);
        LocalDate today = LocalDate.now(zone);

        OutstandingSummaryResponse summary =
                OutstandingSummaryResponse.of(library.getLibraryId(), zone.getId(), today);

        Object[] invoiced = firstRow(reportingRepository.summariseInvoiced(libraryId));
        long invoiceCount = invoiced == null ? 0L : toLong(invoiced[0]);
        BigDecimal totalInvoiced = invoiced == null ? BigDecimal.ZERO : orZero(toBigDecimal(invoiced[1]));

        BigDecimal totalSettled =
                orZero(reportingRepository.sumSettledForLibrary(libraryId, PAYMENT_SUCCESS));

        summary.setInvoiceCount(invoiceCount);
        summary.setTotalInvoiced(totalInvoiced);
        summary.setTotalSettled(totalSettled);
        // Exactly the Phase 2F rule, in exact decimal: invoiced less settled.
        summary.setTotalOutstanding(totalInvoiced.subtract(totalSettled));

        Object[] overdue = firstRow(reportingRepository.summariseOverdue(
                libraryId, PAYMENT_SUCCESS, today));
        summary.setOverdueInvoiceCount(overdue == null ? 0L : toLong(overdue[0]));
        summary.setOverdueAmount(overdue == null ? BigDecimal.ZERO : orZero(toBigDecimal(overdue[1])));

        return summary;
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves the library, then authorises the caller against it.
     *
     * The library is looked up first so an unknown id is 404 rather than 403,
     * matching every other module. No aggregate runs until both have passed.
     */
    private Library requireLibrary(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        try {
            tenantAuthorizationService.requireLibraryAccess(
                    tenantAuthorizationService.getCurrentUserId().orElse(null), libraryId);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
        return library;
    }

    /**
     * The library's own zone. Falls back to the schema's default only if the
     * stored value cannot be resolved, which the NOT NULL DEFAULT makes unlikely.
     */
    private ZoneId zoneOf(Library library) {
        String timezone = library.getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (java.time.DateTimeException e) {
            return FALLBACK_ZONE;
        }
    }

    /**
     * The instant a local day begins, expressed the way the database stores it.
     *
     * The timestamp columns hold local date-times without a zone, written by a
     * JVM using its own default zone. Converting the library's local midnight
     * into that same frame is what lets a range predicate on the raw column stay
     * index-friendly while still meaning "this library's day".
     */
    private LocalDateTime startOfLocalDay(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Minutes to add to a stored timestamp to reach the library's local clock.
     *
     * The timestamp columns hold local date-times written by the JVM in its own
     * default zone, the same assumption {@link #startOfLocalDay} relies on. A
     * stored value therefore already carries that zone's offset, so shifting it
     * by the library's full offset from UTC would apply an offset twice and push
     * late-evening payments into the following day. The shift is the difference
     * between the two zones, which is zero whenever the JVM already runs in the
     * library's own zone.
     *
     * Both offsets are read at the same instant, so a zone with a daylight-saving
     * transition is measured consistently on either side of it.
     */
    private int bucketShiftMinutes(LocalDate day, ZoneId zone) {
        ZonedDateTime libraryMidnight = day.atStartOfDay(zone);
        int libraryOffset = libraryMidnight.getOffset().getTotalSeconds() / 60;
        int storageOffset = libraryMidnight.withZoneSameInstant(ZoneId.systemDefault())
                .getOffset().getTotalSeconds() / 60;
        return libraryOffset - storageOffset;
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put((String) row[0], toLong(row[1]));
        }
        return counts;
    }

    private Object[] firstRow(List<Object[]> rows) {
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** Never converts through a floating-point type. */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof LocalDate localDate) return localDate;
        return LocalDate.parse(value.toString());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
