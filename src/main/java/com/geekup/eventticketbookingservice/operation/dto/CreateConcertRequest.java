package com.geekup.eventticketbookingservice.operation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class CreateConcertRequest {
    private String name;
    private String description;
    private String venue;
    private ZonedDateTime startAt;
    private ZonedDateTime endAt;
    private ZonedDateTime saleStartAt;
    private ZonedDateTime saleEndAt;
}
