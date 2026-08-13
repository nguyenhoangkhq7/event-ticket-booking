package com.geekup.eventticketbookingservice.operation.dto;

import com.geekup.eventticketbookingservice.booking.BookingStatus;
import lombok.Data;

@Data
public class UpdateBookingStatusRequest {
    private BookingStatus status;
    private String reason;
}
