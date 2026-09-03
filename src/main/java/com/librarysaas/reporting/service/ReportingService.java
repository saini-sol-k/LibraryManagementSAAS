package com.librarysaas.reporting.service;

import com.librarysaas.reporting.dto.CollectionReportResponse;
import com.librarysaas.reporting.dto.DashboardSummaryResponse;
import com.librarysaas.reporting.dto.ExpiringMembershipResponse;
import com.librarysaas.reporting.dto.OutstandingSummaryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only reporting over data the other modules own.
 *
 * Nothing here writes. Every operation resolves the library, authorises the
 * caller against it, and only then runs aggregate queries that carry the library
 * id inside the statement.
 *
 * "Today" always means the library's own calendar day, taken from
 * library.timezone rather than the server clock, so a library in one zone is not
 * reported against another zone's midnight.
 */
public interface ReportingService {

    /** The headline numbers for one library, for the dashboard. */
    DashboardSummaryResponse getDashboardSummary(Long libraryId);

    /**
     * Active memberships ending within the window. Never changes a status: this
     * is a view of rows, and automatic expiry is deliberately not implemented.
     */
    List<ExpiringMembershipResponse> getExpiringMemberships(Long libraryId, Integer days);

    /** Money banked over a date range, by day and by method. */
    CollectionReportResponse getCollectionReport(Long libraryId, LocalDate from, LocalDate to);

    /** What is still owed, and how much of it is overdue. */
    OutstandingSummaryResponse getOutstandingSummary(Long libraryId);
}
