package com.geekup.eventticketbookingservice.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class CreateConcertRequest {
    @NotBlank(message = "Concert name is required")
    private String name;

    private String description;

    @NotBlank(message = "Venue is required")
    private String venue;

    @NotNull(message = "Start time is required")
    private ZonedDateTime startAt;

    private ZonedDateTime endAt;

    @NotNull(message = "Sale start time is required")
    private ZonedDateTime saleStartAt;

    @NotNull(message = "Sale end time is required")
    private ZonedDateTime saleEndAt;
}
