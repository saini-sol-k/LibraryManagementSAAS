package com.librarysaas.seat.dto;

import com.librarysaas.seat.entity.SeatType;

import java.math.BigDecimal;

/** Read-only view of a seat type, used to populate seat forms. */
public class SeatTypeResponse {

    private Long seatTypeId;
    private String name;
    private String description;
    private BigDecimal price;
    private String status;

    public static SeatTypeResponse from(SeatType seatType) {
        SeatTypeResponse response = new SeatTypeResponse();
        response.seatTypeId = seatType.getSeatTypeId();
        response.name = seatType.getName();
        response.description = seatType.getDescription();
        response.price = seatType.getPrice();
        response.status = seatType.getStatus();
        return response;
    }

    public Long getSeatTypeId() {
        return seatTypeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getStatus() {
        return status;
    }
}
