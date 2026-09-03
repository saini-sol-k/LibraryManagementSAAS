package com.librarysaas.seat.dto;

import com.librarysaas.seat.entity.SeatZone;

/** Read-only view of a zone, used to populate seat forms and group the grid. */
public class SeatZoneResponse {

    private Long zoneId;
    private String name;
    private String floor;
    private String description;
    private String status;

    public static SeatZoneResponse from(SeatZone zone) {
        SeatZoneResponse response = new SeatZoneResponse();
        response.zoneId = zone.getZoneId();
        response.name = zone.getName();
        response.floor = zone.getFloor();
        response.description = zone.getDescription();
        response.status = zone.getStatus();
        return response;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public String getName() {
        return name;
    }

    public String getFloor() {
        return floor;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }
}
