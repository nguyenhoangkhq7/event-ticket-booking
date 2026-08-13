package com.geekup.eventticketbookingservice.catalog.dto;

import com.geekup.eventticketbookingservice.catalog.ConcertStatus;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class ConcertResponse {
    private Long id;
    private String name;
    private String description;
    private String venue;
    private ZonedDateTime startAt;
    private ZonedDateTime endAt;
    private ZonedDateTime saleStartAt;
    private ZonedDateTime saleEndAt;
    private ConcertStatus status;
}
