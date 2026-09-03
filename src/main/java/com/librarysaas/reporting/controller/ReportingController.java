package com.librarysaas.reporting.controller;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.reporting.dto.CollectionReportResponse;
import com.librarysaas.reporting.dto.DashboardSummaryResponse;
import com.librarysaas.reporting.dto.ExpiringMembershipResponse;
import com.librarysaas.reporting.dto.OutstandingSummaryResponse;
import com.librarysaas.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Reporting and dashboard metrics for one library.
 *
 * Everything here is read-only: there is no POST, PUT or DELETE, because
 * reporting observes data the other modules own and never changes it.
 *
 * Every endpoint is nested under the library it reports on, so the tenant is
 * fixed by the path and is passed into every aggregate query. No header is
 * consulted. Every endpoint requires REPORT_VIEW, which the schema already
 * grants to organization owner, library manager and accountant.
 *
 * Daily figures use the library's own calendar day, from library.timezone,
 * rather than the server clock.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Reporting", description = "Dashboard metrics and reports for a single library")
public class ReportingController {

    private final ReportingService reportingService;

    @Autowired
    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/libraries/{libraryId}/dashboard")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Dashboard summary for a library",
            description = "Student, seat, membership, attendance and collection figures in one "
                    + "call, all computed by the database. Today is the library's own calendar "
                    + "day, taken from its timezone. Requires REPORT_VIEW and membership of the "
                    + "library.")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboard(
            @PathVariable Long libraryId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard retrieved",
                reportingService.getDashboardSummary(libraryId)));
    }

    @GetMapping("/libraries/{libraryId}/reports/expiring-memberships")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Active memberships ending soon",
            description = "Soonest first. The window defaults to 15 days, the requirement carried "
                    + "by the dashboard since Phase 1, and may be overridden with days. Only "
                    + "active memberships are listed, and no membership status is ever changed: "
                    + "automatic expiry is not part of this API.")
    public ResponseEntity<ApiResponse<List<ExpiringMembershipResponse>>> getExpiringMemberships(
            @PathVariable Long libraryId,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Expiring memberships retrieved",
                reportingService.getExpiringMemberships(libraryId, days)));
    }

    @GetMapping("/libraries/{libraryId}/reports/collection")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Collection over a date range",
            description = "Successful payments only, broken down by the library's local day and "
                    + "by payment method, both grouped in the database. Defaults to the last 30 "
                    + "days. Amounts are exact decimals serialised as strings.")
    public ResponseEntity<ApiResponse<CollectionReportResponse>> getCollectionReport(
            @PathVariable Long libraryId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Collection report retrieved",
                reportingService.getCollectionReport(libraryId, from, to)));
    }

    @GetMapping("/libraries/{libraryId}/reports/outstanding")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Outstanding and overdue summary",
            description = "Invoiced less settled, using the same rule the finance module applies "
                    + "to a single invoice. An invoice counts as overdue when its due date has "
                    + "passed in the library's timezone and it still owes money.")
    public ResponseEntity<ApiResponse<OutstandingSummaryResponse>> getOutstandingSummary(
            @PathVariable Long libraryId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Outstanding summary retrieved",
                reportingService.getOutstandingSummary(libraryId)));
    }
}
