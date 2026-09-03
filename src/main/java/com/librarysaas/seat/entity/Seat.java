package com.librarysaas.seat.entity;

import com.librarysaas.library.entity.Library;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single seat in a library's inventory.
 *
 * seat_number is unique per library (uk_seat_library_number), and zone and type
 * are optional so a library can run a flat seat list without modelling floors
 * or categories.
 */
@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private SeatZone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_type_id")
    private SeatType seatType;

    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    // Mapped as a plain column, matching Student and Library. The schema comment
    // on seat_assignment makes the service layer responsible for allocation
    // exclusivity, so no optimistic-locking semantics are introduced here.
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public Library getLibrary() {
        return library;
    }

    public void setLibrary(Library library) {
        this.library = library;
    }

    public SeatZone getZone() {
        return zone;
    }

    public void setZone(SeatZone zone) {
        this.zone = zone;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
