package com.librarysaas.reporting.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Money banked by one library over a date range, broken down two ways.
 *
 * Both breakdowns are grouped in the database. Days are the library's own
 * calendar days, derived from library.timezone. Only SUCCESS payments are
 * counted, matching the Phase 2F balance rule, so this figure and an invoice
 * balance can never disagree about what has been settled.
 *
 * Every amount is BigDecimal and is serialised as a string.
 */
public class CollectionReportResponse {

    private Long libraryId;
    private String timezone;
    private LocalDate fromDate;
    private LocalDate toDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal totalCollected;

    private long paymentCount;
    private List<DailyCollection> byDay;
    private List<MethodCollection> byMethod;

    /** One of the library's local calendar days. */
    public static class DailyCollection {
        private LocalDate date;
        private long paymentCount;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "1500.00")
        private BigDecimal amount;

        public DailyCollection(LocalDate date, long paymentCount, BigDecimal amount) {
            this.date = date;
            this.paymentCount = paymentCount;
            this.amount = amount;
        }

        public LocalDate getDate() { return date; }
        public long getPaymentCount() { return paymentCount; }
        public BigDecimal getAmount() { return amount; }
    }

    /** One payment method, as recorded on the payment rows. */
    public static class MethodCollection {
        private String paymentMethod;
        private long paymentCount;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "1500.00")
        private BigDecimal amount;

        public MethodCollection(String paymentMethod, long paymentCount, BigDecimal amount) {
            this.paymentMethod = paymentMethod;
            this.paymentCount = paymentCount;
            this.amount = amount;
        }

        public String getPaymentMethod() { return paymentMethod; }
        public long getPaymentCount() { return paymentCount; }
        public BigDecimal getAmount() { return amount; }
    }

    public static CollectionReportResponse of(Long libraryId, String timezone,
                                              LocalDate fromDate, LocalDate toDate) {
        CollectionReportResponse r = new CollectionReportResponse();
        r.libraryId = libraryId;
        r.timezone = timezone;
        r.fromDate = fromDate;
        r.toDate = toDate;
        return r;
    }

    public Long getLibraryId() { return libraryId; }
    public String getTimezone() { return timezone; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }

    public BigDecimal getTotalCollected() { return totalCollected; }
    public void setTotalCollected(BigDecimal totalCollected) { this.totalCollected = totalCollected; }

    public long getPaymentCount() { return paymentCount; }
    public void setPaymentCount(long paymentCount) { this.paymentCount = paymentCount; }

    public List<DailyCollection> getByDay() { return byDay; }
    public void setByDay(List<DailyCollection> byDay) { this.byDay = byDay; }

    public List<MethodCollection> getByMethod() { return byMethod; }
    public void setByMethod(List<MethodCollection> byMethod) { this.byMethod = byMethod; }
}
