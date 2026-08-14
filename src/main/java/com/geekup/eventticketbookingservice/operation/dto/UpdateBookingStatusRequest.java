package com.geekup.eventticketbookingservice.operation.dto;

import com.geekup.eventticketbookingservice.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBookingStatusRequest {
    @NotNull(message = "Status is required")
    private BookingStatus status;
    private String reason;
}
