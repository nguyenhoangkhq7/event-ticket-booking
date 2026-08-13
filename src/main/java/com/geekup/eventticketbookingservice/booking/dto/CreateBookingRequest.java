package com.geekup.eventticketbookingservice.booking.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateBookingRequest {
    private List<BookingItemRequest> items;
    private String voucherCode;
}
