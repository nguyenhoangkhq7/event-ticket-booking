package com.geekup.eventticketbookingservice.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateBookingRequest {
    @NotEmpty(message = "Booking items must not be empty")
    @Valid
    private List<BookingItemRequest> items;

    private String voucherCode;
}
