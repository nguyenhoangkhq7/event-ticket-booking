package com.geekup.eventticketbookingservice.operation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class CreateVoucherCampaignRequest {
    private String name;
    private String discountType;
    private BigDecimal discountValue;
    private Integer maxRedemptions;
    private Integer maxPerUser;
    private ZonedDateTime startsAt;
    private ZonedDateTime endsAt;
}
