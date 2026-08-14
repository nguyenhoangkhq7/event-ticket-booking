package com.geekup.eventticketbookingservice.operation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class CreateVoucherCampaignRequest {
    @NotBlank(message = "Voucher name is required")
    private String name;

    @NotBlank(message = "Discount type is required")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
    private BigDecimal discountValue;

    @NotNull(message = "Max redemptions is required")
    @Min(value = 1, message = "Max redemptions must be at least 1")
    private Integer maxRedemptions;

    @NotNull(message = "Max per user is required")
    @Min(value = 1, message = "Max per user must be at least 1")
    private Integer maxPerUser;

    @NotNull(message = "Start time is required")
    private ZonedDateTime startsAt;

    @NotNull(message = "End time is required")
    private ZonedDateTime endsAt;
}
