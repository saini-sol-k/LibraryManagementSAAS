package com.librarysaas.reporting.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What one library is still owed.
 *
 * The arithmetic is exactly the Phase 2F rule and nothing new: outstanding is
 * the invoiced total less everything settled by SUCCESS payments, and an invoice
 * is overdue when its due date has passed and it still owes money. No status is
 * invented, and nothing here writes.
 *
 * "Past" is judged against the library's own calendar day, taken from
 * library.timezone.
 */
public class OutstandingSummaryResponse {

    private Long libraryId;
    private String timezone;
    private LocalDate asOfDate;

    private long invoiceCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "4400.00",
            description = "Exact decimal amount, serialised as a string so no client parses it as a float")
    private BigDecimal totalInvoiced;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "2200.00")
    private BigDecimal totalSettled;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "2200.00")
    private BigDecimal totalOutstanding;

    private long overdueInvoiceCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "1500.00")
    private BigDecimal overdueAmount;

    public static OutstandingSummaryResponse of(Long libraryId, String timezone, LocalDate asOfDate) {
        OutstandingSummaryResponse r = new OutstandingSummaryResponse();
        r.libraryId = libraryId;
        r.timezone = timezone;
        r.asOfDate = asOfDate;
        return r;
    }

    public Long getLibraryId() { return libraryId; }
    public String getTimezone() { return timezone; }
    public LocalDate getAsOfDate() { return asOfDate; }

    public long getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(long invoiceCount) { this.invoiceCount = invoiceCount; }

    public BigDecimal getTotalInvoiced() { return totalInvoiced; }
    public void setTotalInvoiced(BigDecimal totalInvoiced) { this.totalInvoiced = totalInvoiced; }

    public BigDecimal getTotalSettled() { return totalSettled; }
    public void setTotalSettled(BigDecimal totalSettled) { this.totalSettled = totalSettled; }

    public BigDecimal getTotalOutstanding() { return totalOutstanding; }
    public void setTotalOutstanding(BigDecimal totalOutstanding) { this.totalOutstanding = totalOutstanding; }

    public long getOverdueInvoiceCount() { return overdueInvoiceCount; }
    public void setOverdueInvoiceCount(long overdueInvoiceCount) { this.overdueInvoiceCount = overdueInvoiceCount; }

    public BigDecimal getOverdueAmount() { return overdueAmount; }
    public void setOverdueAmount(BigDecimal overdueAmount) { this.overdueAmount = overdueAmount; }
}
