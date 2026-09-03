package com.librarysaas.reporting.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One library's headline numbers, every one computed by the database.
 *
 * {@code reportingDate} is the library's own calendar day, derived from
 * library.timezone rather than the server clock, and is returned so a reader can
 * see which day the daily figures refer to.
 *
 * Money follows the Phase 2F rule: BigDecimal end to end, serialised as a string
 * so no client parses an amount into a floating-point number.
 */
public class DashboardSummaryResponse {

    private Long libraryId;
    private String libraryName;
    private String timezone;
    private LocalDate reportingDate;

    private long totalStudents;
    private Map<String, Long> studentsByStatus;

    private long totalSeats;
    private long occupiedSeats;
    private long availableSeats;
    private Map<String, Long> seatsByStatus;

    private long activeMemberships;

    private long attendanceToday;
    private long studentsCurrentlyInside;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal collectionToday;

    private long paymentsToday;

    public static DashboardSummaryResponse of(Long libraryId, String libraryName, String timezone,
                                              LocalDate reportingDate) {
        DashboardSummaryResponse r = new DashboardSummaryResponse();
        r.libraryId = libraryId;
        r.libraryName = libraryName;
        r.timezone = timezone;
        r.reportingDate = reportingDate;
        return r;
    }

    public Long getLibraryId() { return libraryId; }
    public String getLibraryName() { return libraryName; }
    public String getTimezone() { return timezone; }
    public LocalDate getReportingDate() { return reportingDate; }

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public Map<String, Long> getStudentsByStatus() { return studentsByStatus; }
    public void setStudentsByStatus(Map<String, Long> studentsByStatus) { this.studentsByStatus = studentsByStatus; }

    public long getTotalSeats() { return totalSeats; }
    public void setTotalSeats(long totalSeats) { this.totalSeats = totalSeats; }

    public long getOccupiedSeats() { return occupiedSeats; }
    public void setOccupiedSeats(long occupiedSeats) { this.occupiedSeats = occupiedSeats; }

    public long getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(long availableSeats) { this.availableSeats = availableSeats; }

    public Map<String, Long> getSeatsByStatus() { return seatsByStatus; }
    public void setSeatsByStatus(Map<String, Long> seatsByStatus) { this.seatsByStatus = seatsByStatus; }

    public long getActiveMemberships() { return activeMemberships; }
    public void setActiveMemberships(long activeMemberships) { this.activeMemberships = activeMemberships; }

    public long getAttendanceToday() { return attendanceToday; }
    public void setAttendanceToday(long attendanceToday) { this.attendanceToday = attendanceToday; }

    public long getStudentsCurrentlyInside() { return studentsCurrentlyInside; }
    public void setStudentsCurrentlyInside(long studentsCurrentlyInside) { this.studentsCurrentlyInside = studentsCurrentlyInside; }

    public BigDecimal getCollectionToday() { return collectionToday; }
    public void setCollectionToday(BigDecimal collectionToday) { this.collectionToday = collectionToday; }

    public long getPaymentsToday() { return paymentsToday; }
    public void setPaymentsToday(long paymentsToday) { this.paymentsToday = paymentsToday; }
}
