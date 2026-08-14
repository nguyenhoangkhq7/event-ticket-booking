package com.geekup.eventticketbookingservice.operation.dto;

import com.geekup.eventticketbookingservice.booking.RiskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBookingRiskStatusRequest {
    @NotNull(message = "Risk status is required")
    private RiskStatus riskStatus;
    private String reason;
}
