package com.librarysaas.seat.entity;

import com.librarysaas.library.entity.Library;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A floor or section within one library that seats are grouped into.
 */
@Entity
@Table(name = "seat_zone")
public class SeatZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "floor", length = 50)
    private String floor;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Library getLibrary() {
        return library;
    }

    public void setLibrary(Library library) {
        this.library = library;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}
