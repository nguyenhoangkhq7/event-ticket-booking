package com.geekup.eventticketbookingservice.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TicketCategoryResponse {
    private Long id;
    private Long concertId;
    private String name;
    private BigDecimal price;
    private Integer maxPerBooking;
    private Integer availableQuantity;
}
