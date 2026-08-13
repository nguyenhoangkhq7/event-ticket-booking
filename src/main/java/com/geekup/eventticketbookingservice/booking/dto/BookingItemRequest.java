package com.geekup.eventticketbookingservice.booking.dto;

import lombok.Data;

@Data
public class BookingItemRequest {
    private Long ticketCategoryId;
    private Integer quantity;
}
